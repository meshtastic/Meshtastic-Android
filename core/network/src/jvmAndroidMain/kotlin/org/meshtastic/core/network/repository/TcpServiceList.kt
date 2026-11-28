/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.network.repository
package org.meshtastic.core.network.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import org.meshtastic.sdk.transport.tcp.DiscoveredTcpRadio
import org.meshtastic.sdk.transport.tcp.TcpDiscoveryEvent

/**
 * The radios a [org.meshtastic.sdk.transport.tcp.TcpDiscovery] has resolved and not lost, starting with an empty list so a
 * `combine` over this flow is not held back. A browse that cannot start is logged and ends the flow instead of failing the
 * shared collector.
 */
internal fun Flow<TcpDiscoveryEvent>.toServiceList(): Flow<List<DiscoveredService>> =
    scan(emptyMap<String, DiscoveredTcpRadio>()) { known, event ->
        when (event) {
            is TcpDiscoveryEvent.Found -> known + (event.radio.serviceName to event.radio)
            is TcpDiscoveryEvent.Lost -> known - event.serviceName
        }
    }
        .map { known -> known.values.map { it.toDiscoveredService() } }
        .catch { Logger.w(it) { "mDNS discovery stopped" } }

private fun DiscoveredTcpRadio.toDiscoveredService() = DiscoveredService(
    name = serviceName,
    hostAddress = host,
    port = port,
    txt = attributes.mapValues { it.value.encodeToByteArray() },
)
