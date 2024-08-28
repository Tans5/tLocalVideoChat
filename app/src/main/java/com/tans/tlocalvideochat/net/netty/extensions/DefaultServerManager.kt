package com.tans.tlocalvideochat.net.netty.extensions

import com.tans.tlocalvideochat.net.ILog
import com.tans.tlocalvideochat.net.netty.INettyConnectionTask
import com.tans.tlocalvideochat.net.netty.NettyConnectionObserver
import com.tans.tlocalvideochat.net.netty.NettyTaskState
import com.tans.tlocalvideochat.net.netty.PackageData
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class DefaultServerManager(
    val connectionTask: INettyConnectionTask,
    private val converterFactory: IConverterFactory = DefaultConverterFactory(),
    private val log: ILog
) : IServerManager, NettyConnectionObserver {

    init {
        connectionTask.addObserver(this)
    }

    private val servers: LinkedBlockingDeque<IServer<*, *>> by lazy {
        LinkedBlockingDeque()
    }

    private val handledMessageId: ConcurrentHashMap<Long, Unit> by lazy {
        ConcurrentHashMap()
    }

    override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {}

    override fun onNewMessage(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData,
        task: INettyConnectionTask
    ) {
        val server = servers.find { it.couldHandle(msg.type) }
        if (server != null) {
            val isNew = !handledMessageId.containsKey(msg.messageId)
            handledMessageId[msg.messageId] = Unit
            server.dispatchRequest(
                localAddress = localAddress,
                remoteAddress = remoteAddress,
                msg = msg,
                converterFactory = converterFactory,
                connectionTask = task,
                isNewRequest = isNew
            )
        }
    }

    override fun <Request, Response> registerServer(s: IServer<Request, Response>) {
        servers.add(s)
    }

    override fun <Request, Response> unregisterServer(s: IServer<Request, Response>) {
        servers.remove(s)
    }

    override fun clearAllServers() {
        servers.clear()
    }

    companion object {
        private const val TAG = "DefaultServerManager"
    }
}