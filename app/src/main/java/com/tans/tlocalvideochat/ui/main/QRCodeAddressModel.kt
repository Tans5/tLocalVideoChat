package com.tans.tlocalvideochat.ui.main

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QRCodeAddressModel(
    @Json(name = "address")
    val address: Int,
    @Json(name = "version")
    val version: Int,
    @Json(name = "device_name")
    val deviceName: String
)