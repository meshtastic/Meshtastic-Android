package org.meshtastic.core.network.repository

import android.net.nsd.NsdManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class AndroidServiceDiscovery(
    private val nsdManager: NsdManager,
) : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> =
        nsdManager.serviceList(NetworkConstants.SERVICE_TYPE).map { list ->
            list.map { info ->
                val txtMap = mutableMapOf<String, ByteArray>()
                info.attributes.forEach { (key, value) ->
                    txtMap[key] = value
                }
                @Suppress("DEPRECATION")
                DiscoveredService(
                    name = info.serviceName,
                    hostAddress = info.host?.hostAddress ?: "",
                    port = info.port,
                    txt = txtMap,
                )
            }
        }
}
