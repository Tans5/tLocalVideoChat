package com.tans.tlocalvideochat.webrtc.broadcast.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BroadcastMsg(
    @Json(name = "device_name")
    val deviceName: String,
    @Json(name = "version")
    val version: Int
)
