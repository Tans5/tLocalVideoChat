package com.tans.tlocalvideochat.webrtc.broadcast.sender.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RequestConnectResp(
    @Json(name = "device_name")
    val deviceName: String
)