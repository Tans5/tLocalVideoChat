package com.tans.tlocalvideochat.webrtc

import android.os.Build

object Const {
    val DEVICE_NAME = "${Build.BRAND}-${Build.DEVICE}"

    const val VERSION = 20240904

    // UDP
    const val BROADCAST_SENDER_PORT = 19960

    // UDP
    const val WAITING_CONNECT_SERVER_PORT = 19961

    // TCP
    const val SIGNALING_SERVER_PORT = 19962
}
