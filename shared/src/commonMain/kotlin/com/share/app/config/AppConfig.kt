package com.share.app.config

data class IceServerConfig(
    val urls: List<String>,
    val username: String = "",
    val credential: String = "",
)

/**
 * Runtime switches that the web client reads from `.env`. Keep them in step
 * with the deployed site so both clients behave the same.
 */
data class AppConfig(
    /** The public web origin. QR codes point here, so any camera can open them. */
    val siteUrl: String = "https://getknotic.web.app",
    /**
     * Cloud Storage fallback for files that cannot go peer to peer. Off by
     * default: relayed bytes are metered bandwidth, and newer Firebase projects
     * need the Blaze plan for Storage at all.
     */
    val relayEnabled: Boolean = false,
    val iceServers: List<IceServerConfig> = DEFAULT_ICE_SERVERS + TURN_SERVERS,
    /** Force every connection through TURN. Only useful for testing a relay. */
    val relayOnly: Boolean = false,
) {
    companion object {
        /** More reflexive-address sources means more chances at a direct route. */
        val DEFAULT_ICE_SERVERS = listOf(
            IceServerConfig(
                urls = listOf(
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302",
                    "stun:stun2.l.google.com:19302",
                    "stun:stun3.l.google.com:19302",
                    "stun:stun4.l.google.com:19302",
                ),
            ),
            IceServerConfig(urls = listOf("stun:stun.cloudflare.com:3478")),
        )

        /**
         * A TURN server is the proper fix for strict networks such as carrier
         * CGNAT. Fill in the same values as `VITE_WEBRTC_TURN_*` on the web.
         */
        val TURN_SERVERS: List<IceServerConfig> = emptyList()
    }
}
