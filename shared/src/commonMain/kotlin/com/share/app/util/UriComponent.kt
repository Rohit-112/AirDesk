package com.share.app.util

/**
 * JavaScript's `encodeURIComponent` / `decodeURIComponent`, byte for byte, so
 * relay announcements round-trip with the web client.
 */
object UriComponent {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    private const val HEX = "0123456789ABCDEF"

    fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (byte >= 0 && char in UNRESERVED) {
                append(char)
            } else {
                val unsigned = byte.toInt() and 0xFF
                append('%').append(HEX[unsigned shr 4]).append(HEX[unsigned and 0x0F])
            }
        }
    }

    /** Returns null for malformed input, where JavaScript would throw. */
    fun decode(value: String): String? {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                if (index + 2 >= value.length) return null
                val high = value[index + 1].digitToIntOrNull(16) ?: return null
                val low = value[index + 2].digitToIntOrNull(16) ?: return null
                bytes += ((high shl 4) or low).toByte()
                index += 3
            } else {
                char.toString().encodeToByteArray().forEach { bytes += it }
                index += 1
            }
        }
        return try {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
