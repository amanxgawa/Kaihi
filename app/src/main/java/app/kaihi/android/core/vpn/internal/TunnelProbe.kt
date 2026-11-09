package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.ProbeTargets
import app.kaihi.android.core.vpn.SocksEndpoint
import app.kaihi.android.platform.di.IoDispatcher
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val PROBE_DEADLINE_MS = 6_000L

internal fun interface TunnelProbe {
    suspend fun probe(socks: SocksEndpoint): Boolean
}

@Singleton
internal class SocksTunnelProbe @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TunnelProbe {
    override suspend fun probe(socks: SocksEndpoint): Boolean = withContext(ioDispatcher) {
        val socketGuard = ProbeSocketGuard()
        val deadlineNanos = System.nanoTime() + PROBE_DEADLINE_MS * NANOS_PER_MILLI
        withTimeoutOrNull(PROBE_DEADLINE_MS) {
            coroutineScope {
                val operation = async(ioDispatcher) {
                    val probeContext = currentCoroutineContext()
                    val (firstHost, secondHost) = ProbeTargets.secondOpinionHosts()
                    if (probeHost(socks, firstHost, deadlineNanos, socketGuard, probeContext)) {
                        true
                    } else {
                        probeContext.ensureActive()
                        probeHost(socks, secondHost, deadlineNanos, socketGuard, probeContext)
                    }
                }
                try {
                    operation.await()
                } finally {
                    socketGuard.cancelAndClose()
                }
            }
        } ?: false
    }

    private fun probeHost(
        socks: SocksEndpoint,
        host: String,
        deadlineNanos: Long,
        socketGuard: ProbeSocketGuard,
        probeContext: kotlin.coroutines.CoroutineContext,
    ): Boolean {
        probeContext.ensureActive()
        val socket = Socket()
        if (!socketGuard.install(socket)) {
            socket.closeQuietly()
            probeContext.ensureActive()
            return false
        }
        return try {
            val connectTimeout = remainingMillis(deadlineNanos) ?: return false
            socket.connect(InetSocketAddress(socks.host, socks.port), connectTimeout)
            socket.soTimeout = remainingMillis(deadlineNanos) ?: return false
            Socks5Client.connect(socket, socks.username, socks.password, host, ProbeTargets.PORT)

            val tlsSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, ProbeTargets.PORT, true) as SSLSocket
            try {
                tlsSocket.soTimeout = remainingMillis(deadlineNanos) ?: return false
                tlsSocket.sslParameters = tlsSocket.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                tlsSocket.startHandshake()
                true
            } finally {
                tlsSocket.closeQuietly()
            }
        } catch (error: IOException) {
            false
        } catch (error: IllegalStateException) {
            false
        } finally {
            socketGuard.release(socket)
            socket.closeQuietly()
        }
    }

    private fun remainingMillis(deadlineNanos: Long): Int? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return null
        return ((remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .coerceAtLeast(1)
    }

    private fun Socket.closeQuietly() {
        try {
            close()
        } catch (error: IOException) {
            // The probe result has already been decided; closing is best-effort cleanup.
        }
    }

    private inner class ProbeSocketGuard {
        private var cancelled = false
        private var active: Socket? = null

        @Synchronized
        fun install(socket: Socket): Boolean {
            if (cancelled) return false
            active = socket
            return true
        }

        @Synchronized
        fun release(socket: Socket) {
            if (active === socket) active = null
        }

        @Synchronized
        fun cancelAndClose() {
            cancelled = true
            active?.closeQuietly()
            active = null
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
