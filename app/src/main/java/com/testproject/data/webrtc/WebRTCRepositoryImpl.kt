package com.testproject.data.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.testproject.domain.webrtc.*
import com.testproject.utils.AppsConst.FB_SESSIONS
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

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 1)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var signalingListener: ChildEventListener? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private val TAG = "WebRTC_Repo"

    override fun initialize(sessionCode: String, isHost: Boolean) {
        if (this.sessionCode == sessionCode && this.isHost == isHost) {
            Log.d(TAG, "Already initialized with code: $sessionCode")
            return
        }

        Log.d(TAG, "Initializing WebRTC: code=$sessionCode, isHost=$isHost")
        close() // Ensure fresh start
        this.sessionCode = sessionCode
        this.isHost = isHost

        initializeWebRTC()
        setupPeerConnection()
        if (isHost) {
            setupDataChannel()
        }
        setupSignaling()
    }

    private fun initializeWebRTC() {
        try {
            if (factory == null) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
                )
                val options = PeerConnectionFactory.Options()
                factory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .createPeerConnectionFactory()
                Log.d(TAG, "PeerConnectionFactory initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init error", e)
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

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "onIceCandidate: ${it.sdpMid}")
                    val model = IceCandidateModel(it.sdp, it.sdpMid, it.sdpMLineIndex)
                    sendSignalingMessage(SignalingMessage("candidate", candidate = model))
                }
            }

            override fun onDataChannel(channel: DataChannel?) {
                Log.d(TAG, "onDataChannel received")
                dataChannel = channel
                setupDataChannelCallbacks()
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $newState")
                if (newState == PeerConnection.IceConnectionState.DISCONNECTED || 
                    newState == PeerConnection.IceConnectionState.FAILED ||
                    newState == PeerConnection.IceConnectionState.CLOSED) {
                    // Logic to handle reconnection or closure
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "onConnectionChange: $newState")
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $newState")
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $newState")
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
                if (isHost) createOffer()
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
        })
    }

    private fun setupDataChannelCallbacks() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            val receivedBuffer = mutableListOf<Byte>()
            var currentFileName = ""
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (buffer.binary) {
                    receivedBuffer.addAll(data.toList())
                } else {
                    val msg = String(data)
                    Log.d(TAG, "DataChannel Message: $msg")
                    if (msg.startsWith("NAME:")) {
                        currentFileName = msg.substring(5)
                        receivedBuffer.clear()
                        Log.d(TAG, "Receiving file: $currentFileName")
                    } else if (msg == "END") {
                        Log.d(TAG, "File reception complete: $currentFileName")
                        scope.launch {
                            _incomingFiles.emit(IncomingFile(currentFileName, receivedBuffer.toByteArray()))
                        }
                    }
                }
            }

            override fun onStateChange() {
                val state = dataChannel?.state()
                Log.d(TAG, "DataChannel State Change: $state")
            }

            override fun onBufferedAmountChange(p0: Long) {}
        })
    }

    private fun createOffer() {
        Log.d(TAG, "Creating Offer")
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "Offer Created, setting local description")
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set, sending offer")
                            sendSignalingMessage(SignalingMessage("offer", it.description))
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                    }, it)
                }
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
        }, MediaConstraints())
    }

    private fun setupSignaling() {
        val remoteNode = if (isHost) "guestSignaling" else "hostSignaling"
        Log.d(TAG, "Setting up signaling listener on node: $remoteNode")
        signalingListener = db.child(sessionCode!!).child(remoteNode)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, p: String?) {
                    val json = s.getValue(String::class.java) ?: return
                    val msg = gson.fromJson(json, SignalingMessage::class.java)
                    Log.d(TAG, "Received Signaling Message: ${msg.type}")
                    handleSignalingMessage(msg)
                    s.ref.removeValue()
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {
                    Log.e(TAG, "Signaling cancelled: ${p0.message}")
                }
            })
    }

    private fun handleSignalingMessage(msg: SignalingMessage) {
        when (msg.type) {
            "offer" -> {
                Log.d(TAG, "Handling Offer")
                peerConnection?.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set (Offer), creating answer")
                            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                                override fun onCreateSuccess(sdp: SessionDescription?) {
                                    sdp?.let {
                                        peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                            override fun onSetSuccess() {
                                                Log.d(TAG, "Local description set (Answer), sending answer")
                                                sendSignalingMessage(SignalingMessage("answer", it.description))
                                                drainIceQueue()
                                            }
                                        }, it)
                                    }
                                }
                            }, MediaConstraints())
                        }
                    },
                    SessionDescription(SessionDescription.Type.OFFER, msg.sdp)
                )
            }

            "answer" -> {
                Log.d(TAG, "Handling Answer")
                peerConnection?.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set (Answer)")
                            drainIceQueue()
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, msg.sdp)
                )
            }

            "candidate" -> {
                msg.candidate?.let {
                    Log.d(TAG, "Handling ICE Candidate")
                    val ice = IceCandidate(it.sdpMid, it.sdpMLineIndex, it.sdp)
                    if (peerConnection?.remoteDescription != null) {
                        peerConnection?.addIceCandidate(ice)
                    } else {
                        Log.d(TAG, "Queueing ICE Candidate")
                        pendingIceCandidates.add(ice)
                    }
                }
            }
        }
    }

    private fun drainIceQueue() {
        Log.d(TAG, "Draining ICE Candidate Queue: ${pendingIceCandidates.size} candidates")
        pendingIceCandidates.forEach { 
            val result = peerConnection?.addIceCandidate(it)
            Log.d(TAG, "Added queued ICE candidate: $result")
        }
        pendingIceCandidates.clear()
    }

    private fun setupDataChannel() {
        Log.d(TAG, "Setting up Data Channel as Host")
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("fileTransfer", init)
        setupDataChannelCallbacks()
    }

    private fun sendSignalingMessage(msg: SignalingMessage) {
        val targetNode = if (isHost) "hostSignaling" else "guestSignaling"
        sessionCode?.let { code ->
            Log.d(TAG, "Sending Signaling Message: ${msg.type} to $targetNode")
            db.child(code).child(targetNode).push()
                .setValue(gson.toJson(msg))
        }
    }

    override fun sendFile(fileName: String, fileBytes: ByteArray): Flow<TransferProgress> = flow {
        val currentState = dataChannel?.state()
        Log.d(TAG, "Attempting to send file: $fileName, State: $currentState")
        
        if (currentState != DataChannel.State.OPEN) {
            emit(TransferProgress.Error("WebRTC Data Channel not open (Current state: $currentState)"))
            return@flow
        }
        
        dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap("NAME:$fileName".toByteArray()), false))
        
        val chunkSize = 16 * 1024
        var offset = 0
        while (offset < fileBytes.size) {
            val remaining = fileBytes.size - offset
            val current = if (remaining > chunkSize) chunkSize else remaining
            val buffer = ByteBuffer.wrap(fileBytes, offset, current)
            dataChannel?.send(DataChannel.Buffer(buffer, true))
            offset += current
            
            emit(TransferProgress.Progress((offset * 100L / fileBytes.size).toInt()))
            
            // Flow control to avoid buffer overflow
            while ((dataChannel?.bufferedAmount() ?: 0) > 1024 * 1024) {
                delay(10)
            }
        }
        dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap("END".toByteArray()), false))
        Log.d(TAG, "File sent successfully: $fileName")
        emit(TransferProgress.Success(fileName))
    }.flowOn(Dispatchers.IO)

    override fun observeIncomingFiles(): Flow<IncomingFile> = _incomingFiles.asSharedFlow()

    override fun close() {
        Log.d(TAG, "Closing WebRTC Connection")
        sessionCode?.let { code ->
            signalingListener?.let {
                val remoteNode = if (isHost) "guestSignaling" else "hostSignaling"
                db.child(code).child(remoteNode).removeEventListener(it)
            }
        }
        signalingListener = null
        dataChannel?.close()
        dataChannel = null
        peerConnection?.dispose()
        peerConnection = null
        sessionCode = null
        pendingIceCandidates.clear()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {
            Log.e("WebRTC_SDP", "onCreateFailure: $p0")
        }
        override fun onSetFailure(p0: String?) {
            Log.e("WebRTC_SDP", "onSetFailure: $p0")
        }
    }
}
