package com.geeksville.mesh.repository.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun ConnectivityManager.networkAvailable(): Flow<Boolean> = callbackFlow {
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(allNetworks.isNotEmpty())
        }

        override fun onLost(network: Network) {
            trySend(allNetworks.isNotEmpty())
        }
    }
    val networkRequest = NetworkRequest.Builder().build()
    registerNetworkCallback(networkRequest, callback)

    awaitClose { unregisterNetworkCallback(callback) }
}
