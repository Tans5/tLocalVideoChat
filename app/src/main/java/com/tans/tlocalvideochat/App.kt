package com.tans.tlocalvideochat

import android.app.Application
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AutoApplySystemBarAnnotation.init(this)
    }
}