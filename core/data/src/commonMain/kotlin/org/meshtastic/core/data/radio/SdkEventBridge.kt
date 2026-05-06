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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.sdk.MeshEvent

internal class SdkEventBridge(
    private val serviceRepository: ServiceRepository,
) {
    fun observe(accessor: RadioClientAccessor, scope: CoroutineScope) {
        accessor.client
            .flatMapLatest { client -> client?.events ?: emptyFlow() }
            .onEach(::handleEvent)
            .launchIn(scope)
    }

    internal fun handleEvent(event: MeshEvent) {
        when (event) {
            is MeshEvent.DeviceRebooted -> {
                Logger.i { "[SdkBridge] Device rebooted" }
                serviceRepository.setClientNotification(
                    ClientNotification(message = "Device rebooted"),
                )
            }

            is MeshEvent.CongestionWarning -> {
                Logger.w {
                    "[SdkBridge] Congestion warning: level=${event.metrics.level}, airUtil=${event.metrics.airUtilTx}%, channelUtil=${event.metrics.channelUtil}%"
                }
                serviceRepository.setCongestionLevel(event.metrics.level)
            }

            is MeshEvent.SecurityWarning -> Logger.w { "[SdkBridge] Security warning: $event" }
            is MeshEvent.PacketsDropped -> Logger.w { "[SdkBridge] Packets dropped: ${event.count} from ${event.flow}" }
            else -> Logger.d { "[SdkBridge] Event: $event" }
        }
    }
}
