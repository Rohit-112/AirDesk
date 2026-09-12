package com.share.app.domain.session

import com.share.app.config.AppConfig
import com.share.app.domain.crypto.SessionCipher
import com.share.app.domain.crypto.SessionKeyPair
import com.share.app.domain.crypto.SharedSessionKey
import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.AuthStatus
import com.share.app.domain.model.HistoryAction
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.HistoryStatus
import com.share.app.domain.model.IncomingFile
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.model.SessionRole
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.model.TransportMode
import com.share.app.domain.policy.FilePayload
import com.share.app.domain.policy.PairingCode
import com.share.app.domain.policy.SessionLimits
import com.share.app.domain.repository.AuthRepository
import com.share.app.domain.repository.FileSystemRepository
import com.share.app.domain.repository.HistoryRepository
import com.share.app.domain.repository.PreferencesRepository
import com.share.app.domain.repository.RelayRepository
import com.share.app.domain.repository.SessionRemoteRepository
import com.share.app.domain.repository.SignalingRepository
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import com.share.app.util.AppLog
import com.share.app.util.currentTimeMillis
import com.share.app.util.newId
import com.share.app.util.suspendRunCatching
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The sync engine: anonymous sign-in, hosting and joining a session, the end to
 * end key exchange, presence, inactivity, text and file delivery.
 *
 * A port of the web client's `useFirebaseSync`, so both clients share one
 * protocol and can pair with each other. It is an application-wide singleton
 * that lives above navigation - leaving a screen never tears the session down.
 *
 * Everything runs on one confined dispatcher, which gives the same run-to-
 * completion guarantees the browser's event loop gives the web version.
 * Pairing actions additionally hold [pairingMutex], so a tap on Disconnect can
 * never interleave with a join that is still talking to the server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngine(
    private val config: AppConfig,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRemoteRepository,
    signalingRepository: SignalingRepository,
    private val relayRepository: RelayRepository,
    private val cipher: SessionCipher,
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: HistoryRepository,
    private val fileSystemRepository: FileSystemRepository,
    peerConnectionFactory: PeerConnectionFactoryPort,
) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
            AppLog.e(error) { "Unhandled error in the session engine" }
        },
    )
    private val pairingMutex = Mutex()

    private val core = MutableStateFlow(AppSessionState())
    private var started = false
    private var autoHosted = false
    private var pendingDeepLinkCode: String? = null
    private var deviceId: String? = null

    /** The code whose listeners are attached; null when not in a session. */
    private var activeSessionCode: String? = null
    private var listenersJob: Job? = null
    private var listenerGeneration = 0
    private var lastReceived: String? = null
    private var inactivityJob: Job? = null

    // End to end key material. The private key never leaves the cipher, and the
    // derived key is never written anywhere.
    private var keyPair: SessionKeyPair? = null
    private var peerPublicKey: String? = null
    private var sharedKey: SharedSessionKey? = null
    private var keyGeneration = 0
    private var pendingIncoming: Pair<String, Boolean>? = null

    private val transport = WebRtcTransport(
        scope = scope,
        peerConnectionFactory = peerConnectionFactory,
        signalingRepository = signalingRepository,
        onMessage = { message -> fileTransfer.enqueue(message) },
        onClosed = { fileTransfer.resetTransfers() },
    )

    private val fileTransfer: FileTransferEngine = FileTransferEngine(
        scope = scope,
        getChannel = { transport.channel() },
        relay = object : RelayPort {
            override fun isAvailable(): Boolean = relayAvailable()
            override suspend fun send(file: OutgoingFile, bytes: ByteArray, onProgress: (Long) -> Unit) =
                relaySend(file, bytes, onProgress)
        },
        history = historyRepository,
        onIncomingFile = { file -> core.update { it.copy(incomingFile = file, incomingText = null) } },
        onError = ::setError,
        onPeerDisconnect = { transport.restart() },
        canStillConnect = { !transport.state.value.exhausted },
    )

    val state: StateFlow<AppSessionState> = combine(
        core,
        transport.state,
        fileTransfer.state,
    ) { session, link, transfers ->
        session.copy(
            outgoingTransfer = transfers.outgoing,
            incomingTransfer = transfers.incoming,
            fileTransferReady = link.ready,
            transportMode = when {
                link.ready -> TransportMode.P2P
                config.relayEnabled && session.isLinked -> TransportMode.RELAY
                else -> TransportMode.UNAVAILABLE
            },
            webrtcStatus = link.status,
            webrtcError = link.error,
            webrtcDiagnostics = link.diagnostics,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppSessionState())

    /** Starts sign-in and the background watchers. Safe to call more than once. */
    fun start() {
        scope.launch {
            if (started) return@launch
            started = true
            deviceId = suspendRunCatching { preferencesRepository.deviceId() }
                .getOrElse { "dev-${newId("local")}" }

            launch { observeAuth() }
            launch {
                sessionRepository.observeConnection().collect { connected ->
                    core.update { it.copy(backendConnected = connected) }
                }
            }
            launch { syncTransportInputs() }
            launch { keepHostRemovalRegistered() }
            launch { driveInactivityTimer() }
        }
    }

    /* ------------------------------------------------------------------ *
     * Watchers
     * ------------------------------------------------------------------ */

    private suspend fun observeAuth() {
        authRepository.observeUserId().collect { uid ->
            if (uid == null) {
                core.update { it.copy(authStatus = AuthStatus.AUTHENTICATING) }
                signIn()
            } else {
                core.update { it.copy(userId = uid, authStatus = AuthStatus.READY) }
                onAuthReady()
            }
        }
    }

    private suspend fun signIn() {
        suspendRunCatching { authRepository.signInAnonymously() }.onFailure { error ->
            AppLog.e(error) { "Anonymous sign-in failed" }
            core.update { it.copy(error = "Sign-in failed. Please try again.", authStatus = AuthStatus.ERROR) }
        }
    }

    /**
     * A code is ready as soon as the app settles, exactly once per process.
     * A deep link owns the launch instead; hosting a second session would fight it.
     */
    private fun onAuthReady() {
        val deepLink = pendingDeepLinkCode
        if (deepLink != null) {
            pendingDeepLinkCode = null
            autoHosted = true
            scope.launch { joinReplacingCurrent(deepLink) }
            return
        }
        if (autoHosted) return
        autoHosted = true
        scope.launch { pairingMutex.withLock { createSessionLocked(PairingCode.generate()) } }
    }

    private suspend fun syncTransportInputs() {
        core.map { session ->
            TransportInputs(
                sessionCode = session.sessionCode,
                role = session.role,
                active = session.isLinked,
                signalingOnline = session.backendConnected,
                fallbackAvailable = config.relayEnabled && session.isLinked,
            )
        }.distinctUntilChanged().collect { transport.update(it) }
    }

    /**
     * The host's session dies with the host. onDisconnect handlers are dropped
     * once they fire, so this re-registers whenever the socket comes back.
     */
    private suspend fun keepHostRemovalRegistered() {
        core.map { listOf(it.role, it.sessionStatus, it.sessionCode, it.backendConnected) }
            .distinctUntilChanged()
            .collect {
                val session = core.value
                if (session.role == SessionRole.HOST &&
                    session.sessionStatus == SessionStatus.CONNECTED &&
                    session.sessionCode.isNotEmpty() &&
                    session.backendConnected
                ) {
                    suspendRunCatching { sessionRepository.registerSessionRemovalOnDisconnect(session.sessionCode) }
                }
            }
    }

    private suspend fun driveInactivityTimer() {
        core.map { it.sessionStatus }.distinctUntilChanged().collect { status ->
            if (status == SessionStatus.CONNECTED) resetInactivityTimer() else cancelInactivityTimer()
        }
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(SessionLimits.INACTIVITY_LIMIT_MS)
            inactivityJob = null
            pairingMutex.withLock { handleInactivityLocked() }
        }
    }

    private fun cancelInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    /* ------------------------------------------------------------------ *
     * Public actions
     * ------------------------------------------------------------------ */

    fun retrySignIn() {
        scope.launch {
            if (core.value.authStatus != AuthStatus.ERROR) return@launch
            core.update { it.copy(authStatus = AuthStatus.AUTHENTICATING, error = null) }
            signIn()
        }
    }

    /** Any touch, key or return to the foreground. Keeps a live session open. */
    fun onUserActivity() {
        scope.launch {
            if (core.value.sessionStatus == SessionStatus.CONNECTED) resetInactivityTimer()
        }
    }

    fun clearError() {
        scope.launch { core.update { it.copy(error = null) } }
    }

    /** Host a session under [code], or a fresh random code. */
    fun createSession(code: String? = null) {
        scope.launch { pairingMutex.withLock { createSessionLocked(code) } }
    }

    /** Throw away whatever is running and hand out a brand new code. */
    fun startNewCode() {
        scope.launch {
            pairingMutex.withLock {
                val next = PairingCode.generate()
                core.update { it.copy(sessionCode = next, sessionError = null) }
                if (core.value.sessionStatus == SessionStatus.CONNECTED) disconnectLocked()
                createSessionLocked(next)
            }
        }
    }

    /** Join someone else's session, leaving the current one first. */
    fun joinSession(code: String) {
        scope.launch { joinReplacingCurrent(code) }
    }

    /** A code that arrived from outside the app: a scanned link or a launch intent. */
    fun handleDeepLink(raw: String) {
        val code = PairingCode.extract(raw) ?: return
        scope.launch {
            autoHosted = true
            if (core.value.authStatus == AuthStatus.READY) {
                joinReplacingCurrent(code)
            } else {
                pendingDeepLinkCode = code
            }
        }
    }

    fun disconnect() {
        scope.launch { pairingMutex.withLock { disconnectLocked() } }
    }

    /**
     * Called the moment someone reaches for the join field. Their own session is
     * about to be abandoned, so delete it now rather than leaving it to expire.
     */
    fun beginJoin() {
        scope.launch {
            pairingMutex.withLock {
                core.update { it.copy(joinIntent = true) }
                if (core.value.sessionCode.isNotEmpty()) disconnectLocked()
            }
        }
    }

    /** Force a fresh attempt at the direct connection. */
    fun reconnectFileSharing() {
        scope.launch {
            val session = core.value
            if (session.sessionStatus != SessionStatus.CONNECTED || !session.peerOnline || session.sessionCode.isEmpty()) {
                return@launch
            }
            transport.restart()
        }
    }

    fun sendText(text: String) {
        scope.launch { sendTextInternal(text) }
    }

    fun sendFile(file: OutgoingFile) {
        scope.launch {
            if (!core.value.isLinked) {
                setError("Link a device first.")
                return@launch
            }
            fileTransfer.sendFile(file)
        }
    }

    /** Saves whatever is in the inbox. Suspends while the save dialog is open. */
    suspend fun saveIncomingFile() = withContext(dispatcher) { saveIncomingFileInternal() }

    /** Saves a file from the activity log again, while its bytes are still held. */
    suspend fun saveHistoryFile(id: String) = withContext(dispatcher) {
        val item = historyRepository.history.value.firstOrNull { it.id == id } ?: return@withContext
        val bytes = historyRepository.payload(id)
        if (bytes == null) {
            setError("That file is no longer held on this device.")
            return@withContext
        }
        suspendRunCatching { fileSystemRepository.saveFile(item.title, bytes) }
            .onFailure { setError("Unable to save the file.") }
    }

    /** Best effort, for a desktop window closing. Mobile relies on onDisconnect. */
    suspend fun shutdown() {
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
            withContext(dispatcher) {
                transport.update(TransportInputs())
                suspendRunCatching { clearRemotePresence() }
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * Pairing
     * ------------------------------------------------------------------ */

    private suspend fun joinReplacingCurrent(code: String) {
        pairingMutex.withLock {
            if (core.value.sessionStatus == SessionStatus.CONNECTED) disconnectLocked()
            joinSessionLocked(code)
        }
    }

    private suspend fun createSessionLocked(overrideCode: String?) {
        val session = core.value
        val userId = session.userId
        if (session.authStatus != AuthStatus.READY || userId == null) {
            core.update { it.copy(sessionError = "Authentication not ready.") }
            return
        }

        val sanitized = PairingCode.sanitize(overrideCode ?: session.sessionCode)
        val code = if (PairingCode.isValid(sanitized)) sanitized else PairingCode.generate()
        val device = deviceId.orEmpty()

        core.update {
            it.copy(
                sessionCode = code,
                joinIntent = false,
                sessionError = null,
                sessionStatus = SessionStatus.CONNECTING,
                role = SessionRole.HOST,
            )
        }

        suspendRunCatching {
            val existing = sessionRepository.getSession(code)
            val existingHost = existing?.hostId
            val existingDevice = existing?.hostDeviceId

            if (existingHost != null && existingHost != userId) {
                failPairing("This session code is already in use.")
                return
            }
            if (existingHost == userId && existingDevice != null && existingDevice != device) {
                failPairing("Session already active on another device.")
                return
            }
            if (existingHost == userId && existingDevice == device) {
                suspendRunCatching { sessionRepository.removeSession(code) }
            }

            sessionRepository.updateSession(
                code,
                mapOf(
                    "hostId" to userId,
                    "hostDeviceId" to device,
                    "hostOnline" to true,
                    "guestOnline" to (existing?.guestOnline ?: false),
                    "hostClipboard" to (existing?.hostClipboard ?: ""),
                    "guestClipboard" to (existing?.guestClipboard ?: ""),
                    "updatedAt" to currentTimeMillis(),
                ),
            )

            attachSessionListener(code, SessionRole.HOST)
            core.update { it.copy(sessionStatus = SessionStatus.CONNECTED) }
        }.onFailure { error ->
            AppLog.e(error) { "createSession failed" }
            failPairing("Unable to create session right now.")
        }
    }

    private suspend fun joinSessionLocked(overrideCode: String) {
        val session = core.value
        val userId = session.userId
        if (session.authStatus != AuthStatus.READY || userId == null) {
            core.update { it.copy(sessionError = "Authentication not ready.") }
            return
        }

        val code = PairingCode.sanitize(overrideCode.trim())
        if (!PairingCode.isValid(code)) {
            core.update { it.copy(sessionError = "Enter a valid 6-digit session code.") }
            return
        }

        core.update {
            it.copy(
                sessionCode = code,
                joinIntent = false,
                sessionError = null,
                sessionStatus = SessionStatus.CONNECTING,
                role = SessionRole.GUEST,
            )
        }

        suspendRunCatching {
            val existing = sessionRepository.getSession(code)
            if (existing?.hostId == null || !existing.hostOnline) {
                failPairing("Session not found or host is offline.")
                return
            }
            if (existing.hostId == userId) {
                failPairing("You cannot join your own session.")
                return
            }
            if (existing.guestId != null && existing.guestId != userId) {
                failPairing("This session already has a guest.")
                return
            }

            sessionRepository.updateSession(
                code,
                mapOf(
                    "guestId" to userId,
                    "guestDeviceId" to deviceId.orEmpty(),
                    "guestOnline" to true,
                    "updatedAt" to currentTimeMillis(),
                ),
            )

            attachSessionListener(code, SessionRole.GUEST)
            core.update { it.copy(sessionStatus = SessionStatus.CONNECTED) }
        }.onFailure { error ->
            AppLog.e(error) { "joinSession failed" }
            failPairing("Unable to join session right now.")
        }
    }

    private fun failPairing(message: String) {
        core.update { it.copy(sessionError = message, sessionStatus = SessionStatus.DISCONNECTED) }
    }

    /** Blanks the code as well as the connection. A dead code on screen is no use to anyone. */
    private suspend fun disconnectLocked() {
        val session = core.value
        val code = session.sessionCode
        val role = session.role

        suspendRunCatching {
            // Otherwise the pending onDisconnect writes would recreate or re-delete
            // the node the moment this socket closes.
            if (code.isNotEmpty()) {
                suspendRunCatching { sessionRepository.cancelDisconnectCleanup(code, role) }
                suspendRunCatching { sessionRepository.cancelSessionRemovalOnDisconnect(code) }
            }
            if (role == SessionRole.HOST && code.isNotEmpty()) {
                // Works from the code alone, so it still fires if listeners were
                // already detached - the path taken when joining another device.
                sessionRepository.removeSession(code)
            } else {
                clearRemotePresence()
                attemptDeleteSessionIfInactive()
            }
        }.onFailure { setError("Unable to clear session data.") }

        core.update { it.copy(sessionCode = "") }
        resetLocalConnection()
    }

    private suspend fun handleInactivityLocked() {
        val session = core.value
        if (session.sessionStatus != SessionStatus.CONNECTED) return

        val code = activeSessionCode
        val cleanupFailed = suspendRunCatching {
            if (code != null) {
                suspendRunCatching { sessionRepository.cancelDisconnectCleanup(code, session.role) }
                suspendRunCatching { sessionRepository.cancelSessionRemovalOnDisconnect(code) }
            }
            if (session.role == SessionRole.HOST && code != null) {
                // The host has gone idle: take the session along rather than
                // leaving a node nobody will ever come back to.
                sessionRepository.removeSession(code)
            } else {
                clearRemotePresence()
                attemptDeleteSessionIfInactive()
            }
        }.isFailure

        resetLocalConnection()
        setError(
            if (cleanupFailed) {
                "Session closed after 15 minutes of inactivity (cleanup failed)."
            } else {
                "Session closed after 15 minutes of inactivity."
            },
        )
    }

    private fun resetLocalConnection() {
        resetSessionKeys()
        listenerGeneration += 1
        listenersJob?.cancel()
        listenersJob = null
        activeSessionCode = null
        lastReceived = null
        core.update { it.copy(peerOnline = false, sessionStatus = SessionStatus.DISCONNECTED, sessionError = null) }
        cancelInactivityTimer()
    }

    private fun buildPresenceUpdate(role: SessionRole): Map<String, Any?> = mapOf(
        "${role.key}Online" to false,
        "${role.key}Clipboard" to "",
        "updatedAt" to currentTimeMillis(),
    )

    private suspend fun clearRemotePresence() {
        val code = activeSessionCode ?: return
        sessionRepository.updateSession(code, buildPresenceUpdate(core.value.role))
    }

    private suspend fun attemptDeleteSessionIfInactive() {
        val code = activeSessionCode ?: return
        suspendRunCatching {
            val existing = sessionRepository.getSession(code) ?: return
            if (!existing.hostOnline && !existing.guestOnline) {
                sessionRepository.removeSession(code)
            }
        }.onFailure {
            core.update { it.copy(sessionError = "Unable to clear inactive session data.") }
        }
    }

    /* ------------------------------------------------------------------ *
     * Session listeners and the key exchange
     * ------------------------------------------------------------------ */

    private fun resetSessionKeys() {
        keyGeneration += 1
        keyPair = null
        peerPublicKey = null
        sharedKey = null
        pendingIncoming = null
        core.update { it.copy(secureChannelReady = false) }
    }

    private fun attachSessionListener(code: String, role: SessionRole) {
        activeSessionCode = code
        listenersJob?.cancel()
        listenerGeneration += 1
        val generation = listenerGeneration

        resetSessionKeys()
        val keys = keyGeneration
        val peer = role.peer
        var isFirstClipboardSnapshot = true

        listenersJob = scope.launch {
            // Publish our public half; the peer's arrives through the listener below.
            launch {
                suspendRunCatching {
                    val pair = cipher.generateKeyPair()
                    if (keys != keyGeneration) return@launch
                    keyPair = pair
                    sessionRepository.updateSession(code, mapOf("${role.key}PublicKey" to pair.publicKey))
                    deriveIfPossible(keys)
                }.onFailure { error ->
                    AppLog.e(error) { "Publishing the session public key failed" }
                    if (keys == keyGeneration) setError("This device could not set up encryption.")
                }
            }

            // Narrow listeners rather than one on the whole node, so the
            // signalling traffic underneath it never reaches this client.
            launch {
                sessionRepository.observeString(code, "hostId").collect { hostId ->
                    if (hostId == null) scope.launch { onHostGone(generation, role) }
                }
            }
            launch {
                sessionRepository.observeBoolean(code, "${peer.key}Online").collect { online ->
                    core.update { it.copy(peerOnline = online) }
                }
            }
            launch {
                sessionRepository.observeString(code, "${peer.key}PublicKey").collect { value ->
                    if (value.isNullOrEmpty() || value == peerPublicKey) return@collect
                    peerPublicKey = value
                    deriveIfPossible(keys)
                }
            }
            launch {
                sessionRepository.observeString(code, "${peer.key}Clipboard").collect { value ->
                    val isRestored = isFirstClipboardSnapshot
                    isFirstClipboardSnapshot = false
                    if (value.isNullOrEmpty() || value == lastReceived) return@collect
                    lastReceived = value
                    handleIncoming(value, recordHistory = !isRestored)
                }
            }
        }

        scope.launch { suspendRunCatching { sessionRepository.registerDisconnectCleanup(code, role) } }
    }

    /** The session node lost its host: deleted by the other device or by the server. */
    private suspend fun onHostGone(generation: Int, role: SessionRole) {
        pairingMutex.withLock {
            if (generation != listenerGeneration) return
            core.update { it.copy(sessionCode = "") }
            resetLocalConnection()
            core.update {
                it.copy(
                    sessionError = if (role == SessionRole.GUEST) {
                        "The other device ended the session."
                    } else {
                        "This session was closed. Generate a new code to continue."
                    },
                )
            }
        }
    }

    /** Runs whenever either half of the exchange lands. */
    private suspend fun deriveIfPossible(generation: Int) {
        val pair = keyPair ?: return
        val peerKey = peerPublicKey ?: return
        suspendRunCatching { cipher.deriveSharedKey(pair, peerKey) }
            .onSuccess { key ->
                if (generation != keyGeneration) return
                sharedKey = key
                core.update { it.copy(secureChannelReady = true) }
                // A message that landed before the key existed can be opened now.
                pendingIncoming?.let { (value, record) ->
                    pendingIncoming = null
                    handleIncoming(value, record)
                }
            }
            .onFailure { error ->
                if (generation != keyGeneration) return
                AppLog.e(error) { "Deriving the shared key failed" }
                sharedKey = null
                core.update { it.copy(secureChannelReady = false) }
                setError("Could not set up the secure channel with the other device.")
            }
    }

    private suspend fun handleIncoming(encrypted: String, recordHistory: Boolean) {
        val key = sharedKey
        if (key == null) {
            pendingIncoming = encrypted to recordHistory
            return
        }

        // Null is expected right after a key rotation: a message encrypted with
        // the previous key is still sitting in the database.
        val decrypted = cipher.decrypt(key, encrypted) ?: run {
            AppLog.w { "Ignoring a payload that this session's key cannot open." }
            return
        }
        if (decrypted.isEmpty()) return

        if (decrypted.startsWith(FilePayload.PREFIX)) {
            val parsed = FilePayload.parse(decrypted.removePrefix(FilePayload.PREFIX)) ?: return
            core.update {
                it.copy(
                    incomingFile = IncomingFile(parsed.name, parsed.size, parsed.contentType, storagePath = parsed.storagePath),
                    incomingText = null,
                )
            }
            return
        }

        core.update { it.copy(incomingText = decrypted, incomingFile = null) }
        if (recordHistory) {
            historyRepository.add(
                HistoryItem(
                    id = newId("hist"),
                    action = HistoryAction.RECEIVED_TEXT,
                    title = SessionLimits.normalizeTextPreview(decrypted),
                    timestampMillis = currentTimeMillis(),
                    size = decrypted.encodeToByteArray().size.toLong(),
                    status = HistoryStatus.SUCCESS,
                    text = decrypted,
                ),
            )
        }
    }

    /* ------------------------------------------------------------------ *
     * Payloads
     * ------------------------------------------------------------------ */

    private suspend fun sendTextInternal(text: String) {
        val session = core.value
        val code = activeSessionCode
        if (session.sessionStatus != SessionStatus.CONNECTED || code == null) {
            setError("Connect to a session first.")
            return
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length > SessionLimits.MAX_TEXT_CHARS) {
            setError("Text must be under 1000 characters.")
            return
        }

        val key = sharedKey
        if (key == null) {
            setError("Still setting up the secure channel. Try again in a moment.")
            return
        }

        val encrypted = cipher.encrypt(key, trimmed)
        if (encrypted == null) {
            setError("Unable to encrypt text.")
            return
        }

        suspendRunCatching {
            sessionRepository.updateSession(
                code,
                mapOf(
                    "${session.role.key}Clipboard" to encrypted,
                    "updatedAt" to currentTimeMillis(),
                ),
            )
            historyRepository.add(
                HistoryItem(
                    id = newId("hist"),
                    action = HistoryAction.SENT_TEXT,
                    title = SessionLimits.normalizeTextPreview(trimmed),
                    timestampMillis = currentTimeMillis(),
                    size = trimmed.encodeToByteArray().size.toLong(),
                    status = HistoryStatus.SUCCESS,
                    text = trimmed,
                ),
            )
        }.onFailure { error ->
            AppLog.e(error) { "sendText failed" }
            setError("Unable to send text right now.")
        }
    }

    private fun relayAvailable(): Boolean = config.relayEnabled && core.value.isLinked

    /**
     * Files that cannot go peer to peer are uploaded to Storage and announced
     * over the session node, in the same shape the web client uses.
     */
    private suspend fun relaySend(file: OutgoingFile, bytes: ByteArray, onProgress: (Long) -> Unit) {
        val code = activeSessionCode ?: error("No active session to relay through.")
        val role = core.value.role
        val path = "sessions/$code/${currentTimeMillis()}-${SessionLimits.safeFileName(file.name)}"

        relayRepository.upload(path, bytes, file.contentType, onProgress)

        val key = sharedKey ?: error("The secure channel is not ready yet.")
        val payload = FilePayload.build(path, file.name, file.size, file.contentType)
        val encrypted = cipher.encrypt(key, FilePayload.PREFIX + payload)
            ?: error("Unable to encrypt the file announcement.")

        sessionRepository.updateSession(
            code,
            mapOf(
                "${role.key}Clipboard" to encrypted,
                "updatedAt" to currentTimeMillis(),
            ),
        )
    }

    private suspend fun saveIncomingFileInternal() {
        val file = core.value.incomingFile ?: return

        val localId = file.localFileId
        if (localId != null) {
            val bytes = historyRepository.payload(localId)
            if (bytes == null) {
                setError("Unable to download the incoming file.")
                return
            }
            suspendRunCatching { fileSystemRepository.saveFile(file.name, bytes) }
                .onSuccess { saved -> if (saved) clearIncomingFile(file) }
                .onFailure { setError("Unable to download the incoming file.") }
            return
        }

        val storagePath = file.storagePath ?: return
        val code = activeSessionCode
        if (core.value.sessionStatus != SessionStatus.CONNECTED || code == null) return

        suspendRunCatching {
            val bytes = relayRepository.download(storagePath, SessionLimits.MAX_FILE_SIZE)
            val id = newId("file")
            historyRepository.add(
                HistoryItem(
                    id = id,
                    action = HistoryAction.RECEIVED_FILE,
                    title = file.name,
                    timestampMillis = currentTimeMillis(),
                    size = bytes.size.toLong(),
                    status = HistoryStatus.SUCCESS,
                    hasPayload = true,
                ),
                payload = bytes,
            )

            suspendRunCatching { relayRepository.delete(storagePath) }
            val peerField = "${core.value.role.peer.key}Clipboard"
            sessionRepository.updateSession(code, mapOf(peerField to "", "updatedAt" to currentTimeMillis()))

            // Now held locally, so a cancelled save dialog can simply be retried.
            val local = file.copy(storagePath = null, localFileId = id, size = bytes.size.toLong())
            core.update { it.copy(incomingFile = local) }

            if (fileSystemRepository.saveFile(file.name, bytes)) clearIncomingFile(local)
        }.onFailure { error ->
            AppLog.e(error) { "Downloading the relayed file failed" }
            setError("Unable to download the incoming file.")
        }
    }

    private fun clearIncomingFile(file: IncomingFile) {
        core.update { if (it.incomingFile == file) it.copy(incomingFile = null) else it }
    }

    private fun setError(message: String) {
        core.update { it.copy(error = message) }
    }

    private companion object {
        const val SHUTDOWN_TIMEOUT_MS = 1_500L
    }
}
