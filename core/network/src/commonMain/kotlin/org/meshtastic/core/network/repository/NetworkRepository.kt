package org.meshtastic.core.network.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class NetworkRepository(
    networkMonitor: NetworkMonitor,
    serviceDiscovery: ServiceDiscovery,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) {

    val networkAvailable: Flow<Boolean> by lazy {
        networkMonitor
            .networkAvailable
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
            .distinctUntilChanged()
    }

    val resolvedList: Flow<List<DiscoveredService>> by lazy {
        serviceDiscovery
            .resolvedServices
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
    }

    companion object {
        fun DiscoveredService.toAddressString() = buildString {
            append(hostAddress)
            if (port != NetworkConstants.SERVICE_PORT) {
                append(":$port")
            }
        }
    }
}
