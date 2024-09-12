package com.tans.tlocalvideochat.webrtc

import android.os.Parcelable
import androidx.annotation.Keep
import com.tans.tlocalvideochat.net.netty.INettyConnectionTask
import com.tans.tlocalvideochat.net.netty.NettyConnectionObserver
import com.tans.tlocalvideochat.net.netty.NettyTaskState
import com.tans.tlocalvideochat.net.netty.PackageData
import com.tans.tlocalvideochat.net.netty.extensions.IClientManager
import com.tans.tlocalvideochat.net.netty.extensions.requestSimplify
import com.tans.tlocalvideochat.net.netty.tcp.NettyTcpServerConnectionTask
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


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

fun createNewClientFlowObserver(): Pair<Flow<NettyTcpServerConnectionTask.ChildConnectionTask>, ((childConnection: NettyTcpServerConnectionTask.ChildConnectionTask) -> Unit)> {
    val clientFlow = MutableSharedFlow<NettyTcpServerConnectionTask.ChildConnectionTask>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)
    val clientCallback = { clientTask: NettyTcpServerConnectionTask.ChildConnectionTask ->
        clientFlow.tryEmit(clientTask)
        Unit
    }
    return clientFlow to clientCallback
}

suspend fun Flow<NettyTaskState>.connectionActiveOrClosed(): Boolean {
    val state = this.filter { it != NettyTaskState.NotExecute }.first()
    return state is NettyTaskState.ConnectionActive
}

suspend fun Flow<NettyTaskState>.connectionClosed() {
    this.filter { it !is NettyTaskState.ConnectionActive }.first()
}

suspend inline fun <reified Req, reified Resp> IClientManager.requestSimplifySuspend(
    type: Int,
    request: Req,
    retryTimes: Int = 2,
    retryTimeout: Long = 1000L,
): Resp {
    return suspendCancellableCoroutine { cont ->
        requestSimplify<Req, Resp>(
            type = type,
            request = request,
            retryTimes = retryTimes,
            retryTimeout = retryTimeout,
            callback = object : IClientManager.RequestCallback<Resp> {
                override fun onSuccess(
                    type: Int,
                    messageId: Long,
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    d: Resp
                ) {
                    if (cont.isActive) {
                        cont.resume(d)
                    }
                }

                override fun onFail(errorMsg: String) {
                    if (cont.isActive) {
                        cont.resumeWithException(IOException(errorMsg))
                    }
                }
            }
        )
    }
}

suspend inline fun <reified Req, reified Resp> IClientManager.requestSimplifySuspend(
    type: Int,
    request: Req,
    targetAddress: InetSocketAddress,
    senderAddress: InetSocketAddress? = null,
    retryTimes: Int = 2,
    retryTimeout: Long = 1000L,
): Resp {
    return suspendCancellableCoroutine { cont ->
        requestSimplify<Req, Resp>(
            type = type,
            request = request,
            targetAddress = targetAddress,
            senderAddress = senderAddress,
            retryTimes = retryTimes,
            retryTimeout = retryTimeout,
            callback = object : IClientManager.RequestCallback<Resp> {
                override fun onSuccess(
                    type: Int,
                    messageId: Long,
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    d: Resp
                ) {
                    if (cont.isActive) {
                        cont.resume(d)
                    }
                }

                override fun onFail(errorMsg: String) {
                    if (cont.isActive) {
                        cont.resumeWithException(IOException(errorMsg))
                    }
                }
            }
        )
    }
}

@Keep
@Parcelize
data class InetAddressWrapper(
    val address: InetAddress
) : Parcelable {
    override fun hashCode(): Int {
        return address.address.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is InetAddressWrapper) {
            address.address.contentEquals(other.address.address)
        } else {
            false
        }
    }

    override fun toString(): String {
        val bytes = address.address
        // Only support ipv4
        return if (bytes.size == 4) {
            val ni = NetworkInterface.getByInetAddress(address)?.interfaceAddresses?.first { it.address.address.contentEquals(address.address) }
            "${bytes[0].toUByte()}.${bytes[1].toUByte()}.${bytes[2].toUByte()}.${bytes[3].toUByte()}${if (ni != null) "/${ni.networkPrefixLength}" else ""}"
        } else {
            ""
        }
    }
}

fun InetAddress.wrap() = InetAddressWrapper(this)