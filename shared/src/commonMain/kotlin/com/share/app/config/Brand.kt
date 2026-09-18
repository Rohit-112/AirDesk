package com.share.app.config

/** Single source of truth for what the product is called and how it describes itself. */
object Brand {
    const val NAME = "Knotic"
    const val TAGLINE = "Send files and text between any two devices"
    const val EYEBROW = "Free · No sign-up · Encrypted"
    const val INTRO = "Pair with a 6-digit code or scan the QR. Phone to PC, PC to PC or phone to phone - with the Knotic app or the website."

    /**
     * Bumped with every release, together with `app-version` in the version
     * catalog; `./gradlew checkVersion` fails when the two disagree.
     */
    const val VERSION = "1.2.1"
    const val LEGAL_NAME = "Knotic"
}

data class InfoItem(val title: String, val body: String)

object AboutContent {
    val steps = listOf(
        InfoItem(
            title = "Open both devices",
            body = "A 6-digit code appears as soon as ${Brand.NAME} starts. Type it on the other device, or scan the QR, to link the two.",
        ),
        InfoItem(
            title = "Send anything",
            body = "Paste text or attach a file. It appears on the other device in seconds, up to 20 MB a file.",
        ),
        InfoItem(
            title = "It closes itself",
            body = "The session ends after 15 minutes of inactivity, or the moment you disconnect. Nothing is kept.",
        ),
    )

    // Outcomes, never mechanics: the same wording the website uses, and for the
    // same reason - how any of this is built is nobody else's business.
    val privacy = listOf(
        InfoItem(
            title = "Your files are not stored",
            body = "What you send goes to the other device and nowhere else. It is not kept, not scanned, and not used for anything.",
        ),
        InfoItem(
            title = "Text is encrypted",
            body = "Text is encrypted before it leaves your device and can only be read on the two devices in the session. We cannot read it.",
        ),
        InfoItem(
            title = "Images are converted on your device",
            body = "Saving a photo as JPG, PNG or WEBP happens on this device. The picture is never uploaded anywhere to be converted.",
        ),
        InfoItem(
            title = "No account, nothing kept",
            body = "There is no sign-up and no profile. A session is deleted when you disconnect, and closes itself after 15 minutes of inactivity.",
        ),
        InfoItem(
            title = "Anonymous usage and crash reports",
            body = "On Android and iPhone the app counts which features are used and reports crashes, so they can be fixed. Neither ever includes the text you send, a file, a file name or the pairing code.",
        ),
    )

    val faq = listOf(
        InfoItem(
            title = "How do I send files between two devices without a cable?",
            body = "Open ${Brand.NAME} on both devices - two computers, two phones, or one of each. One shows a 6-digit code and a QR code; scan or type it on the other. Once paired, attach a file or paste text and it arrives in seconds.",
        ),
        InfoItem(
            title = "Do I need an account?",
            body = "No. There is no sign-up and no login. The other device can use this app or simply open ${AppConfig().siteUrl} in a browser.",
        ),
        InfoItem(
            title = "Is it safe? Are my files stored anywhere?",
            body = "What you send goes to the other device over an encrypted connection and is not stored, scanned or used for anything. Text is encrypted so that only the two devices in the session can read it. Sessions close after 15 minutes of inactivity and take everything in them along.",
        ),
        InfoItem(
            title = "How large a file can I send?",
            body = "Up to 20 MB per file, one file at a time. Photos, screenshots, PDFs, documents and short video clips all fit.",
        ),
        InfoItem(
            title = "Can I copy text on my phone and paste it on my computer?",
            body = "Yes. Paste a link, an OTP, an address or a note into ${Brand.NAME} and it appears on your other device instantly, ready to copy.",
        ),
        InfoItem(
            title = "Why won't my two devices connect?",
            body = "Some mobile networks will not let two devices reach each other. Put both devices on the same Wi-Fi network, or switch your phone from mobile data to Wi-Fi, then pair again.",
        ),
        InfoItem(
            title = "Can I convert an iPhone HEIC photo to JPG?",
            body = "Yes. Send the photo across and ${Brand.NAME} offers Save as JPG, PNG or WEBP on the device that received it, or use Convert only with no pairing at all. The conversion happens on your device and the photo is never uploaded. It works on Android 9 and later and on iPhone; the desktop app cannot open HEIC yet, so convert it on the phone or in the browser.",
        ),
        InfoItem(
            title = "Does it preview the file I received?",
            body = "Yes. Photos and videos arrive with a preview you can see immediately, and the activity list shows a thumbnail of everything sent or received in the session, so you can tell one screenshot from another at a glance.",
        ),
    )
}
