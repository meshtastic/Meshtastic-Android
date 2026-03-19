package org.meshtastic.core.network.repository

data class DiscoveredService(
    val name: String,
    val hostAddress: String,
    val port: Int,
    val txt: Map<String, ByteArray> = emptyMap(),
)
