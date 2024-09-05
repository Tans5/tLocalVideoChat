package com.tans.tlocalvideochat.webrtc.signaling.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sdp answer
 */
@JsonClass(generateAdapter = true)
data class SdpResp(
    @Json(name = "description")
    val description: String
)