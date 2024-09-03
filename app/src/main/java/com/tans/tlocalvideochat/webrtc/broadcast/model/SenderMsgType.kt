package com.tans.tlocalvideochat.webrtc.broadcast.model

enum class SenderMsgType(val type: Int) {
    BroadcastMsg(0),
    ConnectReq(1),
    ConnectResp(2)
}