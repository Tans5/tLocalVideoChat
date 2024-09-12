package com.tans.tlocalvideochat.ui.main

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.ScanQrcodeDialogBinding
import com.tans.tlocalvideochat.ui.CoroutineDialogCancelableResultCallback
import com.tans.tlocalvideochat.ui.coroutineShowSafe
import com.tans.tlocalvideochat.ui.fromJson
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ScanQRCodeDialog : BaseCoroutineStateCancelableResultDialogFragment<Unit, QRCodeAddressModel> {

    constructor() : super(Unit, null)

    constructor(callback: DialogCancelableResultCallback<QRCodeAddressModel>) : super(Unit, callback)

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.scan_qrcode_dialog, parent, false)
    }

    override fun firstLaunchInitData() { }

    override fun bindContentView(view: View) {
        val viewBinding = ScanQrcodeDialogBinding.bind(view)
        viewBinding.scanLineView.post {
            val toY = viewBinding.scanLineView.measuredWidth.toFloat()
            val animation = TranslateAnimation(0f, 0f, 0f, toY)
            animation.duration = 1000
            animation.repeatCount = Animation.INFINITE
            animation.repeatMode = Animation.REVERSE
            viewBinding.scanLineView.startAnimation(animation)
        }

        launch {
            val cameraProvider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(requireContext()).get()
            }
            val preview = Preview.Builder().build()
            viewBinding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            preview.setSurfaceProvider(viewBinding.previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setBackgroundExecutor(Dispatchers.IO.asExecutor())
                .build()
            val barcodeOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val barcodeScanningClient = BarcodeScanning.getClient(barcodeOptions)
            val hasFindQRCode = AtomicBoolean(false)
            analysis.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                val imageBitmap = imageProxy.toBitmap()
                if (!hasFindQRCode.get()) {
                    val barcodeInputImage = InputImage.fromBitmap(imageBitmap, imageProxy.imageInfo.rotationDegrees)
                    barcodeScanningClient.process(barcodeInputImage)
                        .addOnSuccessListener {
                            if (it != null) {
                                for (barcode in it) {
                                    AppLog.d(TAG, "Find qrcode: ${barcode.rawValue}")
                                }
                            }
                            if (it != null && it.isNotEmpty() && hasFindQRCode.compareAndSet(false, true)) {
                                this@ScanQRCodeDialog.launch {
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        requireContext().getSystemService<VibratorManager>()?.defaultVibrator
                                    } else {
                                        requireContext().getSystemService<Vibrator>()
                                    }
                                    if (vibrator != null) {
                                        try {
                                            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                                            vibrator.vibrate(effect)
                                        } catch (iae: IllegalArgumentException) {
                                            AppLog.e(TAG, "Vibrator error: ${iae.message}", iae)
                                        }
                                    }
                                    val barcodeString = it.getOrNull(0)?.rawValue
                                    if (barcodeString != null) {
                                        val addressModel = barcodeString.fromJson<QRCodeAddressModel>()
                                        if (addressModel != null && addressModel.version == Const.VERSION) {
                                            onResult(addressModel)
                                        } else {
                                            AppLog.e(TAG, "Wrong barcode string: $barcodeString")
                                            onCancel()
                                        }
                                    } else {
                                        AppLog.e(TAG, "Barcode string is null.")
                                        onCancel()
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            AppLog.e(TAG, "Find qrcode error: ${it.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@ScanQRCodeDialog, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Throwable) {
                AppLog.e(TAG, "Start camera error: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "ScanQRCodeDialog"
    }
}

suspend fun FragmentManager.showScanQRCodeDialogSuspend(): QRCodeAddressModel? {
    return suspendCancellableCoroutine { cont ->
        val d = ScanQRCodeDialog(CoroutineDialogCancelableResultCallback(cont))
        coroutineShowSafe(d, "ScanQRCodeDialog#${System.currentTimeMillis()}", cont)
    }
}