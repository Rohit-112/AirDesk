package com.share.app.domain.policy

import dev.whyoleg.cryptography.random.CryptographyRandom

object PairingCode {
    const val LENGTH = 6
    private const val RANGE = 900_000
    private const val OFFSET = 100_000
    private val PATTERN = Regex("^[0-9]{6}$")
    private val LOOSE_MATCH = Regex("(^|[^0-9])([0-9]{6})([^0-9]|$)")

    fun sanitize(value: String): String = value.filter { it in '0'..'9' }.take(LENGTH)

    fun isValid(value: String): Boolean = PATTERN.matches(value)

    /**
     * The code is the only thing guarding an unclaimed session, so it comes from
     * a cryptographic source. `nextInt(until)` rejects the final partial bucket,
     * so every code stays equally likely.
     */
    fun generate(): String = (OFFSET + CryptographyRandom.nextInt(RANGE)).toString()

    /** Scanning this with any camera app opens the site and joins on its own. */
    fun buildJoinUrl(siteUrl: String, code: String): String =
        if (code.isEmpty()) "" else "${siteUrl.trimEnd('/')}/?code=$code"

    /**
     * Pulls a pairing code out of whatever a QR or link turned out to contain:
     * our own join URL, a custom `knotic://` link, or a bare number read off a
     * screen. Anything else is rejected rather than guessed at.
     */
    fun extract(raw: String): String? {
        val text = raw.trim()
        if (isValid(text)) return text

        // A URL is explicit about what it carries: trust its code param or nothing.
        if (text.contains("://")) {
            val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
            val param = query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == "code" }
                ?.get(1)
            return param?.takeIf(::isValid)
        }

        // Must be an isolated run of six, so 1234567 is not read as 123456.
        return LOOSE_MATCH.find(text)?.groupValues?.get(2)
    }
}
