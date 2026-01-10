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
package org.meshtastic.feature.node.detail

import android.os.RemoteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRequestActions @Inject constructor(private val serviceRepository: ServiceRepository) {
    private var scope: CoroutineScope? = null

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun requestUserInfo(destNum: Int) {
        scope?.launch(Dispatchers.IO) {
            Logger.i { "Requesting UserInfo for '$destNum'" }
            try {
                serviceRepository.meshService?.requestUserInfo(destNum)
            } catch (ex: RemoteException) {
                Logger.e { "Request NodeInfo error: ${ex.message}" }
            }
        }
    }

    fun requestNeighborInfo(destNum: Int) {
        scope?.launch(Dispatchers.IO) {
            Logger.i { "Requesting NeighborInfo for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestNeighborInfo(packetId, destNum)
            } catch (ex: RemoteException) {
                Logger.e { "Request NeighborInfo error: ${ex.message}" }
            }
        }
    }

    fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        scope?.launch(Dispatchers.IO) {
            Logger.i { "Requesting position for '$destNum'" }
            try {
                serviceRepository.meshService?.requestPosition(destNum, position)
            } catch (ex: RemoteException) {
                Logger.e { "Request position error: ${ex.message}" }
            }
        }
    }

    fun requestTelemetry(destNum: Int, type: TelemetryType) {
        scope?.launch(Dispatchers.IO) {
            Logger.i { "Requesting telemetry for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestTelemetry(packetId, destNum, type.ordinal)
            } catch (ex: RemoteException) {
                Logger.e { "Request telemetry error: ${ex.message}" }
            }
        }
    }

    fun requestTraceroute(destNum: Int) {
        scope?.launch(Dispatchers.IO) {
            Logger.i { "Requesting traceroute for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestTraceroute(packetId, destNum)
            } catch (ex: RemoteException) {
                Logger.e { "Request traceroute error: ${ex.message}" }
            }
        }
    }
}
