package com.tans.tlocalvideochat.webrtc.signaling

import com.tans.tlocalvideochat.AppLog
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.ConnectionServerClientImpl
import com.tans.tlocalvideochat.net.netty.extensions.simplifyServer
import com.tans.tlocalvideochat.net.netty.extensions.withClient
import com.tans.tlocalvideochat.net.netty.extensions.withServer
import com.tans.tlocalvideochat.net.netty.tcp.NettyTcpClientConnectionTask
import com.tans.tlocalvideochat.net.netty.tcp.NettyTcpServerConnectionTask
import com.tans.tlocalvideochat.webrtc.Const
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tlocalvideochat.webrtc.connectionActiveOrClosed
import com.tans.tlocalvideochat.webrtc.connectionClosed
import com.tans.tlocalvideochat.webrtc.createNewClientFlowObserver
import com.tans.tlocalvideochat.webrtc.createStateFlowObserver
import com.tans.tlocalvideochat.webrtc.requestSimplifySuspend
import com.tans.tlocalvideochat.webrtc.signaling.model.IceCandidateReq
import com.tans.tlocalvideochat.webrtc.signaling.model.SdpReq
import com.tans.tlocalvideochat.webrtc.signaling.model.SdpResp
import com.tans.tlocalvideochat.webrtc.signaling.model.SignalingMsgType
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class Signaling(
    private val exchangeSdp: (offer: SdpReq, isNew: Boolean) -> SdpResp?,
    private val remoteIceCandidate: (remoteIceCandidate: IceCandidateReq, isNew: Boolean) -> Unit
) : CoroutineState<SignalingState> by CoroutineState(SignalingState.NoConnection), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val lock: Mutex by lazy {
        Mutex()
    }

    private val serverConnectionTask: AtomicReference<NettyTcpServerConnectionTask?> by lazy {
        AtomicReference(null)
    }

    private val clientConnectionTask: AtomicReference<ConnectionServerClientImpl?> by lazy {
        AtomicReference(null)
    }

    private val sdpServer by lazy {
        simplifyServer<SdpReq, SdpResp>(
            requestType = SignalingMsgType.SdpReq.type,
            responseType = SignalingMsgType.SdpResp.type,
            log = AppLog,
            onRequest = { _, _, req, isNew ->
                exchangeSdp(req, isNew)
            }
        )
    }

    private val iceCandidateServer by lazy {
        simplifyServer<IceCandidateReq, Unit>(
            requestType = SignalingMsgType.IceReq.type,
            responseType = SignalingMsgType.IceResp.type,
            log = AppLog,
            onRequest = { _, _, req, isNew ->
                remoteIceCandidate(req, isNew)
            }
        )
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
            val (clientConnectionTask, clientConnectionStateFlow) = if (isServer) {
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
                    clientTask.registerServer(sdpServer)
                    clientTask.registerServer(iceCandidateServer)
                    AppLog.d(TAG, "Client connected.")
                    val (clientStateFlow, clientStateObserver) = createStateFlowObserver()
                    clientTask.addObserver(clientStateObserver)
                    clientTask to clientStateFlow
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
                    clientConnectionTask
                        .withClient<ConnectionClientImpl>(log = AppLog)
                        .withServer<ConnectionServerClientImpl>(log = AppLog) to stateFlow
                } else {
                    clientConnectionTask.stopTask()
                    updateState { SignalingState.NoConnection }
                    error("Connect to signaling server fail.")
                }
            }
            this.clientConnectionTask.getAndSet(clientConnectionTask)?.stopTask()
            val job = runCatching {
                launch {
                    clientConnectionStateFlow.connectionClosed()
                    AppLog.d(TAG, "Connection closed.")
                    clientConnectionTask.stopTask()
                    serverConnectionTask.get()?.stopTask()
                    updateState { SignalingState.NoConnection }
                }
            }.getOrNull()
            if (job == null) {
                clientConnectionTask.stopTask()
                serverConnectionTask.get()?.stopTask()
                updateState { SignalingState.NoConnection }
                error("Create waiting coroutine job fail.")
            }
            updateState {
                SignalingState.Active(
                    localAddress = localAddress,
                    remoteAddress = remoteAddress,
                    isServer = isServer,
                    signalingJob = job
                )
            }
        }
    }

    suspend fun requestExchangeSdp(localSdp: SdpReq): SdpResp {
        return lock.withLock {
            val connectionTask = clientConnectionTask.get() ?: error("No connection")
            connectionTask.requestSimplifySuspend<SdpReq, SdpResp>(
                type = SignalingMsgType.SdpReq.type,
                request = localSdp
            )
        }
    }

    suspend fun sendIceCandidate(localIceCandidate: IceCandidateReq) {
        return lock.withLock {
            val connectionTask = clientConnectionTask.get() ?: error("No connection")
            connectionTask.requestSimplifySuspend<IceCandidateReq, Unit>(
                type = SignalingMsgType.IceReq.type,
                request = localIceCandidate
            )
        }
    }

    suspend fun stop() {
        lock.withLock {
            currentState().let {
                if (it is SignalingState.Active) {
                    it.signalingJob.cancel()
                }
            }
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