package com.tans.tlocalvideochat.net

interface ILog {

    fun d(tag: String, msg: String)

    fun e(tag: String, msg: String, throwable: Throwable? = null)
}