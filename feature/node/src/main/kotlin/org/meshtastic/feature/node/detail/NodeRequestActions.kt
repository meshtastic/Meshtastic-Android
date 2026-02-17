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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.neighbor_info
import org.meshtastic.core.strings.position
import org.meshtastic.core.strings.request_air_quality_metrics
import org.meshtastic.core.strings.request_device_metrics
import org.meshtastic.core.strings.request_environment_metrics
import org.meshtastic.core.strings.request_host_metrics
import org.meshtastic.core.strings.request_pax_metrics
import org.meshtastic.core.strings.request_power_metrics
import org.meshtastic.core.strings.requesting_from
import org.meshtastic.core.strings.signal_quality
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.strings.user_info
import javax.inject.Inject
import javax.inject.Singleton

sealed class NodeRequestEffect {
    data class ShowFeedback(val resource: StringResource, val args: List<Any> = emptyList()) : NodeRequestEffect()
}

@Singleton
class NodeRequestActions @Inject constructor(private val serviceRepository: ServiceRepository) {

    private val _effects = MutableSharedFlow<NodeRequestEffect>()
    val effects: SharedFlow<NodeRequestEffect> = _effects.asSharedFlow()

    private val _lastTracerouteTimes = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val lastTracerouteTimes: StateFlow<Map<Int, Long>> = _lastTracerouteTimes.asStateFlow()

    private val _lastRequestNeighborTimes = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val lastRequestNeighborTimes: StateFlow<Map<Int, Long>> = _lastRequestNeighborTimes.asStateFlow()

    fun requestUserInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting UserInfo for '$destNum'" }
            try {
                serviceRepository.meshService?.requestUserInfo(destNum)
                _effects.emit(
                    NodeRequestEffect.ShowFeedback(Res.string.requesting_from, listOf(Res.string.user_info, longName)),
                )
            } catch (ex: android.os.RemoteException) {
                Logger.e { "Request NodeInfo error: ${ex.message}" }
            }
        }
    }

    fun requestNeighborInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting NeighborInfo for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestNeighborInfo(packetId, destNum)
                _lastRequestNeighborTimes.update { it + (destNum to nowMillis) }
                _effects.emit(
                    NodeRequestEffect.ShowFeedback(
                        Res.string.requesting_from,
                        listOf(Res.string.neighbor_info, longName),
                    ),
                )
            } catch (ex: android.os.RemoteException) {
                Logger.e { "Request NeighborInfo error: ${ex.message}" }
            }
        }
    }

    fun requestPosition(
        scope: CoroutineScope,
        destNum: Int,
        longName: String,
        position: Position = Position(0.0, 0.0, 0),
    ) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting position for '$destNum'" }
            try {
                serviceRepository.meshService?.requestPosition(destNum, position)
                _effects.emit(
                    NodeRequestEffect.ShowFeedback(Res.string.requesting_from, listOf(Res.string.position, longName)),
                )
            } catch (ex: android.os.RemoteException) {
                Logger.e { "Request position error: ${ex.message}" }
            }
        }
    }

    fun requestTelemetry(scope: CoroutineScope, destNum: Int, longName: String, type: TelemetryType) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting telemetry for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestTelemetry(packetId, destNum, type.ordinal)

                val typeRes =
                    when (type) {
                        TelemetryType.DEVICE -> Res.string.request_device_metrics
                        TelemetryType.ENVIRONMENT -> Res.string.request_environment_metrics
                        TelemetryType.AIR_QUALITY -> Res.string.request_air_quality_metrics
                        TelemetryType.POWER -> Res.string.request_power_metrics
                        TelemetryType.LOCAL_STATS -> Res.string.signal_quality
                        TelemetryType.HOST -> Res.string.request_host_metrics
                        TelemetryType.PAX -> Res.string.request_pax_metrics
                    }

                _effects.emit(NodeRequestEffect.ShowFeedback(Res.string.requesting_from, listOf(typeRes, longName)))
            } catch (ex: android.os.RemoteException) {
                Logger.e { "Request telemetry error: ${ex.message}" }
            }
        }
    }

    fun requestTraceroute(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting traceroute for '$destNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.requestTraceroute(packetId, destNum)
                _lastTracerouteTimes.update { it + (destNum to nowMillis) }
                _effects.emit(
                    NodeRequestEffect.ShowFeedback(Res.string.requesting_from, listOf(Res.string.traceroute, longName)),
                )
            } catch (ex: android.os.RemoteException) {
                Logger.e { "Request traceroute error: ${ex.message}" }
            }
        }
    }
}
