package com.tans.tlocalvideochat.webrtc.signaling

import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionServerClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.withClient
import com.tans.tlocalvideochat.net.netty.extensions.withServer
import com.tans.tlocalvideochat.net.netty.tcp.NettyTcpClientConnectionTask
import com.tans.tlocalvideochat.net.netty.tcp.NettyTcpServerConnectionTask
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.connectionActiveOrClosed
import com.tans.tlocalvideochat.webrtc.createNewClientFlowObserver
import com.tans.tlocalvideochat.webrtc.createStateFlowObserver
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class Signaling : CoroutineState<SignalingState> by CoroutineState(SignalingState.NoConnection), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val lock: Mutex by lazy {
        Mutex()
    }

    private val serverConnectionTask: AtomicReference<NettyTcpServerConnectionTask?> by lazy {
        AtomicReference(null)
    }

    private val clientConnectionTask: AtomicReference<ConnectionServerClientImpl?> by lazy {
        AtomicReference(null)
    }


    suspend fun start(localAddress: InetAddressWrapper, remoteAddress: InetAddressWrapper, isServer: Boolean) {
        lock.withLock {
            val state = currentState()
            if (state == SignalingState.Released) {
                error("Signaling already released.")
            }
            if (state != SignalingState.NoConnection) {
                error("Signaling already started.")
            }
            updateState { SignalingState.Requesting }
            if (isServer) {
                val (clientFlow, clientObserver) = createNewClientFlowObserver()
                val serverConnectionTask = NettyTcpServerConnectionTask(
                    bindAddress = localAddress.address,
                    bindPort = Const.SIGNALING_SERVER_PORT,
                    newClientTaskCallback = clientObserver
                )
                val (stateFlow, stateObserver) = createStateFlowObserver()
                serverConnectionTask.addObserver(stateObserver)
                serverConnectionTask.startTask()
                if (stateFlow.connectionActiveOrClosed()) {
                    AppLog.d(TAG, "Start signaling success.")
                    this.serverConnectionTask.getAndSet(serverConnectionTask)?.stopTask()
                    AppLog.d(TAG, "Waiting client.")
                    val clientTask = clientFlow.first()
                        .withClient<ConnectionClientImpl>(log = AppLog)
                        .withServer<ConnectionServerClientImpl>(log = AppLog)
                    // TODO: Add servers.
                    AppLog.d(TAG, "Client connected.")
                    this.clientConnectionTask.getAndSet(clientTask)?.stopTask()
                    updateState {
                        SignalingState.Active(
                            localAddress = localAddress,
                            remoteAddress = remoteAddress,
                            isServer = true
                        )
                    }
                } else {
                    serverConnectionTask.stopTask()
                    updateState { SignalingState.NoConnection }
                    error("Start signaling server fail.")
                }
            } else {
                val clientConnectionTask = NettyTcpClientConnectionTask(
                    serverAddress = remoteAddress.address,
                    serverPort = Const.SIGNALING_SERVER_PORT
                )
                val (stateFlow, stateObserver) = createStateFlowObserver()
                clientConnectionTask.addObserver(stateObserver)
                clientConnectionTask.startTask()
                if (stateFlow.connectionActiveOrClosed()) {
                    AppLog.d(TAG, "Connect to signaling server success.")
                    this.clientConnectionTask.getAndSet(
                        clientConnectionTask
                            .withClient<ConnectionClientImpl>(log = AppLog)
                            .withServer<ConnectionServerClientImpl>(log = AppLog)
                    )?.stopTask()
                } else {
                    clientConnectionTask.stopTask()
                    updateState { SignalingState.NoConnection }
                    error("Connect to signaling server fail.")
                }
            }
        }
    }

    suspend fun stop() {
        lock.withLock {
            serverConnectionTask.getAndSet(null)?.stopTask()
            clientConnectionTask.getAndSet(null)?.stopTask()
            updateState { SignalingState.NoConnection }
        }
    }

    fun release() {
        launch {
            stop()
            updateState { SignalingState.Released }
            cancel()
        }
    }

    companion object {
        private const val TAG = "Signaling"
    }
}