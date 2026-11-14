package com.chesstutor.app.data.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A line-by-line connection to the other phone. [lines] ends when the connection closes. */
interface NearbyLink {
    val lines: Flow<String>

    /** Sends one line; false when the connection is gone. */
    suspend fun send(line: String): Boolean

    fun close()
}

/** Opens a nearby game (host) or finds one (join). */
interface NearbyConnector {
    /** Waits for one friend to join under [code]; [onListening] gets this phone's address, to type in when the code is not found. */
    suspend fun host(code: String, scope: CoroutineScope, onListening: (address: String?) -> Unit): NearbyLink

    /** Joins the game with [target]: a four-letter code, or an address such as "192.168.1.5:41234". */
    suspend fun join(target: String, scope: CoroutineScope): NearbyLink
}

/** A [NearbyLink] over a TCP socket. */
class SocketLink(private val socket: Socket, scope: CoroutineScope) : NearbyLink {
    private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    private val incoming = Channel<String>(Channel.UNLIMITED)
    private val writing = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                while (true) incoming.send(reader.readLine() ?: break)
            } catch (_: IOException) {
                // The other phone went away.
            } finally {
                incoming.close()
            }
        }
    }

    override val lines: Flow<String> = incoming.receiveAsFlow()

    override suspend fun send(line: String): Boolean = withContext(Dispatchers.IO) {
        writing.withLock {
            runCatching {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }.isSuccess
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * Nearby games on the local network, with no server: the host listens on a free port and
 * announces "ChessTutor-CODE" with Android's network service discovery; the joiner looks for
 * that name. Typing the host's address works where discovery is blocked (some hotspots).
 */
class LanConnector(context: Context) : NearbyConnector {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override suspend fun host(code: String, scope: CoroutineScope, onListening: (address: String?) -> Unit): NearbyLink {
        val server = withContext(Dispatchers.IO) { ServerSocket(0) }
        onListening(localAddress()?.let { "$it:${server.localPort}" })
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_PREFIX + code
            serviceType = SERVICE_TYPE
            port = server.localPort
        }
        val lock = wifi?.createMulticastLock("chesstutor-nearby")?.apply { setReferenceCounted(false); acquire() }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
        try {
            val socket = runInterruptible(Dispatchers.IO) { server.accept() }
            socket.tcpNoDelay = true
            return SocketLink(socket, scope)
        } finally {
            runCatching { nsd.unregisterService(registration) }
            runCatching { server.close() }
            lock?.release()
        }
    }

    override suspend fun join(target: String, scope: CoroutineScope): NearbyLink {
        val address = parseAddress(target) ?: discover(target)
        val socket = withContext(Dispatchers.IO) {
            Socket().apply {
                connect(address, CONNECT_TIMEOUT_MS)
                tcpNoDelay = true
            }
        }
        return SocketLink(socket, scope)
    }

    /** Finds the host announcing [code] and its address. */
    private suspend fun discover(code: String): InetSocketAddress {
        val lock = wifi?.createMulticastLock("chesstutor-join")?.apply { setReferenceCounted(false); acquire() }
        try {
            return suspendCancellableCoroutine { waiting ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onServiceLost(info: NsdServiceInfo) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, error: Int) = fail(waiting, "discovery failed ($error)")
                    override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (!info.serviceName.startsWith(SERVICE_PREFIX + code)) return
                        @Suppress("DEPRECATION")
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, error: Int) = fail(waiting, "could not reach the game ($error)")
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                @Suppress("DEPRECATION")
                                val host = info.host ?: return fail(waiting, "the game has no address")
                                if (waiting.isActive) waiting.resume(InetSocketAddress(host, info.port))
                            }
                        })
                    }
                }
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                waiting.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            }
        } finally {
            lock?.release()
        }
    }

    private fun fail(waiting: CancellableContinuation<InetSocketAddress>, why: String) {
        if (waiting.isActive) waiting.resumeWithException(IOException(why))
    }

    companion object {
        const val SERVICE_TYPE = "_chesstutor._tcp."
        const val SERVICE_PREFIX = "ChessTutor-"
        private const val CONNECT_TIMEOUT_MS = 8_000

        /** "192.168.1.5:41234" as an address, else null (then it is a code). */
        fun parseAddress(target: String): InetSocketAddress? {
            val host = target.substringBeforeLast(':', "").trim()
            val port = target.substringAfterLast(':', "").trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return InetSocketAddress(host, port)
        }

        /** This phone's address on the local network (Wi-Fi or its own hotspot). */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
