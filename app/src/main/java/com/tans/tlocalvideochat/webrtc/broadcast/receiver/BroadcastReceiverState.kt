package com.tans.tlocalvideochat.webrtc.broadcast.receiver

import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import kotlinx.coroutines.Job
import java.net.InetAddress


sealed class BroadcastReceiverState {
    data object NoConnection : BroadcastReceiverState()

    data object Requesting : BroadcastReceiverState()

    data class Active(
        val localAddress: InetAddressWrapper,
        val broadcastAddress: InetAddressWrapper,
        val receiverJob: Job
    ) : BroadcastReceiverState()

    data object Released : BroadcastReceiverState()
}