/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
internal fun NsdManager.serviceList(serviceType: String): Flow<List<NsdServiceInfo>> =
    discoverServices(serviceType).mapLatest { serviceList -> serviceList.mapNotNull { resolveService(it) } }

private fun NsdManager.discoverServices(
    serviceType: String,
    protocolType: Int = NsdManager.PROTOCOL_DNS_SD,
): Flow<List<NsdServiceInfo>> = callbackFlow {
    val serviceList = CopyOnWriteArrayList<NsdServiceInfo>()
    val discoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                cancel("Start Discovery failed: Error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                cancel("Stop Discovery failed: Error code: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Timber.d("NSD Service discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("NSD Service discovery stopped")
                close()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Timber.d("NSD Service found: $serviceInfo")
                serviceList += serviceInfo
                trySend(serviceList)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Timber.d("NSD Service lost: $serviceInfo")
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

@SuppressLint("NewApi")
private suspend fun NsdManager.resolveService(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
    suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback =
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        continuation.resume(null)
                    }

                    override fun onServiceUpdated(updatedServiceInfo: NsdServiceInfo) {
                        if (updatedServiceInfo.hostAddresses.isNotEmpty()) {
                            continuation.resume(updatedServiceInfo)
                            try {
                                unregisterServiceInfoCallback(this)
                            } catch (e: IllegalArgumentException) {
                                Timber.w(e, "Already unregistered")
                            }
                        }
                    }

                    override fun onServiceLost() {
                        continuation.resume(null)
                        try {
                            unregisterServiceInfoCallback(this)
                        } catch (e: IllegalArgumentException) {
                            Timber.w(e, "Already unregistered")
                        }
                    }

                    override fun onServiceInfoCallbackUnregistered() {
                        // No op
                    }
                }
            registerServiceInfoCallback(serviceInfo, Dispatchers.Main.asExecutor(), callback)
            continuation.invokeOnCancellation {
                try {
                    unregisterServiceInfoCallback(callback)
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Already unregistered")
                }
            }
        } else {
            val listener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        continuation.resume(serviceInfo)
                    }
                }
            @Suppress("DEPRECATION")
            resolveService(serviceInfo, listener)
        }
    }
