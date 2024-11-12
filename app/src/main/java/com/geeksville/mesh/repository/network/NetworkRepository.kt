package com.geeksville.mesh.repository.network

import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    private val nsdManagerLazy: dagger.Lazy<NsdManager?>,
    private val connectivityManager: dagger.Lazy<ConnectivityManager>,
) : Logging {

    val networkAvailable get() = connectivityManager.get().networkAvailable()

    val resolvedList
        get() = nsdManagerLazy.get()?.serviceList(SERVICE_TYPES, SERVICE_NAME) ?: flowOf(emptyList())

    companion object {
        // To find all available services use SERVICE_TYPE = "_services._dns-sd._udp"
        internal const val SERVICE_NAME = "Meshtastic"
        internal val SERVICE_TYPES = listOf("_http._tcp.", "_meshtastic._tcp.")
    }
}
