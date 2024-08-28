package com.tans.tlocalvideochat.net.netty.extensions

import com.tans.tlocalvideochat.net.netty.INettyConnectionTask

class ConnectionServerClientImpl(
    val connectionTask: INettyConnectionTask,
    val serverManager: IServerManager,
    val clientManager: IClientManager
) : INettyConnectionTask by connectionTask, IServerManager by serverManager, IClientManager by clientManager