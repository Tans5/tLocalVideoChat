package com.tans.tlocalvideochat.webrtc.broadcast.sender

import kotlinx.coroutines.Job
import java.net.InetAddress

sealed class BroadcastSenderState {

    data object NoConnection : BroadcastSenderState()

    data object Requesting : BroadcastSenderState()

    data class Active(
        val localAddress: InetAddress,
        val broadcastAddress: InetAddress,
        val senderJob: Job
    ) : BroadcastSenderState()

    data class Paused(
        val localAddress: InetAddress,
        val broadcastAddress: InetAddress,
        val senderJob: Job
    ) : BroadcastSenderState()

    data object Released : BroadcastSenderState()
}
