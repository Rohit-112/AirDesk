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

# Shared unit tests (protocol, crypto, codecs)
./gradlew :shared:jvmTest

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
"<jdk>\bin\javaw.exe" -jar Knotic-windows-x64-1.0.1.jar
```

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
│   ├── repository/  Interfaces for auth, session node, signalling, relay, storage
│   ├── crypto/      SessionCipher interface
│   ├── webrtc/      PeerConnectionPort - the transport, narrowed
│   ├── session/     SessionEngine, WebRtcTransport, WebRtcLink, FileTransferEngine
│   └── usecase/     PairingUseCase, TransferUseCase, PreferencesUseCase
├── data/            Firebase (GitLive), cryptography-kotlin, DataStore, FileKit
├── di/              Koin modules; platformModule is expect/actual
└── ui/              Compose screens - read state, send intents, nothing deeper
    ├── home/        HomeContract (UiState, Intent, Effect), HomeViewModel, HomeScreen
    ├── about/       How it works, privacy, FAQ
    ├── components/  Cards, buttons, status chip, the logo and hero graphics
    ├── navigation/  Type-safe routes
    └── theme/       The web client's design tokens, light and dark
```

Platform source sets:

| Source set | Holds |
| --- | --- |
| `mobileMain` (Android + iOS) | webrtc-kmp peer connection, camera QR scanner |
| `androidMain` / `iosMain` | Native data channels, clipboard, DataStore path, entry points |
| `jvmMain` (desktop) | webrtc-java, AWT clipboard, window drag and drop, Firebase desktop init |

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

### Why the data channels are native

webrtc-kmp sends every data channel frame as binary and hides the frame type on
receive. The web client tells control messages (`NAME:`, `END`, `DISCONNECT`)
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
