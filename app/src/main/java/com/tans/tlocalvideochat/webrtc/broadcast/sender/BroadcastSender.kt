package com.tans.tlocalvideochat.webrtc.broadcast.sender

import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionServerImpl
import com.tans.tlocalvideochat.net.netty.extensions.IClientManager
import com.tans.tlocalvideochat.net.netty.extensions.requestSimplify
import com.tans.tlocalvideochat.net.netty.extensions.simplifyServer
import com.tans.tlocalvideochat.net.netty.extensions.withClient
import com.tans.tlocalvideochat.net.netty.extensions.withServer
import com.tans.tlocalvideochat.net.netty.getBroadcastAddress
import com.tans.tlocalvideochat.net.netty.udp.NettyUdpConnectionTask
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.broadcast.connectionActiveOrClosed
import com.tans.tlocalvideochat.webrtc.broadcast.createStateFlowObserver
import com.tans.tlocalvideochat.webrtc.broadcast.sender.model.BroadcastMsg
import com.tans.tlocalvideochat.webrtc.broadcast.sender.model.RequestConnectReq
import com.tans.tlocalvideochat.webrtc.broadcast.sender.model.RequestConnectResp
import com.tans.tlocalvideochat.webrtc.broadcast.sender.model.SenderMsgType
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class BroadcastSender :
    CoroutineState<BroadcastSenderState> by CoroutineState(BroadcastSenderState.NoConnection),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val lock: Mutex by lazy {
        Mutex()
    }

    private val connectRequestFlow by lazy {
        MutableSharedFlow<ConnectRequest>()
    }

    private val senderTask: AtomicReference<ConnectionClientImpl?> by lazy {
        AtomicReference(null)
    }

    private val waitingConnectServerTask: AtomicReference<ConnectionServerImpl?> by lazy {
        AtomicReference(null)
    }

    fun observeConnectRequest(): Flow<ConnectRequest> = connectRequestFlow

    suspend fun start(localAddress: InetAddress) {
        lock.withLock {
            val lastState = currentState()
            if (lastState != BroadcastSenderState.NoConnection) {
                error("BroadcastSender already started.")
            }
            updateState { BroadcastSenderState.Requesting }

            // Broadcast sender task.
            val broadcastAddress = localAddress.getBroadcastAddress().first
            val senderTask = NettyUdpConnectionTask(
                connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Connect(
                    address = broadcastAddress,
                    port = Const.BROADCAST_SENDER_PORT
                ),
                enableBroadcast = true
            ).withClient<ConnectionClientImpl>(log = AppLog)
            val (senderTaskStateFlow, senderTaskObserver) = createStateFlowObserver()
            senderTask.addObserver(senderTaskObserver)
            senderTask.startTask()
            val senderTaskConnectResult = senderTaskStateFlow.connectionActiveOrClosed()
            if (!senderTaskConnectResult) {
                senderTask.stopTask()
                updateState { BroadcastSenderState.NoConnection }
                error("Start sender task fail.")
            }
            AppLog.d(TAG, "Start broadcast sender task success.")


            // Waiting connect server task.
            val waitingConnectServer = simplifyServer<RequestConnectReq, RequestConnectResp>(
                requestType = SenderMsgType.ConnectReq.type,
                responseType = SenderMsgType.ConnectResp.type,
                log = AppLog,
                onRequest = { _, ra, req, isNew ->
                    if (ra != null && isNew) {
                        AppLog.d(TAG, "Receive client request: ra=$ra, req=$req")
                        runCatching {
                            this@BroadcastSender.launch {
                                connectRequestFlow.emit(ConnectRequest(request = req, remoteAddress = ra.address))
                            }
                        }
                    }
                    RequestConnectResp(Const.DEVICE_NAME)
                }
            )
            val waitingConnectServerTask = NettyUdpConnectionTask(
                connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                    address = localAddress,
                    port = Const.WAITING_CONNECT_SERVER_PORT
                )
            ).withServer<ConnectionServerImpl>(log = AppLog)
            waitingConnectServerTask.registerServer(waitingConnectServer)
            val (waitingConnectServerStateFlow, waitingConnectServerObserver) = createStateFlowObserver()
            waitingConnectServerTask.addObserver(waitingConnectServerObserver)
            val waitingConnectServerResult = waitingConnectServerStateFlow.connectionActiveOrClosed()
            if (!waitingConnectServerResult) {
                senderTask.stopTask()
                waitingConnectServerTask.stopTask()
                updateState { BroadcastSenderState.NoConnection }
                error("Start waiting connect server task fail.")
            }

            val oldSenderTask = this@BroadcastSender.senderTask.getAndSet(senderTask)
            if (oldSenderTask != null) {
                AppLog.e(TAG, "Stop old broadcast sender task.")
                oldSenderTask.stopTask()
            }
            val oldConnectTask = this.waitingConnectServerTask.getAndSet(waitingConnectServerTask)
            if (oldConnectTask != null) {
                AppLog.e(TAG, "Stop old waiting connect server task.")
                oldConnectTask.stopTask()
            }

            // Connection is created, do send broadcast and waiting connection close.
            val job = runCatching {
                launch {
                    val closeChannel = MutableSharedFlow<Unit>()
                    val sendJob = launch {
                        while (true) {
                            val state = currentState()
                            if (state is BroadcastSenderState.Active) {
                                senderTask.requestSimplify<BroadcastMsg, Unit>(
                                    type = SenderMsgType.BroadcastMsg.type,
                                    request = BroadcastMsg(
                                        deviceName = Const.DEVICE_NAME,
                                        version = Const.VERSION
                                    ),
                                    retryTimes = 0,
                                    callback = object : IClientManager.RequestCallback<Unit> {
                                        override fun onSuccess(
                                            type: Int,
                                            messageId: Long,
                                            localAddress: InetSocketAddress?,
                                            remoteAddress: InetSocketAddress?,
                                            d: Unit
                                        ) { }

                                        override fun onFail(errorMsg: String) { }
                                    }
                                )
                                AppLog.d(TAG, "Send broadcast msg.")
                            } else {
                                AppLog.d(TAG, "Skip broadcast msg, because of state: $state")
                            }
                            delay(500L)
                        }
                    }

                    val senderConnectionJob = launch {
                        senderTaskStateFlow.connectionActiveOrClosed()
                        AppLog.e(TAG, "Sender connection closed.")
                        closeChannel.emit(Unit)
                    }

                    val waitingConnectionJob = launch {
                        waitingConnectServerStateFlow.connectionActiveOrClosed()
                        AppLog.e(TAG, "Waiting connect connection closed.")
                        closeChannel.emit(Unit)
                    }
                    closeChannel.first()
                    sendJob.cancel()
                    senderConnectionJob.cancel()
                    waitingConnectionJob.cancel()

                    senderTask.stopTask()
                    waitingConnectServerTask.stopTask()

                    updateState { BroadcastSenderState.NoConnection }
                }
            }.getOrNull()
            updateState { BroadcastSenderState.Active(localAddress = localAddress, broadcastAddress = broadcastAddress, broadcastJob = job) }
        }
    }

    suspend fun resume() {
        lock.withLock {
            val state = currentState()
            if (state is BroadcastSenderState.Paused) {
                updateState {
                    BroadcastSenderState.Active(
                        localAddress = state.localAddress,
                        broadcastAddress = state.broadcastAddress,
                        broadcastJob = state.broadcastJob
                    )
                }
            }
        }
    }

    suspend fun pause() {
        lock.withLock {
            val state = currentState()
            if (state is BroadcastSenderState.Active) {
                updateState {
                    BroadcastSenderState.Paused(
                        localAddress = state.localAddress,
                        broadcastAddress = state.broadcastAddress,
                        broadcastJob = state.broadcastJob
                    )
                }
            }
        }
    }

    suspend fun stop() {
        lock.withLock {
            senderTask.getAndSet(null)?.stopTask()
            waitingConnectServerTask.getAndSet(null)?.stopTask()
            updateState { BroadcastSenderState.NoConnection }
        }
        cancel()
    }

    companion object {
        private const val TAG = "BroadcastSender"
    }
}