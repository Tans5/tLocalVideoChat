package com.tans.tlocalvideochat.webrtc

import android.content.Context
import android.os.Build
import com.tans.tlocalvideochat.AppLog
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.Inet4Address

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
    private val context: Context,
    private val localAddress: Inet4Address?,
    private val remoteAddress: Inet4Address?
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
                            AppLog.e("onWebRtcAudioRecordInitError", p0 ?: "")
                        }

                        override fun onWebRtcAudioRecordStartError(
                            p0: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                            p1: String?
                        ) {
                            AppLog.e("onWebRtcAudioRecordStartError", "Error code: $p0, $p1")
                        }

                        override fun onWebRtcAudioRecordError(p0: String?) {
                            AppLog.e("onWebRtcAudioRecordError", p0 ?: "")
                        }
                    })
                    .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                        override fun onWebRtcAudioTrackInitError(p0: String?) {
                            AppLog.e("onWebRtcAudioTrackInitError", p0 ?: "")
                        }

                        override fun onWebRtcAudioTrackStartError(
                            p0: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                            p1: String?
                        ) {
                            AppLog.e("onWebRtcAudioTrackStartError", "Error code: $p0, $p1")
                        }

                        override fun onWebRtcAudioTrackError(p0: String?) {
                            AppLog.e("onWebRtcAudioTrackError", p0 ?: "")
                        }

                    })
                    .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                        override fun onWebRtcAudioRecordStart() {
                            AppLog.d("onWebRtcAudioRecordStart", "Audio record started.")
                        }

                        override fun onWebRtcAudioRecordStop() {
                            AppLog.d("onWebRtcAudioRecordStop", "Audio record stopped.")
                        }
                    })
                    .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                        override fun onWebRtcAudioTrackStart() {
                            AppLog.d("onWebRtcAudioTrackStart", "Audio track stared.")
                        }

                        override fun onWebRtcAudioTrackStop() {
                            AppLog.d("onWebRtcAudioTrackStop", "Audio track stopped.")
                        }
                    })
                    .createAudioDeviceModule().apply {
                        setMicrophoneMute(false)
                        setSpeakerMute(false)
                    }
            )
            .createPeerConnectionFactory()
    }

    // TODO:

    // endregion

}