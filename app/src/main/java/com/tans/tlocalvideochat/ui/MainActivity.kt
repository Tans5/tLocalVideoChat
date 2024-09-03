package com.tans.tlocalvideochat.ui

import android.Manifest
import android.view.View
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ActivityMainBinding
import com.tans.tlocalvideochat.net.netty.findLocalAddressV4
import com.tans.tlocalvideochat.webrtc.WebRtc
import com.tans.tlocalvideochat.webrtc.broadcast.sender.BroadcastSender
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

    private val broadcastSender: BroadcastSender by lazyViewModelField("broadcastSender") {
        BroadcastSender()
    }

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
                // TODO:
                val address = findLocalAddressV4().getOrNull(0)
                if (address != null) {
                    launch {
                        broadcastSender.start(address)
                    }
                }
            } else {
                finish()
            }
        }
    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        broadcastSender.release()
    }
}