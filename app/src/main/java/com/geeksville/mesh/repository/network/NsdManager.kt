/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.repository.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

internal fun NsdManager.serviceList(
    serviceTypes: List<String>,
    serviceName: String,
): Flow<List<NsdServiceInfo>> {
    val flows = serviceTypes.map { serviceType -> serviceList(serviceType, serviceName) }
    return combine(flows) { lists -> lists.flatMap { it }.distinctBy { it.serviceName } }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun NsdManager.serviceList(
    serviceType: String,
    serviceName: String,
): Flow<List<NsdServiceInfo>> = discoverServices(serviceType).mapLatest { serviceList ->
    serviceList
        .filter { it.serviceName.contains(serviceName) }
        .mapNotNull { resolveService(it) }
}

private fun NsdManager.discoverServices(
    serviceType: String,
    protocolType: Int = NsdManager.PROTOCOL_DNS_SD,
): Flow<List<NsdServiceInfo>> = callbackFlow {
    val serviceList = CopyOnWriteArrayList<NsdServiceInfo>()
    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            cancel("Start Discovery failed: Error code: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            cancel("Stop Discovery failed: Error code: $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String) {
        }

        override fun onDiscoveryStopped(serviceType: String) {
            close()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            serviceList += serviceInfo
            trySend(serviceList)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            serviceList.removeAll { it.serviceName == serviceInfo.serviceName }
            trySend(serviceList)
        }
    }
    trySend(emptyList()) // Emit an initial empty list
    discoverServices(serviceType, protocolType, discoveryListener)

    awaitClose {
        try {
            stopServiceDiscovery(discoveryListener)
        } catch (ex: IllegalArgumentException) {
            // ignore if discovery is already stopped
        }
    }
}

private suspend fun NsdManager.resolveService(
    serviceInfo: NsdServiceInfo,
): NsdServiceInfo? = suspendCancellableCoroutine { continuation ->
    val listener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            continuation.resume(null)
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            continuation.resume(serviceInfo)
        }
    }
    resolveService(serviceInfo, listener)
}
