package com.tans.tlocalvideochat.net.netty.extensions

import com.tans.tlocalvideochat.net.netty.PackageData

interface IPackageDataConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData?
}