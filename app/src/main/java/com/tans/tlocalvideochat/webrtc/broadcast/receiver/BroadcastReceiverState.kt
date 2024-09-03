package com.tans.tlocalvideochat.webrtc.broadcast.receiver

import kotlinx.coroutines.Job
import java.net.InetAddress


sealed class BroadcastReceiverState {
    data object NoConnection : BroadcastReceiverState()

    data object Requesting : BroadcastReceiverState()

    data class Active(
        val localAddress: InetAddress,
        val broadcastAddress: InetAddress,
        val receiverJob: Job
    ) : BroadcastReceiverState()

    data object Released : BroadcastReceiverState()
}