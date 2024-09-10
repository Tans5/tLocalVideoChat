package com.tans.tlocalvideochat.webrtc

import org.webrtc.SessionDescription

sealed class WebRtcState {

    data object NotInit : WebRtcState()

    data class SignalingActive(
        val localAddress: InetAddressWrapper,
        val remoteAddress: InetAddressWrapper,
        val isServer: Boolean
    ) : WebRtcState()

    data class SdpActive(
        val signalingState: SignalingActive,
        val offer: SessionDescription,
        val answer: SessionDescription
    ) : WebRtcState()

    data class RtcConnectionConnected(
        val sdpState: SdpActive
    ) : WebRtcState()

    data class RtcConnectionDisconnected(
        val sdpState: SdpActive
    ) : WebRtcState()

    data class Error(val msg: String) : WebRtcState()

    data object Released : WebRtcState()
}