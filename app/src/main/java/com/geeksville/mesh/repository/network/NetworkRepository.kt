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
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    private val nsdManagerLazy: dagger.Lazy<NsdManager>,
    private val connectivityManager: dagger.Lazy<ConnectivityManager>,
    private val dispatchers: CoroutineDispatchers,
) : Logging {

    val networkAvailable: Flow<Boolean>
        get() = connectivityManager.get().networkAvailable()
            .flowOn(dispatchers.io)
            .conflate()

    val resolvedList: Flow<List<NsdServiceInfo>>
        get() = nsdManagerLazy.get().serviceList(SERVICE_TYPES, SERVICE_NAME)
            .flowOn(dispatchers.io)
            .conflate()

    companion object {
        // To find all available services use SERVICE_TYPE = "_services._dns-sd._udp"
        internal const val SERVICE_NAME = "Meshtastic"
        internal val SERVICE_TYPES = listOf("_http._tcp.", "_meshtastic._tcp.")
    }
}
