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
package org.meshtastic.app.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.NodeId

/**
 * POC ViewModel that surfaces per-node telemetry from [TelemetryApi.observe].
 *
 * **Gap D verified:** [TelemetryApi.observe] returns a plain [kotlinx.coroutines.flow.Flow]
 * of unsolicited periodic [Telemetry] packets (device metrics, environment metrics, etc.).
 * It does NOT auto-poll — packets arrive only when the radio pushes them.
 * To request an immediate telemetry update, call [requestDeviceMetrics] which issues an RPC.
 *
 * Telemetry fields are nullable (Wire proto) — check per-field before display:
 *   [Telemetry.device_metrics], [Telemetry.environment_metrics],
 *   [Telemetry.air_quality_metrics], [Telemetry.power_metrics]
 *
 * Usage: observe [deviceMetrics] / [environmentMetrics] in a node-detail Composable,
 * call [requestDeviceMetrics] on screen entry to prime the display.
 */
@KoinViewModel
class SdkTelemetryViewModel(
    private val provider: RadioClientProvider,
) : ViewModel() {

    /**
     * Observe all raw [Telemetry] packets for [nodeId].
     *
     * Re-subscribes automatically when [RadioClientProvider.client] changes (reconnect).
     * Errors are caught and logged — the flow resets to null rather than crashing.
     */
    private fun telemetryFor(nodeId: NodeId): StateFlow<Telemetry?> =
        provider.client
            .flatMapLatest { c ->
                if (c == null) flowOf(null)
                else c.telemetry.observe(nodeId)
                    .catch { e ->
                        Logger.e(e) { "[SDK] telemetry.observe(${nodeId.raw}) error" }
                        emit(Telemetry())
                    }
                    .map { it as Telemetry? }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Latest telemetry (any type) for the local node (NodeId.LOCAL). */
    val localTelemetry: StateFlow<Telemetry?> = telemetryFor(NodeId.LOCAL)

    /**
     * Request an immediate device-metrics telemetry packet from [nodeId].
     * The result will be pushed back through [telemetryFor]'s [TelemetryApi.observe] flow.
     */
    fun requestDeviceMetrics(nodeId: NodeId = NodeId.LOCAL) {
        val client = provider.client.value ?: return
        viewModelScope.launch {
            when (val r = client.telemetry.requestDevice(nodeId)) {
                is AdminResult.Success ->
                    Logger.d { "[SDK] requestDeviceMetrics(${nodeId.raw}): ${r.value}" }
                else -> Logger.w { "[SDK] requestDeviceMetrics(${nodeId.raw}) failed: $r" }
            }
        }
    }

    /**
     * Build a per-node telemetry StateFlow for a specific node num.
     * Compose screens can call this once per node-detail screen.
     */
    fun observeNode(nodeNum: Int): StateFlow<Telemetry?> = telemetryFor(NodeId(nodeNum))
}
