package com.tans.tlocalvideochat.webrtc.broadcast.receiver

import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.broadcast.model.BroadcastMsg

data class ScannedDevice(
    val firstUpdateTime: Long,
    val latestUpdateTime: Long,
    val remoteAddress: InetAddressWrapper,
    val broadcastMsg: BroadcastMsg
)
