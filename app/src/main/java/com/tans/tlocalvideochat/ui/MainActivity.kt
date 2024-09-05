package com.tans.tlocalvideochat.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import androidx.core.content.getSystemService
import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.MainActivityBinding
import com.tans.tlocalvideochat.databinding.RemoteDeviceItemLayoutBinding
import com.tans.tlocalvideochat.net.netty.findLocalAddressV4
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.broadcast.receiver.BroadcastReceiver
import com.tans.tlocalvideochat.webrtc.broadcast.receiver.BroadcastReceiverState
import com.tans.tlocalvideochat.webrtc.broadcast.sender.BroadcastSender
import com.tans.tlocalvideochat.webrtc.broadcast.sender.BroadcastSenderState
import com.tans.tlocalvideochat.webrtc.wrap
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.permission.permissionsRequestSimplifySuspend
import com.tans.tuiutils.systembar.annotation.ContentViewFitSystemWindow
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

@SystemBarStyle
@ContentViewFitSystemWindow
class MainActivity : BaseCoroutineStateActivity<MainActivity.Companion.State>(State()) {

    override val layoutId: Int = R.layout.main_activity

    private val broadcastSender: BroadcastSender by lazyViewModelField("broadcastSender") {
        BroadcastSender()
    }

    private val broadcastReceiver: BroadcastReceiver by lazyViewModelField("broadcastReceiver") {
        BroadcastReceiver()
    }

    private val networkRequest: NetworkRequest by lazy {
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
    }

    private val connectivityManager: ConnectivityManager by lazy {
        this.getSystemService()!!
    }

    private val networkCallback: ConnectivityManager.NetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AppLog.d(TAG, "Network available: $network")
                updateLocalAddress()
            }
            override fun onLost(network: Network) {
                AppLog.d(TAG, "Network lost: $network")
                updateLocalAddress()
            }
        }
    }

    private val wifiApChangeReceiver: android.content.BroadcastReceiver by lazy {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                AppLog.d(TAG, "Wifi AP changed.")
                updateLocalAddress()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val intentFilter = IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        registerReceiver(wifiApChangeReceiver, intentFilter)
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {

        // Broadcast sender and receiver.
        launch {
            updateLocalAddress(delay = 0L)
            val selectedAddressChannel = Channel<Optional<InetAddressWrapper>>()
            launch {
                var lastSenderJob: Job? = null
                var lastReceiverJob: Job? = null
                for (addressOptional in selectedAddressChannel) {
                    lastSenderJob?.cancel()
                    lastReceiverJob?.cancel()
                    val address = addressOptional.getOrNull()
                    AppLog.d(TAG, "Update select address: $address")
                    broadcastSender.stop()
                    broadcastReceiver.stop()
                    if (address != null) {
                        // Sender
                        lastSenderJob = launch {
                            while (true) {
                                runCatching {
                                    broadcastSender.start(address)
                                }.onSuccess {
                                    AppLog.d(TAG, "Start broadcast sender success.")
                                    broadcastSender.stateFlow()
                                        .filter { it !is BroadcastSenderState.Active && it !is BroadcastSenderState.Paused }
                                        .first()
                                    AppLog.d(TAG, "Broadcast sender connection closed.")
                                }.onFailure {
                                    AppLog.e(TAG, "Start broadcast sender fail: ${it.message}", it)
                                }
                                delay(1000L)
                            }
                        }

                        // Receiver
                        lastReceiverJob = launch {
                            while (true) {
                                runCatching {
                                    broadcastReceiver.start(address)
                                }.onSuccess {
                                    AppLog.d(TAG, "Start broadcast receiver success.")
                                    broadcastReceiver.stateFlow()
                                        .filter { it !is BroadcastReceiverState.Active}
                                        .first()
                                    AppLog.d(TAG, "Broadcast receiver connection closed.")
                                }.onFailure {
                                    AppLog.e(TAG, "Start broadcast receiver fail: ${it.message}", it)
                                }
                                delay(1000L)
                            }
                        }
                    } else {
                        lastSenderJob = null
                        lastReceiverJob = null
                    }
                }
            }
            stateFlow().map { it.selectedAddress }
                .distinctUntilChanged()
                .collect { selectedAddressChannel.send(it) }
        }
    }

    private val connectLock: Mutex by lazy {
        Mutex()
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        launch {
            val viewBinding = MainActivityBinding.bind(contentView)
            val grant = runCatching {
                permissionsRequestSimplifySuspend(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            }.getOrNull() ?: false
            if (grant) {
                viewBinding.toolBar.title = Const.DEVICE_NAME

                renderStateNewCoroutine({ it.selectedAddress }) {
                    viewBinding.toolBar.subtitle = it.getOrNull()?.toString() ?: ""
                }
                viewBinding.toolBar.menu.findItem(R.id.main_act_menu_more).setOnMenuItemClickListener {
                    launch {
                        val s = currentState()
                        val selectedAddress = s.selectedAddress.getOrNull()
                        if (selectedAddress != null) {
                            val newSelected = supportFragmentManager.showSelectAddressDialog(s.allAddresses, selectedAddress)
                            if (newSelected != null && newSelected != selectedAddress) {
                                updateState { it.copy(selectedAddress = Optional.of(newSelected)) }
                            }
                        }
                    }
                    true
                }

                viewBinding.remoteDevicesRv.adapter = SimpleAdapterBuilderImpl(
                    itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_device_item_layout),
                    dataSource = FlowDataSourceImpl(
                        dataFlow = broadcastReceiver.observeRemoteDevices(),
                        areDataItemsTheSameParam = { d1, d2 -> d1.firstUpdateTime == d2.firstUpdateTime && d1.broadcastMsg == d2.broadcastMsg },
                        areDataItemsContentTheSameParam = { d1, d2 -> d1.firstUpdateTime == d2.firstUpdateTime && d1.broadcastMsg == d2.broadcastMsg }
                    ),
                    dataBinder = DataBinderImpl { data, itemView, _ ->
                        val itemViewBinding = RemoteDeviceItemLayoutBinding.bind(itemView)
                        itemViewBinding.deviceNameTv.text = data.broadcastMsg.deviceName
                        itemViewBinding.addressTv.text = data.remoteAddress.toString()

                        itemViewBinding.root.clicks(coroutineScope = this@bindContentViewCoroutine, clickWorkOn = Dispatchers.IO) {
                            if (!connectLock.isLocked && this@MainActivity.isVisible) {
                                connectLock.withLock {
                                    runCatching {
                                        broadcastReceiver.requestConnect(data.remoteAddress)
                                    }.onSuccess {
                                        AppLog.d(TAG, "Request connect success.")
                                        // TODO:
                                    }.onFailure {
                                        AppLog.e(TAG, "Request connect fail: ${it.message}", it)
                                    }
                                }
                            }
                        }
                    }
                ).build()

                launch(Dispatchers.IO) {
                    broadcastSender.observeConnectRequest()
                        .collect {
                            if (!connectLock.isLocked && this@MainActivity.isVisible)
                                connectLock.withLock {
                                    AppLog.d(TAG, "Receive request: $it")
                                    // TODO:
                                }
                        }
                }

            } else {
                finish()
            }
        }
    }

    private var isVisible = false

    override fun onResume() {
        super.onResume()
        isVisible = true
        dataCoroutineScope.launch {
            broadcastSender.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        dataCoroutineScope.launch {
            broadcastSender.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        unregisterReceiver(wifiApChangeReceiver)
    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        broadcastSender.release()
        broadcastReceiver.release()
    }

    private val updateAddressLock: Mutex by lazy {
        Mutex()
    }

    private fun updateLocalAddress(delay: Long = 1000L) {
        dataCoroutineScope.launch {
            if (!updateAddressLock.isLocked) {
                updateAddressLock.withLock {
                    delay(delay)
                    val addresses = findLocalAddressV4().map { it.wrap() }
                    AppLog.d(TAG, "Update local addresses: $addresses")
                    updateState { oldState ->
                        if (addresses == oldState.allAddresses) {
                            oldState
                        } else {
                            val lastSelected = oldState.selectedAddress.getOrNull()
                            if (lastSelected == null || addresses.find { it == lastSelected } == null) {
                                oldState.copy(allAddresses = addresses, selectedAddress = Optional.ofNullable(addresses.getOrNull(0)))
                            } else {
                                oldState.copy(allAddresses = addresses)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        data class State(
            val allAddresses: List<InetAddressWrapper> = emptyList(),
            val selectedAddress: Optional<InetAddressWrapper> = Optional.empty()
        )

        private const val TAG = "MainActivity"
    }
}