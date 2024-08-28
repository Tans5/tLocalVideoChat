package com.tans.tlocalvideochat

import android.util.Log
import com.tans.tlocalvideochat.net.ILog

object AppLog : ILog {

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        Log.e(tag, msg, throwable)
    }
}