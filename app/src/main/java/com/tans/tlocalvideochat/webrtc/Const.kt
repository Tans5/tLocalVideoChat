package com.tans.tlocalvideochat.webrtc

import android.os.Build

object Const {
    val DEVICE_NAME = "${Build.BRAND}-${Build.MODEL}"

    const val VERSION = 20240904

    // UDP
    const val BROADCAST_SENDER_PORT = 19960

    // UDP
    const val WAITING_CONNECT_SERVER_PORT = 19961

    // UDP
    const val REQUEST_CONNECT_CLIENT_PORT = 19962

    // TCP
    const val SIGNALING_SERVER_PORT = 19963
}
