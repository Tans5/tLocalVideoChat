package com.tans.tlocalvideochat.ui

import android.Manifest
import android.view.View
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ActivityMainBinding
import com.tans.tlocalvideochat.webrtc.WebRtc
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSimplifySuspend
import com.tans.tuiutils.systembar.annotation.ContentViewFitSystemWindow
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SystemBarStyle
@ContentViewFitSystemWindow
class MainActivity : BaseCoroutineStateActivity<Unit>(Unit) {

    override val layoutId: Int = R.layout.activity_main

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        // TODO:
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        launch {
            val viewBinding = ActivityMainBinding.bind(contentView)
            val grant = runCatching {
                permissionsRequestSimplifySuspend(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            }.getOrNull() ?: false
            if (grant) {
                // TODO: test code.
                val webRtc = WebRtc(this@MainActivity.applicationContext, null, null)
            } else {
                finish()
            }
        }
    }
}