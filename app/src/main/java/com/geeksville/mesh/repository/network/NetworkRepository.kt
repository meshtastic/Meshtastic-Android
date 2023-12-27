package com.geeksville.mesh.repository.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    dispatchers: CoroutineDispatchers,
    processLifecycle: Lifecycle,
    private val nsdManagerLazy: dagger.Lazy<NsdManager?>,
    private val connectivityManager: dagger.Lazy<ConnectivityManager>,
) : Logging {

    private val availableNetworks: HashSet<Network> = HashSet()
    val networkAvailable: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                trySend(availableNetworks.isNotEmpty())
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                trySend(availableNetworks.isNotEmpty())
            }
        }
        val networkRequest = NetworkRequest.Builder().build()
        connectivityManager.get().registerNetworkCallback(networkRequest, callback)
        awaitClose { connectivityManager.get().unregisterNetworkCallback(callback) }
    }

    private val _resolvedList = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val resolvedList: StateFlow<List<NsdServiceInfo>> get() = _resolvedList

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            nsdManagerLazy.get()?.let { manager ->
                manager.discoverServices(SERVICE_TYPE).collectLatest { serviceList ->
                    _resolvedList.value = serviceList
                        .filter { it.serviceName.contains(SERVICE_NAME) }
                        .mapNotNull { manager.resolveService(it) }
                }
            }
        }
    }

    companion object {
        // To find all available services use SERVICE_TYPE = "_services._dns-sd._udp"
        internal const val SERVICE_NAME = "Meshtastic"
        internal const val SERVICE_TYPE = "_https._tcp."
    }
}
