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

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun ConnectivityManager.networkAvailable(): Flow<Boolean> =
    allNetworks().map { it.isNotEmpty() }.distinctUntilChanged()

internal fun ConnectivityManager.allNetworks(
    networkRequest: NetworkRequest = NetworkRequest.Builder().build(),
): Flow<Array<Network>> = callbackFlow {
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(allNetworks)
        }

        override fun onLost(network: Network) {
            trySend(allNetworks)
        }
    }
    registerNetworkCallback(networkRequest, callback)

    awaitClose { unregisterNetworkCallback(callback) }
}
