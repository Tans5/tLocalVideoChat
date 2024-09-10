package com.tans.tlocalvideochat.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.webrtc.signaling.Signaling
import com.tans.tlocalvideochat.webrtc.signaling.model.IceCandidateReq
import com.tans.tlocalvideochat.webrtc.signaling.model.SdpReq
import com.tans.tlocalvideochat.webrtc.signaling.model.SdpResp
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 1. PeerConnection init local audio track and video track.
 * 2. PeerConnection create local sdp (Session Description).
 * 2. Exchange sdp (Session Description) via Signaling Server (Here use socket.).
 * 3. PeerConnection update local sdp and remote sdp.
 * 4. Receive local ICE (Interactive Connectivity Establishment) Candidate.
 * 5. Exchange ICE (Interactive Connectivity Establishment) Candidate via Signaling Server (Here use socket.).
 * 6. PeerConnection add remote ICE Candidate.
 * 7. Receive remote audio track and video track.
 * 8. Render local tracks and remote tracks.
 */
class WebRtc(
    private val context: Context
): CoroutineState<WebRtcState> by CoroutineState(WebRtcState.NotInit), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    // region WebRtc
    val eglBaseContext: EglBase.Context by lazy {
        EglBase.create().eglBaseContext
    }

    private val localSdp: AtomicReference<SessionDescription?> by lazy {
        AtomicReference()
    }

    // rtcConfig contains STUN and TURN servers list
    private val rtcConfig by lazy {
        RTCConfiguration(
            listOf(
                // adding google's standard server
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    // declaring video constraints and setting OfferToReceiveVideo to true
    // this step is mandatory to create valid offer and answer
    private val mediaConstraints by lazy {
        MediaConstraints().apply {
            mandatory.addAll(
                listOf(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"),
                    MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            )
        }
    }

    private val remoteVideoTrack by lazy {
        MutableSharedFlow<VideoTrack>(replay = 1)
    }

    private val videoDecoderFactory by lazy {
        DefaultVideoDecoderFactory(eglBaseContext)
    }

    private val videoEncoderFactory by lazy {
        val hardwareEncoder = HardwareVideoEncoderFactory(eglBaseContext, true, true)
        SimulcastVideoEncoderFactory(hardwareEncoder, SoftwareVideoEncoderFactory())
    }

    private val peerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setInjectableLogger({ message, severity, label ->
                    // AppLog.d(tag = "PeerConnectionFactory", msg = "[$severity] [$label]: $message")
                }, Logging.Severity.LS_VERBOSE)
                .createInitializationOptions()
        )

        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setAudioDeviceModule(
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    .setUseHardwareNoiseSuppressor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                        override fun onWebRtcAudioRecordInitError(p0: String?) {
                            AppLog.e(TAG, "onWebRtcAudioRecordInitError: $p0")
                        }

                        override fun onWebRtcAudioRecordStartError(
                            p0: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                            p1: String?
                        ) {
                            AppLog.e(TAG, "onWebRtcAudioRecordStartError: ErrorCode=$p0, Msg=$p1")
                        }

                        override fun onWebRtcAudioRecordError(p0: String?) {
                            AppLog.e(TAG, "onWebRtcAudioRecordError: $p0")
                        }
                    })
                    .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                        override fun onWebRtcAudioTrackInitError(p0: String?) {
                            AppLog.e(TAG, "onWebRtcAudioTrackInitError: $p0")
                        }

                        override fun onWebRtcAudioTrackStartError(
                            p0: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                            p1: String?
                        ) {
                            AppLog.e(TAG, "onWebRtcAudioTrackStartError: ErrorCode=$p0, Msg=$p1")
                        }

                        override fun onWebRtcAudioTrackError(p0: String?) {
                            AppLog.e(TAG, "onWebRtcAudioTrackError: $p0")
                        }

                    })
                    .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                        override fun onWebRtcAudioRecordStart() {
                            AppLog.d(TAG, "onWebRtcAudioRecordStart")
                        }

                        override fun onWebRtcAudioRecordStop() {
                            AppLog.d(TAG, "onWebRtcAudioRecordStop")
                        }
                    })
                    .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                        override fun onWebRtcAudioTrackStart() {
                            AppLog.d(TAG, "onWebRtcAudioTrackStart")
                        }

                        override fun onWebRtcAudioTrackStop() {
                            AppLog.d(TAG, "onWebRtcAudioTrackStop")
                        }
                    })
                    .createAudioDeviceModule().apply {
                        setMicrophoneMute(false)
                        setSpeakerMute(false)
                    }
            )
            .createPeerConnectionFactory()
    }

    private val peerConnectionObserver by lazy {
        object : SimplifyPeerConnectionObserver {

            override fun onIceCandidate(ice: IceCandidate?) {
                super.onIceCandidate(ice)
                AppLog.d(TAG, "Local ice candidate update: $ice")
                if (ice != null) {
                    this@WebRtc.launch {
                        lock.withLock {
                            val s = currentState()
                            if (s is WebRtcState.SdpActive ||
                                s is WebRtcState.RtcConnectionConnected) {
                                runCatching {
                                    signaling.sendIceCandidate(IceCandidateReq(sdpMid = ice.sdpMid, sdpMLineIndex = ice.sdpMLineIndex, sdp = ice.sdp))
                                }.onSuccess {
                                    AppLog.d(TAG, "Send ice candidate success.")
                                }.onFailure {
                                    val msg = "Send ice candidate fail: ${it.message}"
                                    AppLog.e(TAG, msg, it)
                                    updateState { WebRtcState.Error(msg) }
                                }
                            } else {
                                val msg = "Wrong state $s, to send ice candidate."
                                updateState { WebRtcState.Error(msg) }
                            }
                        }
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                val track = transceiver?.receiver?.track()
                if (track != null) {
                    if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                        AppLog.d(TAG, "Remote video track update.")
                        track as VideoTrack
                        launch {
                            remoteVideoTrack.emit(track)
                        }
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) {
                    AppLog.d(TAG, "Connection state update: $newState")
                    launch {
                        lock.withLock {
                            val s = currentState()
                            when (newState) {
                                PeerConnection.PeerConnectionState.CONNECTED -> {
                                    if (s is WebRtcState.SdpActive) {
                                        updateState { WebRtcState.RtcConnectionConnected(s) }
                                    }
                                    if (s is WebRtcState.RtcConnectionDisconnected) {
                                        updateState { WebRtcState.RtcConnectionConnected(s.sdpState) }
                                    }
                                }
                                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                                    if (s is WebRtcState.RtcConnectionConnected) {
                                        updateState { WebRtcState.RtcConnectionDisconnected(s.sdpState) }
                                    }
                                    if (s is WebRtcState.SdpActive) {
                                        updateState { WebRtcState.RtcConnectionDisconnected(s) }
                                    }
                                }
                                PeerConnection.PeerConnectionState.FAILED -> {
                                    val msg = "Create web rtc connection fail."
                                    updateState { WebRtcState.Error(msg) }
                                    AppLog.e(TAG, msg)
                                }
                                else -> {

                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private val peerConnection by lazy {
       peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)!!
    }

    // region LocalVideo
    private val cameraManager by lazy {
        context.getSystemService<CameraManager>()!!
    }

    private val videoCapture by lazy {
        val ids = cameraManager.cameraIdList
        var targetIds: String? = null
        for (id in ids) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                targetIds = id
            }
        }
        Camera2Capturer(context, targetIds ?: ids.firstOrNull(), null)
    }

    private val cameraEnumerator by lazy {
        Camera2Enumerator(context)
    }

    private val cameraCaptureFormat: CameraEnumerationAndroid.CaptureFormat
        get() {
            val frontCamera = cameraEnumerator.deviceNames.first { cameraName ->
                cameraEnumerator.isFrontFacing(cameraName)
            }
            val supportedFormats = cameraEnumerator.getSupportedFormats(frontCamera) ?: emptyList()
            return supportedFormats.firstOrNull {
                (it.width == 720 || it.width == 480 || it.width == 360)
            } ?: error("There is no matched resolution!")
        }

    private val surfaceTextureHelper = SurfaceTextureHelper.create(
        "SurfaceTextureHelperThread",
        eglBaseContext
    )

    private val videoSource by lazy {
        val source = peerConnectionFactory.createVideoSource(videoCapture.isScreencast)
        videoCapture.initialize(surfaceTextureHelper, context, source.capturerObserver)
        videoCapture.startCapture(cameraCaptureFormat.width, cameraCaptureFormat.height, 24)
        source
    }

    val localVideoTrack by lazy {
        peerConnectionFactory.createVideoTrack("Video${System.currentTimeMillis()}", videoSource)!!
    }
    // endregion

    // region LocalAudio

    private val audioManager by lazy {
        context.getSystemService<AudioManager>()!!
    }

    private val audioConstraints by lazy {
        MediaConstraints().apply {
            with(optional) {
                addAll(
                    listOf(
                        MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"),
                        MediaConstraints.KeyValuePair("googEchoCancellation", "true"),
                        MediaConstraints.KeyValuePair("googAutoGainControl", "true"),
                        MediaConstraints.KeyValuePair("googHighpassFilter", "true"),
                        MediaConstraints.KeyValuePair("googNoiseSuppression", "true"),
                        MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true")
                    )
                )
            }
        }
    }

    private val audioSource by lazy {
        peerConnectionFactory.createAudioSource(audioConstraints)
    }

    private val localAudioTrack by lazy {
        peerConnectionFactory.createAudioTrack("Audio${System.currentTimeMillis()}", audioSource)
    }
    // endregion

    // endregion

    private val signaling: Signaling by lazy {
        Signaling(
            exchangeSdp = { offer: SdpReq, isNew: Boolean ->
                if (isNew) {
                    val state = currentState()
                    if (state is WebRtcState.SignalingActive) {
                        launch {
                            lock.withLock {
                                val s = currentState()
                                if (s is WebRtcState.SignalingActive) {
                                    val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, offer.description)
                                    runCatching {
                                        this@WebRtc.setSdpSuspend(remoteSdp, true)
                                        val sdp = this@WebRtc.createSdpSuspend(false)
                                        this@WebRtc.setSdpSuspend(sdp, false)
                                        this@WebRtc.localSdp.set(sdp)
                                        sdp
                                    }.onSuccess { sdp ->
                                        updateState { WebRtcState.SdpActive(signalingState = state, offer = remoteSdp, answer = sdp) }
                                        AppLog.d(TAG, "Update remote sdp success: offer=${remoteSdp.description}, answer=${sdp.description}")
                                    }.onFailure {
                                        val msg = "Update remote sdp fail: ${it.message}"
                                        updateState { WebRtcState.Error(msg) }
                                        AppLog.e(TAG, msg)
                                    }
                                } else {
                                    val msg = "Error state: $s, need SignalingActive state."
                                    updateState { WebRtcState.Error(msg) }
                                    AppLog.e(TAG, msg)
                                }
                            }
                        }
                        localSdp.get()?.let {
                            SdpResp(it.description)
                        }
                    } else {
                        val msg = "Wrong state: $state, need SignalingActive."
                        updateState { WebRtcState.Error(msg) }
                        AppLog.e(TAG, msg)
                        null
                    }
                } else {
                    localSdp.get()?.let {
                        SdpResp(it.description)
                    }
                }
            },
            remoteIceCandidate = { remoteIceCandidate: IceCandidateReq, isNew: Boolean ->
                if (isNew) {
                    launch {
                        lock.withLock {
                            val state = currentState()
                            if (state !is WebRtcState.SdpActive &&
                                state !is WebRtcState.RtcConnectionConnected) {
                                val msg = "Wrong state: $state to handle remote ice candidate."
                                updateState { WebRtcState.Error(msg) }
                                AppLog.e(TAG, msg)
                                return@withLock
                            }
                            val iceCandidate = IceCandidate(remoteIceCandidate.sdpMid, remoteIceCandidate.sdpMLineIndex, remoteIceCandidate.sdp)
                            val setIceResult = peerConnection.addIceCandidate(iceCandidate)
                            AppLog.d(TAG, "Set remote ice candidate result: $setIceResult")
                        }
                    }
                }
                Unit
            }
        )
    }

    private val lock: Mutex by lazy {
        Mutex()
    }

    suspend fun createRtcConnection(
        localAddress: InetAddressWrapper,
        remoteAddress: InetAddressWrapper,
        isServer: Boolean) {
        lock.withLock {
            if (currentState() != WebRtcState.NotInit) {
                error("WebRtc already started")
            }
            var signalingRetryTimes = 0
            do {
                val result = runCatching { signaling.start(localAddress, remoteAddress, isServer) }
                if (result.isSuccess) {
                    break
                } else {
                    delay(200)
                    signalingRetryTimes ++
                }
            } while (signalingRetryTimes < 3)
            if (signalingRetryTimes >= 3) {
                val msg = "Signaling connect error."
                updateState { WebRtcState.Error(msg) }
                AppLog.e(TAG, msg)
                error(msg)
            }
            updateState { WebRtcState.SignalingActive(localAddress = localAddress, remoteAddress = remoteAddress, isServer = isServer) }

            setupAudio()
            peerConnection.addTrack(localVideoTrack)
            peerConnection.addTrack(localAudioTrack)

            if (!isServer) {
                val localSdp = runCatching {
                    val sdp = createSdpSuspend(true)
                    setSdpSuspend(sdp, false)
                    this@WebRtc.localSdp.set(sdp)
                    sdp
                }.getOrNull()
                if (localSdp == null) {
                    val msg = "Create local sdp fail."
                    updateState { WebRtcState.Error(msg) }
                    error(msg)
                }
                AppLog.d(TAG, "Create local sdp success: ${localSdp.description}")
                val remoteSdp = runCatching {
                    val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, signaling.requestExchangeSdp(SdpReq(localSdp.description)).description)
                    setSdpSuspend(remoteSdp, true)
                    remoteSdp
                }.getOrNull()
                if (remoteSdp == null) {
                    val msg = "Exchange sdp fail."
                    updateState { WebRtcState.Error(msg) }
                    error(msg)
                }
                AppLog.d(TAG, "Exchange sdp success, remoteSdp: ${remoteSdp.description}")
                val lastState = currentState()
                if (lastState !is WebRtcState.SignalingActive) {
                    val msg = "Wrong state: $lastState, need SignalingActive state."
                    updateState { WebRtcState.Error(msg) }
                    error(msg)
                }
                updateState { WebRtcState.SdpActive(signalingState = lastState, offer = localSdp, answer = remoteSdp) }
            } else {
                // Server waiting client request sdp
                AppLog.d(TAG, "Server waiting client request sdp.")
            }
        }
    }

    fun switchCamera() {
        videoCapture.switchCamera(null)
    }

    fun enableCamera(enable: Boolean) {
        if (enable) {
            videoCapture.startCapture(cameraCaptureFormat.width, cameraCaptureFormat.height, 24)
        } else {
            videoCapture.stopCapture()
        }
    }

    fun enableMicrophone(enable: Boolean) {
        audioManager.isMicrophoneMute = !enable
    }

    fun observeRemoteVideoTrack(): Flow<VideoTrack> = remoteVideoTrack

    fun release() {
        launch {
            lock.withLock {
                localVideoTrack.dispose()
                videoCapture.stopCapture()
                videoCapture.dispose()
                localAudioTrack.dispose()
                peerConnection.dispose()
                signaling.release()
                updateState { WebRtcState.Released }
                cancel()
            }
        }
    }

    private suspend fun createSdpSuspend(isOffer: Boolean): SessionDescription {
        return suspendCancellableCoroutine { cont ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (cont.isActive) {
                        if (sdp != null) {
                            cont.resume(sdp)
                        } else {
                            cont.resumeWithException(IOException("Create answer fail."))
                        }
                    }
                }

                override fun onCreateFailure(msg: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(IOException(msg ?: ""))
                    }
                }

                override fun onSetSuccess() {}

                override fun onSetFailure(p0: String?) {}
            }
            if (isOffer) {
                peerConnection.createOffer(observer, mediaConstraints)
            } else {
                peerConnection.createAnswer(observer, mediaConstraints)
            }
        }
    }

    private fun setupAudio() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(playbackAttributes)
            .setOnAudioFocusChangeListener {  }
            .setAcceptsDelayedFocusGain(true)
            .build()
        audioManager.requestAudioFocus(audioFocusRequest)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allDevices = audioManager.availableCommunicationDevices
            val selectedDevice = allDevices.find { it.type == TYPE_BUILTIN_SPEAKER } ?: allDevices.firstOrNull()
            if (selectedDevice != null) {
                audioManager.setCommunicationDevice(selectedDevice)
            }
        } else {
            audioManager.isSpeakerphoneOn = true
        }
    }

    private suspend fun setSdpSuspend(dsp: SessionDescription, isRemote: Boolean) {
        return suspendCancellableCoroutine { cont ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(msg: String?) {}
                override fun onSetSuccess() {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                override fun onSetFailure(p0: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(IOException(p0 ?: ""))
                    }
                }
            }
            if (isRemote) {
                peerConnection.setRemoteDescription(observer, dsp)
            } else {
                peerConnection.setLocalDescription(observer, dsp)
            }
        }
    }


    companion object {
        private const val TAG = "WebRtc"
    }
}