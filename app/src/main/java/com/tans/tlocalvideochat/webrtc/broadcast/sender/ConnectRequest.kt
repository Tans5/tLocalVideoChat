package com.tans.tlocalvideochat.webrtc.broadcast.sender

import com.tans.tlocalvideochat.webrtc.broadcast.sender.model.RequestConnectReq
import java.net.InetAddress

data class ConnectRequest(
    val request: RequestConnectReq,
    val remoteAddress: InetAddress
)