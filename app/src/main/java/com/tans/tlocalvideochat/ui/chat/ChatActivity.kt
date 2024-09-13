package com.tans.tlocalvideochat.ui.chat

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.os.BundleCompat
import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ChatActivityBinding
import com.tans.tlocalvideochat.ui.showOptionalDialogSuspend
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.WebRtc
import com.tans.tlocalvideochat.webrtc.WebRtcState
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon.RendererEvents

@SystemBarStyle
class ChatActivity : BaseCoroutineStateActivity<Unit>(Unit) {

    private val webRtc: WebRtc by lazyViewModelField("webRtc") {
        WebRtc(this.applicationContext)
    }

    override val layoutId: Int = R.layout.chat_activity

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch {
            val localAddress = intent.getLocalAddress()
            val remoteAddress = intent.getRemoteAddress()
            val isServer = intent.isServer()
            if (localAddress != null && remoteAddress != null) {
                runCatching {
                    webRtc.createRtcConnection(localAddress = localAddress, remoteAddress = remoteAddress, isServer = isServer)
                }.onFailure {
                    AppLog.e(TAG, "Create rtc connection fail: ${it.message}", it)
                }
            }
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ChatActivityBinding.bind(contentView)
        viewBinding.localRenderView.init(webRtc.eglBaseContext, object : RendererEvents {
            override fun onFirstFrameRendered() = Unit
            override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) = Unit
        })
        viewBinding.remoteRenderView.init(webRtc.eglBaseContext, object : RendererEvents {
            override fun onFirstFrameRendered() = Unit
            override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) = Unit
        })
        webRtc.localVideoTrack.addSink(viewBinding.localRenderView)
        launch {
            webRtc.observeRemoteVideoTrack()
                .collect { it.addSink(viewBinding.remoteRenderView) }
        }

        launch {
            webRtc.stateFlow()
                .flowOn(Dispatchers.IO)
                .collect { s ->
                    when (s) {
                        is WebRtcState.Error -> {
                            supportFragmentManager.showOptionalDialogSuspend(
                                title = getString(R.string.chat_act_connection_error_title),
                                message = s.msg,
                                positiveButtonText = getString(R.string.chat_act_ok),
                                negativeButtonText = null
                            )
                            finish()
                        }
                        is WebRtcState.RtcConnectionDisconnected -> {
                            supportFragmentManager.showOptionalDialogSuspend(
                                title = getString(R.string.chat_act_connection_disconnect_title),
                                message = getString(R.string.chat_act_connection_disconnect_content),
                                positiveButtonText = getString(R.string.chat_act_ok),
                                negativeButtonText = null
                            )
                            finish()
                        }
                        else -> {}
                    }
                }
        }
        viewBinding.switchCameraCard.clicks(this, 500L) {
            webRtc.switchCamera()
        }
        viewBinding.mirrorCameraCard.clicks(this, 500L) {
            webRtc.switchCameraMirrorMode()
        }
        viewBinding.root.clicks(this) {
            if (viewBinding.optLayout.visibility == View.VISIBLE) {
                viewBinding.optLayout.visibility = View.GONE
            } else {
                viewBinding.optLayout.visibility = View.VISIBLE
            }
        }
    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        webRtc.release()
    }

    companion object {
        private const val TAG = "ChatActivity"

        private const val LOCAL_ADDRESS_EXTRA = "local_address_extra"
        private const val REMOTE_ADDRESS_EXTRA = "remote_address_extra"
        private const val IS_SERVER_EXTRA = "is_server_extra"

        fun Intent.getLocalAddress(): InetAddressWrapper? {
            return extras?.let {
                BundleCompat.getParcelable(it, LOCAL_ADDRESS_EXTRA, InetAddressWrapper::class.java)
            }
        }

        fun Intent.getRemoteAddress(): InetAddressWrapper? {
            return extras?.let {
                BundleCompat.getParcelable(it, REMOTE_ADDRESS_EXTRA, InetAddressWrapper::class.java)
            }
        }


        fun Intent.isServer(): Boolean = this.getBooleanExtra(IS_SERVER_EXTRA, false)

        fun createIntent(
            context: Context,
            localAddress: InetAddressWrapper,
            remoteAddress: InetAddressWrapper,
            isServer: Boolean
        ): Intent {
            val i = Intent(context, ChatActivity::class.java)
            i.putExtra(LOCAL_ADDRESS_EXTRA, localAddress)
            i.putExtra(REMOTE_ADDRESS_EXTRA, remoteAddress)
            i.putExtra(IS_SERVER_EXTRA, isServer)
            return i
        }
    }
}