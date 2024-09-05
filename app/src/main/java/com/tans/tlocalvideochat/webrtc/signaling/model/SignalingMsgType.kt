package com.tans.tlocalvideochat.webrtc.signaling.model

enum class SignalingMsgType(val type: Int) {
    SdpReq(0),
    SdpResp(1),
    IceReq(2),
    IceResp(3)
}