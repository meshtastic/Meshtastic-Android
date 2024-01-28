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
