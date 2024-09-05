package com.tans.tlocalvideochat.webrtc.broadcast.sender

import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.broadcast.model.RequestConnectReq

data class ConnectRequest(
    val request: RequestConnectReq,
    val remoteAddress: InetAddressWrapper
)