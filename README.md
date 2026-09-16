# Knotic - Android, iOS and desktop

The native version of the [Knotic web app](../AirDeskWeb): pair two devices with a
6-digit code or a QR, then move clipboard text and files between them. It speaks
the same protocol as the website, so a phone running this app can pair with a
laptop running the browser version, and the other way round.

One Kotlin Multiplatform codebase, one Compose Multiplatform UI, three apps.

- **Text** is end to end encrypted (ECDH P-256 + AES-GCM, byte compatible with
  the web client's WebCrypto code) and relayed through Firebase Realtime Database.
- **Files** go peer to peer over a WebRTC data channel in 16 KB chunks, up to 20 MB.
- **Cloud relay** for files up to 5 MB when no direct route exists - off by
  default, exactly like `VITE_ENABLE_RELAY` on the web.
- Sessions close after 15 minutes of inactivity; presence is cleared server side
  when a device drops off.
- **Previews and conversion**: received photos and videos show a preview, the
  activity list shows thumbnails, and any received image - or one picked in
  *Convert only* - can be saved as JPG, PNG or WEBP, all on the device.

Version **1.2.0**, in step with the website's 1.2.0.

## Firebase config

All three platforms talk to the same `getknotic` project the website uses, so
any pair of them can link up.

| Platform | File | Registered as |
| --- | --- | --- |
| Android | `androidApp/google-services.json` | `applicationId` **`com.sharing.app`** |
| iOS | `iosApp/iosApp/GoogleService-Info.plist` | bundle id **`com.share.app`** |
| Desktop | `desktopApp/src/main/resources/firebase.properties` | the web app config (`VITE_FIREBASE_*`) |

The Android `applicationId` deliberately differs from the module's `namespace`
(`com.share.app`, which follows the Kotlin package): the console registration
is `com.sharing.app`, and that is what the google-services plugin matches. App
ids play no part in pairing, so every platform still reaches the same sessions.

The Realtime Database rules in the web repo (`database.rules.json`) apply
unchanged.

## Setting up on another machine

Everything needed to build is committed, including the Gradle wrapper and all
three Firebase configs. A clone needs only:

1. **JDK 17 or newer.** Android Studio's bundled JBR works for building. It has
   no `jpackage`, so desktop *packaging* needs a full JDK - see below.
2. **Android SDK 37**, and one of:
   - open the project in Android Studio once (it writes `local.properties`), or
   - set `ANDROID_HOME` to the SDK path.

   `local.properties` is deliberately not committed, because it holds a path
   specific to one machine. Without it and without `ANDROID_HOME`, only the
   Android module fails, with `SDK location not found`; the shared module and
   the desktop app still build.
3. **On macOS and Linux**, make the wrapper executable once - git on Windows
   stores it without the executable bit:

   ```bash
   chmod +x gradlew
   ```

The first build downloads Gradle 9.5 and the dependencies, so allow a few
minutes; after that it is incremental.

## Build and run

Requirements: JDK 17+ (Android Studio's bundled JBR works), Android SDK 37.
iOS needs a Mac with Xcode 16+ and CocoaPods.

```bash
# Android
./gradlew :androidApp:installDebug

# Desktop (Windows, macOS, Linux)
./gradlew :desktopApp:run                          # run from Gradle
./gradlew :desktopApp:createDistributable          # app folder with a bundled runtime
./gradlew :desktopApp:packageUberJarForCurrentOS   # one portable jar, no jpackage needed
./gradlew :desktopApp:packageDistributionForCurrentOS   # .msi / .dmg / .deb

# Shared unit tests (protocol, crypto, file types, transfers, desktop image codec)
./gradlew :shared:jvmTest

# Version check alone (the tests run it first anyway)
./gradlew checkVersion

# iOS (on a Mac)
cd iosApp && pod install && open iosApp.xcworkspace
```

The Xcode build runs the Kotlin framework build itself through the CocoaPods
integration. Set your signing team in Xcode before running on a device.

### Icons

All platforms use the official mark (`#4F7CFF` / `#22D3A6` arcs around a white
dot on `#08090C`), the same artwork as the website's logo pack:

| Where | File |
| --- | --- |
| In-app logo and hero | drawn in `ui/components/Graphics.kt`, same path data as `app-icon.svg` |
| Android launcher | `androidApp/src/main/res/drawable/ic_launcher_foreground.xml` (adaptive) |
| Desktop window and taskbar | `desktopApp/src/main/resources/knotic-icon.png` |
| Desktop installer and shortcut | `desktopApp/icons/knotic.ico` (16-256 px), `knotic.png` for Linux |
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` |

### Packaging the desktop app

`createDistributable` and `packageDistributionForCurrentOS` need a JDK that
ships `jpackage` - Android Studio's bundled JBR does **not**, so point
`JAVA_HOME` at a full JDK 17+ (a Gradle-provisioned Temurin under
`~/.gradle/jdks` works). A Windows `.msi` additionally needs the WiX Toolset.

**Code signing on locked-down Windows:** the launcher jpackage generates is
unsigned, so machines running an enterprise WDAC policy or Smart App Control
refuse it ("did not meet the Enterprise signing level requirements"). Either
sign the produced `.exe`, or ship `packageUberJarForCurrentOS` and launch it
with a signed runtime:

```
"<jdk>\bin\javaw.exe" -jar Knotic-windows-x64-1.2.0.jar
```

## Releasing a new version

The version is set once, in `gradle/libs.versions.toml`:

```toml
app-version = "1.2.0"   # what people see
app-build = "2"         # must go up on every release
```

Android's `versionName`/`versionCode`, the desktop installer and the CocoaPods
spec read it from there. Three places cannot, and `./gradlew checkVersion`
fails until they match - the shared tests run it first:

- `Brand.VERSION` in `shared/.../config/Brand.kt` (the footer and About screen)
- `CFBundleShortVersionString` / `CFBundleVersion` in `iosApp/iosApp/Info.plist`
- `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `iosApp/iosApp.xcodeproj`

Keep the app's version equal to the website's `package.json` when the two ship
the same features.

## Architecture

MVVM with a unidirectional MVI loop, on clean architecture layers. Everything
lives in `shared/`; the three app modules are thin launchers.

```
shared/src/commonMain/kotlin/com/share/app/
├── base/            BaseViewModel<State, Intent, Effect>
├── config/          AppConfig (site URL, relay switch, STUN/TURN), brand, copy
├── domain/
│   ├── model/       AppSessionState - the one contract screens read
│   ├── policy/      Pure rules: pairing codes, retry policy, wire formats
│   ├── media/       FileTypes (what a file is), ImageFormats, the ImageProcessor port
│   ├── analytics/   The closed list of analytics events; logger and crash reporter ports
│   ├── repository/  Interfaces for auth, session node, signalling, relay, storage
│   ├── crypto/      SessionCipher interface
│   ├── webrtc/      PeerConnectionPort - the transport, narrowed
│   ├── session/     SessionEngine, WebRtcTransport, WebRtcLink, FileTransferEngine, FilePreviews
│   └── usecase/     Pairing, Transfer, ConvertImage and Preferences use cases
├── data/            Firebase (GitLive), cryptography-kotlin, DataStore, FileKit, log-to-crash bridge
├── di/              Koin modules; platformModule is expect/actual
└── ui/              Compose screens - read state, send intents, nothing deeper
    ├── home/        HomeContract (UiState, Intent, Effect), HomeViewModel, HomeScreen
    ├── convert/     Convert only: pick an image, save it in another format; the shared conversion state
    ├── about/       How it works, privacy, FAQ
    ├── components/  Cards, buttons, status chip, file thumbnails, previews, the logo
    ├── navigation/  Type-safe routes, and one screen view per destination
    └── theme/       The web client's design tokens, light and dark
```

Platform source sets:

| Source set | Holds |
| --- | --- |
| `mobileMain` (Android + iOS) | webrtc-kmp peer connection, camera QR scanner, Firebase Analytics and Crashlytics |
| `androidMain` | Native data channels, clipboard, DataStore path, entry points, `AndroidImageProcessor` |
| `iosMain` | Native data channels, clipboard, DataStore path, entry points, `UIKitImageProcessor` |
| `jvmMain` (desktop) | webrtc-java, AWT clipboard, window drag and drop, Firebase desktop init, `SkiaImageProcessor` |

**Rule of thumb:** nothing in `ui/` imports from `data/`. A screen observes its
ViewModel's `uiState`, calls `onIntent(...)`, and collects `effects` once.

### The session engine

`SessionEngine` is a direct port of the web client's `useFirebaseSync`,
`useWebRtcTransport`, `createWebRtcSession` and `useFileTransfer`: the same
handshake, the same 12 s connect timeout and 6 s recovery grace, three attempts
with backoff, the same RTDB fields and signalling envelopes. It is an app-wide
singleton above navigation, so leaving a screen never drops the session.

All of it runs on one confined dispatcher, which gives the run-to-completion
behaviour the browser's event loop gives the web version. Pairing actions are
additionally serialised with a mutex.

### The life of a session

The same rules as the website (its README has the full table):

- **Whoever is last online owns the cleanup.** Alone in a session, a device
  registers removal of the whole node on disconnect; with the other device
  present, it only registers removal of its own presence fields, and the other
  device - now alone - takes over. That is what lets a browser peer move to
  another page of the site without the session dying.
- **Cleanup removes fields, it never blanks them.** Writing `hostOnline: false`
  into a node the other device already deleted recreates it as an empty shell.
- **A guest that leaves gives its slot back**, and a host hands back the slot of
  a guest that has been gone for 90 seconds, so the code can be used again. If
  the host is already gone, the leaving guest deletes the node instead - while
  it still holds the slot, because the rules stop it the moment it lets go.
- **Ciphertext that cannot be opened yet is held**, and retried when a key is
  next agreed. When the peer rebuilds its half of the exchange, the last message
  this device sent is published again under the new key.

The website also carries a paired session across its own page navigations
(`sessionHandoff.ts`). The app has no such navigation - leaving a screen never
touches the session - so there is nothing to port there.

### Files: the size check and the stall timeout

The sender announces `SIZE:<bytes>` just before `NAME:`, so a stream that stops
short is rejected instead of being saved as a complete file. Clients that do not
send it - including 1.0 apps - still work; the check is simply skipped. A receive
with no chunk for 30 seconds is abandoned, so an interrupted send cannot wedge
the receiving side. A relayed file is stored under a random name, never its own.

### Previews and converting images

What a file is comes from its first 32 bytes (`domain/media/FileTypes.kt`, a
port of the web's `fileType.ts`), not its name - an iPhone photo called `.jpg`
is often HEIC underneath. The pixel work is each platform's own:

| Platform | Decoder | Reads | Video frames |
| --- | --- | --- | --- |
| Android 9+ | `ImageDecoder` | JPEG, PNG, WEBP, GIF, BMP, HEIC; AVIF from 12 | `MediaMetadataRetriever` |
| Android 8 | `BitmapFactory` | the same minus HEIC and AVIF, without EXIF rotation | `MediaMetadataRetriever` |
| iOS | UIKit | everything Photos produces, HEIC included | `AVAssetImageGenerator` |
| Desktop | Skia | JPEG, PNG, WEBP, GIF, BMP, ICO | none - a type icon instead |

All three write JPEG, PNG and WEBP (iOS has no WebP encoder, so it hands the
pixels to Skia). Jobs run one at a time off the main thread, and anything over
16.7 megapixels is scaled down first, as on the web. Known limits: an animated
GIF keeps its first frame, metadata such as location is not carried over, SVG
is not converted, and the desktop app cannot open HEIC. Unlike the website,
the inbox shows a still frame for a video rather than a player.

## Analytics and crash reports

Android and iOS use Firebase Analytics and Crashlytics through GitLive; the
desktop app reports nothing, because firebase-java-sdk has neither.

- Events are a closed list in `domain/analytics/Analytics.kt` - screens, joins,
  links, texts and files sent or received (with route and kind), conversions,
  timeouts. None of them carries message text, a file name or a pairing code.
- Warnings and errors from the app log become Crashlytics breadcrumbs; only an
  error with a throwable becomes a non-fatal (`CrashReportingLogWriter`).
- **Debug Android builds collect nothing** (`androidApp/src/debug/AndroidManifest.xml`).
  To watch events, flip the analytics flag there and use DebugView.
- The advertising ID and ad-personalisation signals are off on both platforms.
- **iOS crash reports need dSYMs**, and that step lives in Xcode, not here.
  After `pod install`, add a *Run Script* build phase to the `iosApp` target
  running `"${PODS_ROOT}/FirebaseCrashlytics/run"`, with the input files listed
  in Firebase's "Get readable crash reports" guide, and set *Debug Information
  Format* to *DWARF with dSYM File* for Release. Without it crashes still
  arrive, just unsymbolicated.
- Android release builds upload the R8 mapping file to Crashlytics as part of
  `assembleRelease`, so they need to be signed in to the Firebase project
  (`-x uploadCrashlyticsMappingFileRelease` skips it).

### Why the data channels are native

webrtc-kmp sends every data channel frame as binary and hides the frame type on
receive. The web client tells control messages (`SIZE:`, `NAME:`, `END`, `DISCONNECT`)
from file bytes by frame type, so on Android and iOS the app drives
`org.webrtc.DataChannel` / `RTCDataChannel` directly. webrtc-java on desktop
keeps the distinction already.

## Deep links

A QR from any Knotic device encodes `https://getknotic.web.app/?code=NNNNNN`, so
any camera app opens it. The apps also accept `knotic://join?code=NNNNNN`.

- **Android** opens `getknotic.web.app` links directly once the site serves
  `/.well-known/assetlinks.json` for `com.share.app`; until then they open the site.
- **iOS** handles `knotic://`. For universal links, add the Associated Domains
  capability and an `apple-app-site-association` file on the site.
- **Desktop** accepts a code or join URL as a launch argument.

## Configuration

`config/AppConfig.kt` mirrors the web `.env`:

| Web | App |
| --- | --- |
| `VITE_SITE_URL` | `siteUrl` |
| `VITE_ENABLE_RELAY` | `relayEnabled` |
| `VITE_WEBRTC_TURN_*` | `TURN_SERVERS` |
| `VITE_WEBRTC_ICE_TRANSPORT_POLICY=relay` | `relayOnly` |
