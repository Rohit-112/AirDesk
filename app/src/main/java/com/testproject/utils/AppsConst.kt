package com.testproject.utils

object AppsConst {

    // DataStore
    const val DATA_STORE = "AirDesk_DataStore"
    const val SESSION_CODE_KEY = "session_code"
    const val IS_HOST_KEY = "is_host"
    const val IS_LOGGED_IN = "is_logged_in"
    const val LAST_SENT_TEXT_KEY = "last_sent_text"

    // Firebase Nodes
    const val FB_SESSIONS = "sessions"
    const val FB_HOST_ID = "hostId"
    const val FB_GUEST_ID = "guestId"
    const val FB_HOST_ONLINE = "hostOnline"
    const val FB_GUEST_ONLINE = "guestOnline"
    const val FB_HOST_CLIPBOARD = "hostClipboard"
    const val FB_GUEST_CLIPBOARD = "guestClipboard"

    // File Sharing Limits
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB Limit
    const val FILE_CLEANUP_HOURS = 1 // Auto-delete after 1 hour

    // Protocol Prefixes
    const val FILE_PROTOCOL_PREFIX = "FILE:"
    const val FILE_PROTOCOL_SEPARATOR = "|"

    // Encryption
    const val KEYSET_NAME = "airdesk_keyset"
    const val PREFS_NAME = "airdesk_prefs"
    const val MASTER_KEY_URI = "android-keystore://airdesk_master_key"

    // Intent Extras
    const val EXTRA_TEXT = "text"

}
