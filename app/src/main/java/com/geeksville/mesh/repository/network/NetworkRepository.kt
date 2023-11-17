package com.geeksville.mesh.repository.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    private val nsdManagerLazy: dagger.Lazy<NsdManager?>,
    private val connectivityManager: dagger.Lazy<ConnectivityManager>,
) : Logging {

    private val availableNetworks: HashSet<Network> = HashSet()
    val networkAvailable: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                trySend(availableNetworks.isNotEmpty()).isSuccess
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                trySend(availableNetworks.isNotEmpty()).isSuccess
            }
        }
        val networkRequest = NetworkRequest.Builder().build()
        connectivityManager.get().registerNetworkCallback(networkRequest, callback)
        awaitClose { connectivityManager.get().unregisterNetworkCallback(callback) }
    }

    private val _resolvedList = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val resolvedList: StateFlow<List<NsdServiceInfo>> get() = _resolvedList

    private val _networkDiscovery: Flow<NsdServiceInfo> = callbackFlow {
        val resolveQueue = Semaphore(1)
        val hostsList = mutableListOf<NsdServiceInfo>()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                debug("Service discovery started: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                debug("Service discovery success: $service")
                if (service.serviceName.contains(SERVICE_NAME)) {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            debug("Resolve Succeeded: $service")
                            hostsList.add(service)
                            _resolvedList.value = hostsList
                            trySend(service)
                        }

                        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                            debug("Resolve failed: $service - Error code: $errorCode")
                        }
                    }
                    // one resolveService at a time to avoid: Error Code 3: Failure Already active
                    launch {
                        try {
                            resolveQueue.acquire()
                            nsdManagerLazy.get()?.resolveService(service, resolveListener)
                        } finally {
                            resolveQueue.release()
                        }
                    }
                } else {
                    debug("Not our Service - Name: ${service.serviceName}, Type: ${service.serviceType}")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                debug("Service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                debug("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                debug("Start Discovery failed: Error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                debug("Stop Discovery failed: Error code: $errorCode")
            }
        }
        nsdManagerLazy.get()
            ?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { nsdManagerLazy.get()?.stopServiceDiscovery(discoveryListener) }
    }

    fun networkDiscoveryFlow(): Flow<NsdServiceInfo> {
        return _networkDiscovery
    }

    companion object {
        // To find all available services use SERVICE_TYPE = "_services._dns-sd._udp"
        internal const val SERVICE_NAME = "Meshtastic"
        internal const val SERVICE_TYPE = "_https._tcp."
    }
}
