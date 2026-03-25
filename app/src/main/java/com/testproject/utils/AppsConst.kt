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
    
    // WebRTC Signaling Nodes
    const val FB_WEBRTC_HOST = "hostSignaling"
    const val FB_WEBRTC_GUEST = "guestSignaling"

    // WebRTC Message Types
    const val TYPE_OFFER = "offer"
    const val TYPE_ANSWER = "answer"
    const val TYPE_CANDIDATE = "candidate"
    const val TYPE_DISCONNECT = "disconnect"

    // DataChannel Constants
    const val DC_LABEL = "fileTransfer"
    const val DC_MSG_NAME = "NAME:"
    const val DC_MSG_END = "END"
    const val DC_MSG_DISCONNECT = "DISCONNECT"

    // File Sharing Limits
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB Limit

    // Protocol Prefixes
    const val FILE_PROTOCOL_PREFIX = "FILE:"
    const val FILE_PROTOCOL_SEPARATOR = "|"

    // Security
    const val SECURITY_VIOLATION = "Security Violation"
}
