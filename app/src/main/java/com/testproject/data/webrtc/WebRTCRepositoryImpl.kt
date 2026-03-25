package com.testproject.data.webrtc

import android.content.Context
import com.google.firebase.database.*
import com.google.gson.Gson
import com.testproject.domain.webrtc.*
import com.testproject.utils.AppsConst.FB_SESSIONS
import com.testproject.utils.AppsConst.FB_WEBRTC_GUEST
import com.testproject.utils.AppsConst.FB_WEBRTC_HOST
import com.testproject.utils.AppsConst.TYPE_ANSWER
import com.testproject.utils.AppsConst.TYPE_CANDIDATE
import com.testproject.utils.AppsConst.TYPE_DISCONNECT
import com.testproject.utils.AppsConst.TYPE_OFFER
import com.testproject.utils.AppsConst.DC_LABEL
import com.testproject.utils.AppsConst.DC_MSG_NAME
import com.testproject.utils.AppsConst.DC_MSG_END
import com.testproject.utils.AppsConst.DC_MSG_DISCONNECT
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.*
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WebRTCRepository {

    private val db = FirebaseDatabase.getInstance().getReference(FB_SESSIONS)
    private var sessionCode: String? = null
    private var isHost: Boolean = false

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var dataChannel: DataChannel? = null

    private val _connectionState = MutableStateFlow<WebRTCState>(WebRTCState.Idle)
    override val connectionState: StateFlow<WebRTCState> = _connectionState.asStateFlow()

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 1)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var signalingListener: ChildEventListener? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    override fun initialize(sessionCode: String, isHost: Boolean) {
        synchronized(this) {
            if (this.sessionCode == sessionCode && this.isHost == isHost && 
                (_connectionState.value == WebRTCState.Connected || _connectionState.value == WebRTCState.Connecting)) {
                return
            }

            closeInternal()
            
            this.sessionCode = sessionCode
            this.isHost = isHost
            _connectionState.value = WebRTCState.Connecting

            initializeWebRTCFactory()
            setupPeerConnection()
            
            if (isHost) {
                setupDataChannel()
            }
            
            setupSignaling()
        }
    }

    private fun initializeWebRTCFactory() {
        try {
            if (factory == null) {
                val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(initOptions)
                
                val options = PeerConnectionFactory.Options()
                factory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .createPeerConnectionFactory()
            }
        } catch (e: Exception) {
            _connectionState.value = WebRTCState.Error("WebRTC Init Failed: ${e.message}")
        }
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val model = IceCandidateModel(it.sdp, it.sdpMid, it.sdpMLineIndex)
                    sendSignalingMessage(SignalingMessage(TYPE_CANDIDATE, candidate = model))
                }
            }

            override fun onDataChannel(channel: DataChannel?) {
                dataChannel = channel
                setupDataChannelCallbacks()
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionState.value = WebRTCState.Disconnected("ICE Connection Lost")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionState.value = WebRTCState.Error("ICE Connection Failed")
                    }
                    else -> {}
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    _connectionState.value = WebRTCState.Connected
                } else if (newState == PeerConnection.PeerConnectionState.FAILED) {
                    _connectionState.value = WebRTCState.Error("Peer Connection Failed")
                }
            }

            override fun onRenegotiationNeeded() {
                if (isHost) {
                    createOffer()
                }
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
        })
    }

    private fun setupDataChannelCallbacks() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            private val receivedBuffer = mutableListOf<Byte>()
            private var currentFileName = ""
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (buffer.binary) {
                    receivedBuffer.addAll(data.toList())
                } else {
                    val msg = String(data)
                    when {
                        msg.startsWith(DC_MSG_NAME) -> {
                            currentFileName = msg.substring(DC_MSG_NAME.length)
                            receivedBuffer.clear()
                        }
                        msg == DC_MSG_END -> {
                            val fileBytes = receivedBuffer.toByteArray()
                            scope.launch { _incomingFiles.emit(IncomingFile(currentFileName, fileBytes)) }
                        }
                        msg == DC_MSG_DISCONNECT -> {
                            closeInternal()
                        }
                    }
                }
            }

            override fun onStateChange() {
                val state = dataChannel?.state()
                if (state == DataChannel.State.OPEN) {
                    _connectionState.value = WebRTCState.Connected
                }
            }

            override fun onBufferedAmountChange(p0: Long) {}
        })
    }

    private fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendSignalingMessage(SignalingMessage(TYPE_OFFER, it.description))
                        }
                    }, it)
                }
            }
        }, constraints)
    }

    private fun setupSignaling() {
        val code = sessionCode ?: return
        val nodeToListen = if (isHost) FB_WEBRTC_HOST else FB_WEBRTC_GUEST
        
        signalingListener = db.child(code).child(nodeToListen)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, p: String?) {
                    val json = s.getValue(String::class.java) ?: return
                    try {
                        val msg = gson.fromJson(json, SignalingMessage::class.java)
                        handleSignalingMessage(msg)
                    } catch (e: Exception) {}
                    s.ref.removeValue() 
                }
                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun handleSignalingMessage(msg: SignalingMessage) {
        when (msg.type) {
            TYPE_OFFER -> {
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        peerConnection?.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(sdp: SessionDescription?) {
                                sdp?.let {
                                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            sendSignalingMessage(SignalingMessage(TYPE_ANSWER, it.description))
                                            drainIceQueue()
                                        }
                                    }, it)
                                }
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, msg.sdp))
            }

            TYPE_ANSWER -> {
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        drainIceQueue()
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, msg.sdp))
            }

            TYPE_CANDIDATE -> {
                msg.candidate?.let {
                    val ice = IceCandidate(it.sdpMid, it.sdpMLineIndex, it.sdp)
                    if (peerConnection?.remoteDescription != null) {
                        peerConnection?.addIceCandidate(ice)
                    } else {
                        pendingIceCandidates.add(ice)
                    }
                }
            }
            TYPE_DISCONNECT -> {
                closeInternal()
            }
        }
    }

    private fun drainIceQueue() {
        pendingIceCandidates.forEach { 
            peerConnection?.addIceCandidate(it) 
        }
        pendingIceCandidates.clear()
    }

    private fun setupDataChannel() {
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel(DC_LABEL, init)
        setupDataChannelCallbacks()
    }

    private fun sendSignalingMessage(msg: SignalingMessage) {
        val code = sessionCode ?: return
        val targetNode = if (isHost) FB_WEBRTC_GUEST else FB_WEBRTC_HOST
        db.child(code).child(targetNode).push().setValue(gson.toJson(msg))
    }

    override fun sendFile(fileName: String, fileBytes: ByteArray): Flow<TransferProgress> = flow {
        val dc = dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            emit(TransferProgress.Error("Connection not ready. Please wait."))
            return@flow
        }
        
        try {
            dc.send(DataChannel.Buffer(ByteBuffer.wrap("$DC_MSG_NAME$fileName".toByteArray()), false))
            
            val chunkSize = 16 * 1024
            var offset = 0
            while (offset < fileBytes.size) {
                if (dc.state() != DataChannel.State.OPEN) throw Exception("DataChannel closed during transfer")
                
                val currentChunk = if (fileBytes.size - offset > chunkSize) chunkSize else fileBytes.size - offset
                dc.send(DataChannel.Buffer(ByteBuffer.wrap(fileBytes, offset, currentChunk), true))
                offset += currentChunk
                
                emit(TransferProgress.Progress((offset * 100L / fileBytes.size).toInt()))
                
                while (dc.bufferedAmount() > 1 * 1024 * 1024) { 
                    delay(10) 
                }
            }
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(DC_MSG_END.toByteArray()), false))
            emit(TransferProgress.Success(fileName))
        } catch (e: Exception) {
            emit(TransferProgress.Error("Send Failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun observeIncomingFiles(): Flow<IncomingFile> = _incomingFiles.asSharedFlow()

    override fun close() {
        synchronized(this) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            try { 
                dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(DC_MSG_DISCONNECT.toByteArray()), false)) 
            } catch (e: Exception) {}
        }
        
        sessionCode?.let { code ->
            signalingListener?.let {
                val node = if (isHost) FB_WEBRTC_HOST else FB_WEBRTC_GUEST
                db.child(code).child(node).removeEventListener(it)
            }
        }
        signalingListener = null
        
        dataChannel?.dispose()
        dataChannel = null
        peerConnection?.dispose()
        peerConnection = null
        sessionCode = null
        _connectionState.value = WebRTCState.Idle
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
