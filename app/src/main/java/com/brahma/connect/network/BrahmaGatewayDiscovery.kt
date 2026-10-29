package com.brahma.connect.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.brahma.connect.core.AgentStateStore
import com.brahma.connect.core.GatewayEndpoint

class BrahmaGatewayDiscovery(private val context: Context) {

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    fun start(onFound: (GatewayEndpoint) -> Unit, onError: ((String) -> Unit)? = null) {
        val manager = nsdManager ?: run {
            onError?.invoke("NSD Service not available")
            return
        }

        stop()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                AgentStateStore.addLog("Discovery start failed: $errorCode")
                onError?.invoke("Discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                AgentStateStore.addLog("Discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                AgentStateStore.addLog("Searching for Brahma PC...")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                AgentStateStore.addLog("Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val service = serviceInfo ?: return
                if (service.serviceName.contains("brahma", ignoreCase = true) ||
                    service.serviceType.contains("_brahma._tcp")) {
                    resolveService(service, onFound)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                AgentStateStore.addLog("Service lost: ${serviceInfo?.serviceName}")
            }
        }

        try {
            manager.discoverServices("_brahma._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            onError?.invoke("Failed to start discovery: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, onFound: (GatewayEndpoint) -> Unit) {
        val manager = nsdManager ?: return
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                AgentStateStore.addLog("Resolve failed: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                val resolved = resolvedInfo ?: return
                val host = resolved.host?.hostAddress ?: return
                val port = resolved.port
                val endpoint = GatewayEndpoint(
                    name = resolved.serviceName,
                    host = host,
                    port = port,
                    online = true
                )
                AgentStateStore.addLog("Found Brahma Gateway at $host:$port")
                onFound(endpoint)
            }
        }

        try {
            manager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            AgentStateStore.addLog("Resolve exception: ${e.message}")
        }
    }

    fun stop() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (_: Exception) {}
            discoveryListener = null
        }
    }
}
