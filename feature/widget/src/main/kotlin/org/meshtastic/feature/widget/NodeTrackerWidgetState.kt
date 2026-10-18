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
package org.meshtastic.feature.widget

import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.NodeRepository

/** What one Node Tracker instance renders. */
sealed interface NodeTrackerWidgetState {
    /** No node chosen yet, or the chosen one has left the node DB. Tapping reopens the picker. */
    data object NodeNotFound : NodeTrackerWidgetState

    data class Tracking(
        val nodeNum: Int,
        val shortName: String,
        val longName: String,
        val colors: Pair<Int, Int>,
        val batteryLevel: Int?,
        val snr: Float?,
        val rssi: Int?,
        val hopsAway: Int?,
        val lastHeard: Int,
        val distance: String?,
        val isStale: Boolean,
    ) : NodeTrackerWidgetState
}

/** Everything a tracker needs besides its own node number, shared by every instance. */
data class NodeTrackerInputs(
    val nodes: Map<Int, Node> = emptyMap(),
    val ourNode: Node? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val units: MeasurementSystem = MeasurementSystem.METRIC,
)

/** Per-instance Glance state key holding the tracked node number. */
internal val TRACKED_NODE_NUM_KEY = intPreferencesKey("tracked_node_num")

internal fun nodeTrackerState(
    nodeNum: Int?,
    inputs: NodeTrackerInputs,
    onlineThreshold: Int = onlineTimeThreshold(),
): NodeTrackerWidgetState {
    val node = nodeNum?.let { inputs.nodes[it] } ?: return NodeTrackerWidgetState.NodeNotFound
    val direct = node.hopsAway == 0 && !node.viaMqtt
    // A disconnected radio's last position would read as live, so distance is shown only while connected.
    val distance =
        inputs.ourNode
            ?.takeIf { inputs.connectionState is ConnectionState.Connected && it.num != node.num }
            ?.let { node.distance(it) }
            ?.takeIf { it > 0 }
            ?.toDistanceString(inputs.units)
    return NodeTrackerWidgetState.Tracking(
        nodeNum = node.num,
        shortName = node.user.short_name.ifEmpty { "???" },
        longName = node.user.long_name.ifEmpty { NodeAddress.numToDefaultId(node.num) },
        colors = node.colors,
        batteryLevel = node.batteryLevel?.takeIf { it > 0 },
        snr = if (direct) node.snrOrNull else null,
        rssi = if (direct) node.rssiOrNull else null,
        hopsAway = node.hopsAway.takeIf { it >= 0 },
        lastHeard = node.lastHeard,
        distance = distance,
        isStale = node.lastHeard <= onlineThreshold,
    )
}

@Single
class NodeTrackerWidgetDataSource(
    private val nodeRepository: NodeRepository,
    connectionStateProvider: ConnectionStateProvider,
    localeUnitsProvider: LocaleUnitsProvider,
    scope: ApplicationCoroutineScope,
) {
    val inputs: StateFlow<NodeTrackerInputs> =
        combine(
            nodeRepository.nodeDBbyNum,
            nodeRepository.ourNodeInfo,
            connectionStateProvider.connectionState,
            localeUnitsProvider.measurementSystem,
        ) { nodes, ourNode, connectionState, units ->
            NodeTrackerInputs(nodes, ourNode, connectionState, units)
        }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = NodeTrackerInputs())

    /**
     * The current inputs, reading the database directly while the repository's flow still holds its empty initial value
     * after a cold process start; otherwise a widget rendered then would claim its node was not found.
     */
    suspend fun currentInputs(): NodeTrackerInputs {
        val current = inputs.value
        return if (current.nodes.isEmpty()) current.copy(nodes = nodeRepository.getNodeDbSnapshot()) else current
    }
}
