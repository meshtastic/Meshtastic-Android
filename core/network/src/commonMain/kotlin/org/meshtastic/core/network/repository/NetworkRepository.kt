package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.Flow

interface NetworkRepository {
    val networkAvailable: Flow<Boolean>
    val resolvedList: Flow<List<DiscoveredService>>

    companion object {
        fun DiscoveredService.toAddressString() = buildString {
            append(hostAddress)
            if (port != NetworkConstants.SERVICE_PORT) {
                append(":$port")
            }
        }
    }
}
