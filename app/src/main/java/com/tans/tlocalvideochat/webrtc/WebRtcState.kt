package com.tans.tlocalvideochat.webrtc

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

sealed class WebRtcState {

    data object NotInit : WebRtcState()

    data class SignalingActive(
        val localAddress: InetAddressWrapper,
        val remoteAddress: InetAddressWrapper,
        val isServer: Boolean
    ) : WebRtcState()

    data class SdpActive(
        val lastState: SignalingActive,
        val offer: SessionDescription,
        val answer: SessionDescription
    ) : WebRtcState()

    data class IceCandidateActive(
        val lastState: SdpActive,
        val remoteIceCandidate: IceCandidate
    ) : WebRtcState()

    data class Error(val msg: String) : WebRtcState()

    data object Released : WebRtcState()
}