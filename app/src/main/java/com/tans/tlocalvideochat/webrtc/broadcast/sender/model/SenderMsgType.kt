package com.tans.tlocalvideochat.webrtc.broadcast.sender.model

enum class SenderMsgType(val type: Int) {
    BroadcastMsg(0),
    ConnectReq(1),
    ConnectResp(2)
}