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

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun ConnectivityManager.networkAvailable(): Flow<Boolean> = observeNetworks()
        .map { activeNetworksList -> activeNetworksList.isNotEmpty() }
        .distinctUntilChanged()

internal fun ConnectivityManager.observeNetworks(
    networkRequest: NetworkRequest = NetworkRequest.Builder().build(),
): Flow<List<Network>> = callbackFlow {
    // Keep track of the current active networks
    val activeNetworks = mutableSetOf<Network>()

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activeNetworks.add(network)
            trySend(activeNetworks.toList())
        }

        override fun onLost(network: Network) {
            activeNetworks.remove(network)
            trySend(activeNetworks.toList())
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (activeNetworks.contains(network)) {
                trySend(activeNetworks.toList())
            }
        }
    }

    registerNetworkCallback(networkRequest, callback)

    awaitClose {
        unregisterNetworkCallback(callback)
    }
}
