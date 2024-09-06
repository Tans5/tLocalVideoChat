package com.tans.tlocalvideochat.webrtc.signaling

import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import kotlinx.coroutines.Job

sealed class SignalingState {

    data object NoConnection : SignalingState()

    data object Requesting : SignalingState()

    data class Active(
        val localAddress: InetAddressWrapper,
        val remoteAddress: InetAddressWrapper,
        val isServer: Boolean,
        val signalingJob: Job
    ) : SignalingState()

    data object Released : SignalingState()
}