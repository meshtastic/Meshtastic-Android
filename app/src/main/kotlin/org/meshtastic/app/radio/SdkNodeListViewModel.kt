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
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId

/**
 * Stable, Compose-safe representation of a mesh node.
 *
 * Wire-generated [NodeInfo] is NOT `@Stable`; never pass it directly to Compose. This wrapper holds only the
 * primitive/String fields the node list UI needs.
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
)

private fun NodeInfo.toUiNode() = UiNode(
    num = num,
    longName = user?.long_name.orEmpty(),
    shortName = user?.short_name.orEmpty(),
    snr = snr,
    hopsAway = hops_away,
    lastHeard = last_heard,
    viaMqtt = via_mqtt,
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
                    .map { nodeMap -> nodeMap.values.map(NodeInfo::toUiNode) }
                    .flowOn(Dispatchers.Default)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
