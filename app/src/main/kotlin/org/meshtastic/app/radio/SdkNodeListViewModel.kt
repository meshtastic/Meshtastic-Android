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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.NodeInfo
import org.meshtastic.sdk.ConnectionQuality
import org.meshtastic.sdk.MeshNode
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.SignalQuality
import org.meshtastic.sdk.toMeshNode

/**
 * Stable, Compose-safe representation of a mesh node.
 *
 * Wire-generated [NodeInfo] is NOT `@Stable`; never pass it directly to Compose. This wrapper holds the
 * fields the node list UI needs for display, filtering, and sorting.
 */
@Immutable
data class UiNode(
    val num: Int,
    val longName: String,
    val shortName: String,
    val snr: Float,
    val hopsAway: Int?,
    val lastHeard: Int,
    val viaMqtt: Boolean,
    // Enriched fields
    val isOnline: Boolean,
    val connectionQuality: ConnectionQuality,
    val signalQuality: SignalQuality,
    val batteryLevel: Int?,
    val voltage: Float?,
    val channelUtilization: Float?,
    val airUtilTx: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Int?,
    val isFavorite: Boolean,
    val isIgnored: Boolean,
    val isMuted: Boolean,
    val hwModel: String?,
)

private fun MeshNode.toUiNode() = UiNode(
    num = nodeNum,
    longName = longName ?: "Unknown",
    shortName = shortName ?: "?",
    snr = snr,
    hopsAway = hopsAway,
    lastHeard = lastHeard,
    viaMqtt = viaMqtt,
    isOnline = isOnline,
    connectionQuality = connectionQuality,
    signalQuality = signalQuality,
    batteryLevel = batteryLevel,
    voltage = voltage,
    channelUtilization = channelUtilization,
    airUtilTx = airUtilTx,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    isFavorite = isFavorite,
    isIgnored = isIgnored,
    isMuted = isMuted,
    hwModel = hwModel?.name,
)

/**
 * POC ViewModel that drives a node list directly from the SDK's [org.meshtastic.sdk.RadioClient].
 *
 * **Fold pattern:**
 * 1. `flatMapLatest` switches to the new client's `nodes` flow whenever [RadioClientProvider] replaces the active
 *    client.
 * 2. `.catch {}` before `.scan {}` so that a transport error re-emits a safe [NodeChange.Snapshot] (empty map) rather
 *    than terminating the downstream scan accumulator.
 * 3. `.scan {}` folds delta events — [NodeChange.Added], [NodeChange.Updated], [NodeChange.Removed] — onto the
 *    accumulator map. The initial [NodeChange.Snapshot] is guaranteed by the SDK for every new subscriber; no explicit
 *    replay config needed.
 * 4. `.flowOn(Dispatchers.Default)` keeps folding off the main thread.
 * 5. `.stateIn(WhileSubscribed(5_000))` keeps the upstream alive for 5 s after the last subscriber (safe across config
 *    changes; SDK re-sends a Snapshot for later subscribers).
 *
 * This ViewModel is registered as a Koin singleton alongside [RadioClientViewModel]. Both are instantiated at
 * [org.meshtastic.app.ui.MainScreen] startup so the node map is warm before any screen subscribes.
 */
@KoinViewModel
class SdkNodeListViewModel(provider: RadioClientProvider) : ViewModel() {

    val nodes: StateFlow<List<UiNode>> =
        provider.client
            .flatMapLatest { client ->
                if (client == null) return@flatMapLatest flowOf(emptyList())
                client.nodes
                    .catch { e ->
                        Logger.e(e) { "[SDK] nodes flow error — resetting to empty" }
                        emit(NodeChange.Snapshot(emptyMap()))
                    }
                    .scan(emptyMap<NodeId, NodeInfo>()) { acc, change ->
                        when (change) {
                            is NodeChange.Snapshot -> change.nodes
                            is NodeChange.Added -> acc + (NodeId(change.node.num) to change.node)
                            is NodeChange.Updated -> acc + (NodeId(change.node.num) to change.node)
                            is NodeChange.Removed -> acc - change.nodeId
                        }
                    }
                    .map { nodeMap ->
                        val now = (System.currentTimeMillis() / 1000).toInt()
                        nodeMap.values.map { it.toMeshNode(now).toUiNode() }
                    }
                    .flowOn(Dispatchers.Default)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
