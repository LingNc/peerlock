package com.peerlock.system.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.peerlock.system.log.PeerLockLogger
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

@RequiresApi(Build.VERSION_CODES.R)
internal class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val onServiceResolved: (Int) -> Unit,
) {
    private var registered = false
    private var running = false
    private var serviceName: String? = null
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val discoveryListener = DiscoveryListener(this)
    private val resolveListener = ResolveListener(this)

    fun start() {
        if (running) return
        running = true
        if (!registered) {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        if (registered) {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
        }
    }

    private fun onDiscoveryStart() { registered = true }
    private fun onDiscoveryStop() { registered = false }

    private fun onServiceFound(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        nsdManager.resolveService(info, resolveListener)
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        if (!running) return
        @Suppress("DEPRECATION")
        val hostAddr = resolvedService.host?.hostAddress
        val isLocal = NetworkInterface.getNetworkInterfaces()?.asSequence()?.any { iface ->
            iface.inetAddresses.asSequence().any { hostAddr == it.hostAddress }
        } ?: false
        if (isLocal && isPortAvailable(resolvedService.port)) {
            serviceName = resolvedService.serviceName
            onServiceResolved(resolvedService.port)
        }
    }

    private fun isPortAvailable(port: Int) = try {
        ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", port), 1); false }
    } catch (_: IOException) { true }

    private class DiscoveryListener(private val mdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) { mdns.onDiscoveryStart() }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            PeerLockLogger.w(TAG, "Discovery start failed: $errorCode")
        }
        override fun onDiscoveryStopped(serviceType: String) { mdns.onDiscoveryStop() }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onServiceFound(serviceInfo: NsdServiceInfo) { mdns.onServiceFound(serviceInfo) }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
    }

    private class ResolveListener(private val mdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) { mdns.onServiceResolved(serviceInfo) }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        private const val TAG = "AdbMdns"
    }
}
