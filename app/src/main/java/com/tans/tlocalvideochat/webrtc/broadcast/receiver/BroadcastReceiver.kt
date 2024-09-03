package com.tans.tlocalvideochat.webrtc.broadcast.receiver

import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionServerImpl
import com.tans.tlocalvideochat.net.netty.extensions.simplifyServer
import com.tans.tlocalvideochat.net.netty.extensions.withClient
import com.tans.tlocalvideochat.net.netty.extensions.withServer
import com.tans.tlocalvideochat.net.netty.getBroadcastAddress
import com.tans.tlocalvideochat.net.netty.udp.NettyUdpConnectionTask
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.connectionActiveOrClosed
import com.tans.tlocalvideochat.webrtc.createStateFlowObserver
import com.tans.tlocalvideochat.webrtc.broadcast.model.BroadcastMsg
import com.tans.tlocalvideochat.webrtc.broadcast.model.RequestConnectReq
import com.tans.tlocalvideochat.webrtc.broadcast.model.RequestConnectResp
import com.tans.tlocalvideochat.webrtc.broadcast.model.SenderMsgType
import com.tans.tlocalvideochat.webrtc.connectionClosed
import com.tans.tlocalvideochat.webrtc.requestSimplifySuspend
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class BroadcastReceiver(
    private val remoteDevicesOutOfDateDuration: Long = 8000L
) : CoroutineState<BroadcastReceiverState> by CoroutineState(BroadcastReceiverState.NoConnection), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val lock by lazy {
        Mutex()
    }

    private val receiverTask: AtomicReference<ConnectionServerImpl?> by lazy {
        AtomicReference()
    }

    private val requestConnectTask: AtomicReference<ConnectionClientImpl?> by lazy {
        AtomicReference()
    }

    private val remoteDevicesFlow by lazy {
        MutableStateFlow<List<ScannedDevice>>(emptyList())
    }

    private val receiverServer by lazy {
        simplifyServer<BroadcastMsg, Unit>(
            requestType = SenderMsgType.BroadcastMsg.type,
            responseType = SenderMsgType.BroadcastMsg.type,
            log = AppLog,
            onRequest = { _, ra, msg, _ ->
                if (ra != null) {
                    newRemoteDeviceFound(ra.address, msg)
                }
                null
            }
        )
    }

    fun observeRemoteDevices(): Flow<List<ScannedDevice>> = remoteDevicesFlow

    suspend fun start(localAddress: InetAddress) {
        lock.withLock {
            val state = currentState()
            if (state == BroadcastReceiverState.Released) {
                error("BroadcastReceiver already released.")
            }
            if (state != BroadcastReceiverState.NoConnection) {
                error("BroadcastReceiver already started.")
            }
            updateState { BroadcastReceiverState.Requesting }
            val broadcastAddress = localAddress.getBroadcastAddress().first

            // Receiver task
            val receiverTask = NettyUdpConnectionTask(
                connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                    address = broadcastAddress,
                    port = Const.BROADCAST_SENDER_PORT
                ),
                enableBroadcast = true
            ).withServer<ConnectionServerImpl>(log = AppLog)
            receiverTask.registerServer(receiverServer)
            val (receiverStateFlow, receiverStateObserver) = createStateFlowObserver()
            receiverTask.addObserver(receiverStateObserver)
            receiverTask.startTask()
            val receiverResult = receiverStateFlow.connectionActiveOrClosed()
            if (!receiverResult) {
                receiverTask.stopTask()
                updateState { BroadcastReceiverState.NoConnection }
                error("Start receiver task fail.")
            }
            AppLog.d(TAG, "Start receiver task success.")

            // Request connect task
            val requestConnectTask = NettyUdpConnectionTask(
                connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                    address = localAddress,
                    port = Const.REQUEST_CONNECT_CLIENT_PORT
                )
            ).withClient<ConnectionClientImpl>(log = AppLog)
            val (requestConnectStateFlow, requestConnectStateObserver) = createStateFlowObserver()
            requestConnectTask.addObserver(requestConnectStateObserver)
            requestConnectTask.startTask()
            val requestConnectResult = requestConnectStateFlow.connectionActiveOrClosed()
            if (!requestConnectResult) {
                receiverTask.stopTask()
                requestConnectTask.stopTask()
                updateState { BroadcastReceiverState.NoConnection }
                error("Start request connect task fail.")
            }
            AppLog.d(TAG, "Start request connect task success.")

            val job = runCatching {
                launch {
                    val closeChannel = MutableSharedFlow<Unit>()

                    val checkRemoteDevicesJob = launch {
                        while (true) {
                            val oldDevices = remoteDevicesFlow.value
                            val now = System.currentTimeMillis()
                            if (oldDevices.any { now - it.latestUpdateTime > remoteDevicesOutOfDateDuration }) {
                                val newDevices = oldDevices.filter { now - it.latestUpdateTime <= remoteDevicesOutOfDateDuration }
                                remoteDevicesFlow.value = newDevices
                            }
                            delay(remoteDevicesOutOfDateDuration)
                        }
                    }

                    val receiverCloseJob = launch {
                        receiverStateFlow.connectionClosed()
                        AppLog.d(TAG, "Receiver connect closed.")
                        closeChannel.emit(Unit)
                    }

                    val requestConnectCloseJob = launch {
                        requestConnectStateFlow.connectionClosed()
                        AppLog.d(TAG, "Request connect task closed.")
                        closeChannel.emit(Unit)
                    }

                    closeChannel.first()

                    receiverCloseJob.cancel()
                    requestConnectCloseJob.cancel()
                    checkRemoteDevicesJob.cancel()
                    receiverTask.stopTask()
                    requestConnectTask.stopTask()
                    updateState { BroadcastReceiverState.NoConnection }
                }
            }.getOrNull()
            if (job == null) {
                receiverTask.stopTask()
                requestConnectTask.stopTask()
                updateState { BroadcastReceiverState.NoConnection }
                error("Create receiver job fail.")
            }

            val oldReceiverTask = this.receiverTask.getAndSet(receiverTask)
            if (oldReceiverTask != null) {
                oldReceiverTask.stopTask()
                AppLog.e(TAG, "Stop old receiver task.")
            }
            val oldRequestConnectTask = this.requestConnectTask.getAndSet(requestConnectTask)
            if (oldRequestConnectTask != null) {
                oldRequestConnectTask.stopTask()
                AppLog.e(TAG, "Stop old request connect task.")
            }

            updateState {
                BroadcastReceiverState.Active(
                    localAddress = localAddress,
                    broadcastAddress = broadcastAddress,
                    receiverJob = job
                )
            }
        }
    }

    suspend fun requestConnect(remoteAddress: InetAddress): RequestConnectResp {
        return lock.withLock {
            val requestTask = requestConnectTask.get() ?: error("Request task is null")
            requestTask.requestSimplifySuspend<RequestConnectReq, RequestConnectResp>(
                type = SenderMsgType.ConnectReq.type,
                request = RequestConnectReq(
                    deviceName = Const.DEVICE_NAME,
                    version = Const.VERSION
                ),
                targetAddress = InetSocketAddress(remoteAddress, Const.WAITING_CONNECT_SERVER_PORT)
            )
        }
    }

    suspend fun stop() {
        lock.withLock {
            val state = currentState()
            if (state is BroadcastReceiverState.Active) {
                state.receiverJob.cancel()
            }
            this.receiverTask.getAndSet(null)?.stopTask()
            this.requestConnectTask.getAndSet(null)?.stopTask()
            updateState { BroadcastReceiverState.NoConnection }
        }
    }


    fun release() {
        launch {
            stop()
            updateState { BroadcastReceiverState.Released }
            cancel()
        }
    }

    private fun newRemoteDeviceFound(remoteAddress: InetAddress, msg: BroadcastMsg) {
        if (msg.version == Const.VERSION) {
            launch {
                lock.withLock {
                    val oldDevices = remoteDevicesFlow.value
                    val targetDevice = oldDevices.find { it.remoteAddress.address.contentEquals(remoteAddress.address) }
                    val now = System.currentTimeMillis()
                    val newDevices = if (targetDevice == null) {
                        oldDevices + ScannedDevice(
                            firstUpdateTime = now,
                            latestUpdateTime = now,
                            remoteAddress = remoteAddress,
                            broadcastMsg = msg
                        )
                    } else {
                        oldDevices.map { if (it == targetDevice) targetDevice.copy(latestUpdateTime = now) else it }
                    }
                    remoteDevicesFlow.value = newDevices
                }
            }
        }
    }

    companion object {
        private const val TAG = "BroadcastReceiver"
    }
}