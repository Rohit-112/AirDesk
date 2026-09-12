package com.share.app.config

/** Single source of truth for what the product is called and how it describes itself. */
object Brand {
    const val NAME = "Knotic"
    const val TAGLINE = "Send files and text between your phone and PC"
    const val EYEBROW = "Free · No account · Encrypted"
    const val INTRO = "Pair with a 6-digit code or scan the QR. Works with the Knotic app and the website on any device."
    const val VERSION = "1.0.1"
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
            body = "Paste text or attach a file. Files go straight between the devices, up to 20 MB.",
        ),
        InfoItem(
            title = "It closes itself",
            body = "The session ends after 15 minutes of inactivity, or the moment you disconnect. Nothing is kept.",
        ),
    )

    val privacy = listOf(
        InfoItem(
            title = "Files go device to device",
            body = "When a direct route exists, files travel straight between your two devices over an encrypted connection and never touch a server.",
        ),
        InfoItem(
            title = "Text is end to end encrypted",
            body = "The two devices agree on a key directly with each other, and only the public halves ever travel. The key is never sent, never stored, and is different for every session - so the database only ever holds ciphertext that the server cannot open.",
        ),
        InfoItem(
            title = "No account, nothing kept",
            body = "You are signed in anonymously. A session is deleted when you disconnect, and closes itself after 15 minutes of inactivity.",
        ),
    )

    val faq = listOf(
        InfoItem(
            title = "How do I send files from my phone to my PC without a cable?",
            body = "Open ${Brand.NAME} on both devices. One shows a 6-digit code and a QR code - scan the QR or type the code on the other. Once paired, attach a file or paste text and it arrives on the other device in seconds.",
        ),
        InfoItem(
            title = "Do I need an account?",
            body = "No. There is no sign-up and no login. The other device can use this app or simply open ${AppConfig().siteUrl} in a browser.",
        ),
        InfoItem(
            title = "Is it safe? Are my files uploaded to a server?",
            body = "When a direct connection is possible, files travel straight between your two devices over an encrypted connection and are never stored on a server. Text is end-to-end encrypted with a key unique to each session, so the server cannot read it. Sessions close after 15 minutes of inactivity.",
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
            body = "Some mobile networks block direct device-to-device connections. Put both devices on the same Wi-Fi network, or switch your phone from mobile data to Wi-Fi, then pair again.",
        ),
    )
}
