package com.tans.tlocalvideochat.webrtc.broadcast

import com.tans.tlocalvideochat.net.netty.INettyConnectionTask
import com.tans.tlocalvideochat.net.netty.NettyConnectionObserver
import com.tans.tlocalvideochat.net.netty.NettyTaskState
import com.tans.tlocalvideochat.net.netty.PackageData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.InetSocketAddress


fun createStateFlowObserver(): Pair<Flow<NettyTaskState>, NettyConnectionObserver> {
    val stateFlow = MutableStateFlow<NettyTaskState>(NettyTaskState.NotExecute)
    val observer = object : NettyConnectionObserver {
        override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
            stateFlow.value = nettyState
        }

        override fun onNewMessage(
            localAddress: InetSocketAddress?,
            remoteAddress: InetSocketAddress?,
            msg: PackageData,
            task: INettyConnectionTask
        ) {}
    }
    return stateFlow to observer
}

suspend fun Flow<NettyTaskState>.connectionActiveOrClosed(): Boolean {
    val state = this.filter { it != NettyTaskState.NotExecute }.first()
    return state is NettyTaskState.ConnectionActive
}