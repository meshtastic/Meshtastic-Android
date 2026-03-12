/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.connections.repository

import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
    private val nsdManager: NsdManager,
    private val connectivityManager: ConnectivityManager,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) {

    val networkAvailable: Flow<Boolean> by lazy {
        connectivityManager
            .networkAvailable()
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
            .distinctUntilChanged()
    }

    val resolvedList: Flow<List<NsdServiceInfo>> by lazy {
        nsdManager
            .serviceList(NetworkConstants.SERVICE_TYPE)
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
    }

    companion object {

        fun NsdServiceInfo.toAddressString() = buildString {
            @Suppress("DEPRECATION")
            append(host.hostAddress)
            if (serviceType.trim('.') == NetworkConstants.SERVICE_TYPE && port != NetworkConstants.SERVICE_PORT) {
                append(":$port")
            }
        }
    }
}
