package com.tans.tlocalvideochat.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import com.tans.tlocalvideochat.AppLog
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.audio.JavaAudioDeviceModule

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
): CoroutineState<Unit> by CoroutineState(Unit), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    // region WebRtc
    val eglBaseContext: EglBase.Context by lazy {
        EglBase.create().eglBaseContext
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
                listOf(
                    MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"),
                    MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
                )
            )
        }
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
                    AppLog.d(tag = "PeerConnectionFactory", msg = "[$severity] [$label]: $message")
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
                            AppLog.e(TAG, "onWebRtcAudioRecordInitError: p0")
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
                // TODO: Local ICE Candidate update.
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                val track = transceiver?.receiver?.track()
                if (track != null) {
                    if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                        // TODO: Remote video track update.
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

    private val localVideoTrack by lazy {
        peerConnectionFactory.createVideoTrack("Video${System.currentTimeMillis()}", videoSource)
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

    fun start() {
        // TODO:
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

    fun stop() {
        // TODO:
        localVideoTrack.dispose()
        videoCapture.stopCapture()
        videoCapture.dispose()

        localAudioTrack.dispose()

        peerConnection.dispose()
        cancel()
    }


    companion object {
        private const val TAG = "WebRtc"
    }
}