package com.geeksville.mesh.repository.network

import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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

    val networkAvailable get() = connectivityManager.get().networkAvailable()

    private val _resolvedList = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val resolvedList: StateFlow<List<NsdServiceInfo>> get() = _resolvedList

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            val manager = nsdManagerLazy.get() ?: return@launch
            manager.discoverServices(SERVICE_TYPE).collectLatest { serviceList ->
                _resolvedList.update {
                    serviceList
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
