package com.tans.tlocalvideochat.webrtc.broadcast.receiver

import com.tans.tlocalvideochat.webrtc.broadcast.model.BroadcastMsg
import java.net.InetAddress

data class ScannedDevice(
    val firstUpdateTime: Long,
    val latestUpdateTime: Long,
    val remoteAddress: InetAddress,
    val broadcastMsg: BroadcastMsg
)
