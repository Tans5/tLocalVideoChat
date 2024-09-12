package com.tans.tlocalvideochat.ui.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ShowQrcodeDialogBinding
import com.tans.tlocalvideochat.net.netty.toInt
import com.tans.tlocalvideochat.ui.toJson
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import net.glxn.qrgen.android.QRCode
import kotlinx.coroutines.launch

class ShowQRCodeDialog : BaseCoroutineStateDialogFragment<Unit> {

    private val address: InetAddressWrapper?

    constructor() : super(Unit) {
        address = null
    }

    constructor(address: InetAddressWrapper) : super(Unit) {
        this.address = address
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.show_qrcode_dialog, parent, false)
    }

    override fun firstLaunchInitData() {  }

    override fun bindContentView(view: View) {
        address ?: return
        val viewBinding = ShowQrcodeDialogBinding.bind(view)
        launch {
            val addressModel = QRCodeAddressModel(
                address = address.address.toInt(),
                version = Const.VERSION,
                deviceName = Const.DEVICE_NAME
            )
            val json = addressModel.toJson()
            if (json != null) {
                val qrcodeBitmap = QRCode.from(json).withSize(320, 320).bitmap()
                if (qrcodeBitmap != null) {
                    viewBinding.qrCodeIv.setImageBitmap(qrcodeBitmap)
                }
            }
        }
    }
}