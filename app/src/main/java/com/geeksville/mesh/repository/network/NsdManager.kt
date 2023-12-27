package com.geeksville.mesh.repository.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun NsdManager.discoverServices(
    serviceType: String,
    protocolType: Int = NsdManager.PROTOCOL_DNS_SD,
): Flow<List<NsdServiceInfo>> = callbackFlow {
    val serviceList = mutableListOf<NsdServiceInfo>()
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
    discoverServices(serviceType, protocolType, discoveryListener)
    awaitClose { stopServiceDiscovery(discoveryListener) }
}

internal suspend fun NsdManager.resolveService(
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
