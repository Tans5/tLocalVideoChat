package com.tans.tlocalvideochat.webrtc.broadcast.sender

import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import kotlinx.coroutines.Job

sealed class BroadcastSenderState {

    data object NoConnection : BroadcastSenderState()

    data object Requesting : BroadcastSenderState()

    data class Active(
        val localAddress: InetAddressWrapper,
        val broadcastAddress: InetAddressWrapper,
        val senderJob: Job
    ) : BroadcastSenderState()

    data class Paused(
        val localAddress: InetAddressWrapper,
        val broadcastAddress: InetAddressWrapper,
        val senderJob: Job
    ) : BroadcastSenderState()

    data object Released : BroadcastSenderState()
}
