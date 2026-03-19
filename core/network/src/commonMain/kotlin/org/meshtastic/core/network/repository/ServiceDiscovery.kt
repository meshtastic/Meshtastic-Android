package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.Flow

interface ServiceDiscovery {
    val resolvedServices: Flow<List<DiscoveredService>>
}
