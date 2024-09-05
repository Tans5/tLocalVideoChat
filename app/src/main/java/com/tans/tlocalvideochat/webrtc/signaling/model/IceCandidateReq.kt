package com.tans.tlocalvideochat.webrtc.signaling.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IceCandidateReq(
    @Json(name = "sdpMid")
    val sdpMid: String,
    @Json(name = "sdpMLineIndex")
    val sdpMLineIndex: Int,
    @Json(name = "sdp")
    val sdp: String
)