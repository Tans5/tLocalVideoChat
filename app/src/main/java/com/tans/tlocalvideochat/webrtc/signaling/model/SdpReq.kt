package com.tans.tlocalvideochat.webrtc.signaling.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sdp offer
 */
@JsonClass(generateAdapter = true)
data class SdpReq(
    @Json(name = "description")
    val description: String
)