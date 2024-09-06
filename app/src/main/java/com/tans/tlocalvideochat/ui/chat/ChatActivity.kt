package com.tans.tlocalvideochat.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ChatActivityBinding
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.WebRtc
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
                }
            }
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ChatActivityBinding.bind(contentView)

    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        webRtc.release()
    }

    companion object {

        private const val LOCAL_ADDRESS_EXTRA = "local_address_extra"
        private const val REMOTE_ADDRESS_EXTRA = "remote_address_extra"
        private const val IS_SERVER_EXTRA = "is_server_extra"

        fun Intent.getLocalAddress(): InetAddressWrapper? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                this.getParcelableExtra(LOCAL_ADDRESS_EXTRA, InetAddressWrapper::class.java)
            else this.getParcelableExtra(LOCAL_ADDRESS_EXTRA)

        fun Intent.getRemoteAddress(): InetAddressWrapper? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                this.getParcelableExtra(REMOTE_ADDRESS_EXTRA, InetAddressWrapper::class.java)
            else this.getParcelableExtra(REMOTE_ADDRESS_EXTRA)

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