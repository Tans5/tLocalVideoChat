package com.tans.tlocalvideochat.net.netty

import java.net.InetSocketAddress

data class PackageDataWithAddress(
    val receiverAddress: InetSocketAddress?,
    val senderAddress: InetSocketAddress? = null,
    val data: PackageData
)