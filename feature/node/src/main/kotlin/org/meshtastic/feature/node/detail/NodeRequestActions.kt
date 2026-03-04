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
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.request_air_quality_metrics
import org.meshtastic.core.resources.request_device_metrics
import org.meshtastic.core.resources.request_environment_metrics
import org.meshtastic.core.resources.request_host_metrics
import org.meshtastic.core.resources.request_pax_metrics
import org.meshtastic.core.resources.request_power_metrics
import org.meshtastic.core.resources.requesting_from
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.traceroute
import org.meshtastic.core.resources.user_info
import javax.inject.Inject
import javax.inject.Singleton

sealed class NodeRequestEffect {
    data class ShowFeedback(val text: UiText) : NodeRequestEffect()
}

@Singleton
class NodeRequestActions @Inject constructor(private val radioController: RadioController) {

    private val _effects = MutableSharedFlow<NodeRequestEffect>()
    val effects: SharedFlow<NodeRequestEffect> = _effects.asSharedFlow()

    private val _lastTracerouteTime = MutableStateFlow<Long?>(null)
    val lastTracerouteTime: StateFlow<Long?> = _lastTracerouteTime.asStateFlow()

    private val _lastRequestNeighborTimes = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val lastRequestNeighborTimes: StateFlow<Map<Int, Long>> = _lastRequestNeighborTimes.asStateFlow()

    fun requestUserInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting UserInfo for '$destNum'" }
            radioController.requestUserInfo(destNum)
            _effects.emit(
                NodeRequestEffect.ShowFeedback(
                    UiText.Resource(Res.string.requesting_from, Res.string.user_info, longName),
                ),
            )
        }
    }

    fun requestNeighborInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting NeighborInfo for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestNeighborInfo(packetId, destNum)
            _lastRequestNeighborTimes.update { it + (destNum to nowMillis) }
            _effects.emit(
                NodeRequestEffect.ShowFeedback(
                    UiText.Resource(Res.string.requesting_from, Res.string.neighbor_info, longName),
                ),
            )
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
            radioController.requestPosition(destNum, position)
            _effects.emit(
                NodeRequestEffect.ShowFeedback(
                    UiText.Resource(Res.string.requesting_from, Res.string.position, longName),
                ),
            )
        }
    }

    fun requestTelemetry(scope: CoroutineScope, destNum: Int, longName: String, type: TelemetryType) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting telemetry for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestTelemetry(packetId, destNum, type.ordinal)

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

            _effects.emit(
                NodeRequestEffect.ShowFeedback(UiText.Resource(Res.string.requesting_from, typeRes, longName)),
            )
        }
    }

    fun requestTraceroute(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Requesting traceroute for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestTraceroute(packetId, destNum)
            _lastTracerouteTime.value = nowMillis
            _effects.emit(
                NodeRequestEffect.ShowFeedback(
                    UiText.Resource(Res.string.requesting_from, Res.string.traceroute, longName),
                ),
            )
        }
    }
}
