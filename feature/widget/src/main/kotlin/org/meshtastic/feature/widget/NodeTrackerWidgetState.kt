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

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.NodeRepository

/** What one Node Tracker instance renders. */
sealed interface NodeTrackerWidgetState {
    /** No node chosen yet, or the chosen one is missing from a populated node DB. Tapping reopens the picker. */
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
    lastKnown: NodeTrackerWidgetState.Tracking? = null,
): NodeTrackerWidgetState {
    val node = nodeNum?.let { inputs.nodes[it] }
    return when {
        node != null -> trackingOf(node, inputs, onlineThreshold)

        // An empty node DB means no radio's DB is open (disconnected, or no device selected), not that the node left.
        nodeNum != null && inputs.nodes.isEmpty() ->
            lastKnown?.takeIf { it.nodeNum == nodeNum }?.asLastKnown() ?: NodeTrackerWidgetState.NodeNotFound

        else -> NodeTrackerWidgetState.NodeNotFound
    }
}

/** How [this] reading renders once no node DB backs it: dimmed, and without a distance from a radio now gone. */
internal fun NodeTrackerWidgetState.Tracking.asLastKnown() = copy(distance = null, isStale = true)

private fun trackingOf(node: Node, inputs: NodeTrackerInputs, onlineThreshold: Int): NodeTrackerWidgetState.Tracking {
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
        longName = node.effectiveLongName.ifEmpty { NodeAddress.numToDefaultId(node.num) },
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

private val LAST_KNOWN_NUM_KEY = intPreferencesKey("last_known_num")
private val LAST_KNOWN_SHORT_NAME_KEY = stringPreferencesKey("last_known_short_name")
private val LAST_KNOWN_LONG_NAME_KEY = stringPreferencesKey("last_known_long_name")
private val LAST_KNOWN_FOREGROUND_KEY = intPreferencesKey("last_known_foreground")
private val LAST_KNOWN_BACKGROUND_KEY = intPreferencesKey("last_known_background")
private val LAST_KNOWN_BATTERY_KEY = intPreferencesKey("last_known_battery")
private val LAST_KNOWN_SNR_KEY = floatPreferencesKey("last_known_snr")
private val LAST_KNOWN_RSSI_KEY = intPreferencesKey("last_known_rssi")
private val LAST_KNOWN_HOPS_KEY = intPreferencesKey("last_known_hops")
private val LAST_KNOWN_LAST_HEARD_KEY = intPreferencesKey("last_known_last_heard")

/**
 * The last reading this instance rendered from a node DB, kept in its Glance state for when no DB is open.
 * [storeLastKnown] writes every key in one atomic edit, so the node number stands for the whole set.
 */
internal fun Preferences.lastKnownTracking(): NodeTrackerWidgetState.Tracking? = this[LAST_KNOWN_NUM_KEY]?.let { num ->
    NodeTrackerWidgetState.Tracking(
        nodeNum = num,
        shortName = this[LAST_KNOWN_SHORT_NAME_KEY].orEmpty(),
        longName = this[LAST_KNOWN_LONG_NAME_KEY].orEmpty(),
        colors = (this[LAST_KNOWN_FOREGROUND_KEY] ?: 0) to (this[LAST_KNOWN_BACKGROUND_KEY] ?: 0),
        batteryLevel = this[LAST_KNOWN_BATTERY_KEY],
        snr = this[LAST_KNOWN_SNR_KEY],
        rssi = this[LAST_KNOWN_RSSI_KEY],
        hopsAway = this[LAST_KNOWN_HOPS_KEY],
        lastHeard = this[LAST_KNOWN_LAST_HEARD_KEY] ?: 0,
        distance = null,
        isStale = true,
    )
}

internal fun MutablePreferences.storeLastKnown(state: NodeTrackerWidgetState.Tracking) {
    this[LAST_KNOWN_NUM_KEY] = state.nodeNum
    this[LAST_KNOWN_SHORT_NAME_KEY] = state.shortName
    this[LAST_KNOWN_LONG_NAME_KEY] = state.longName
    this[LAST_KNOWN_FOREGROUND_KEY] = state.colors.first
    this[LAST_KNOWN_BACKGROUND_KEY] = state.colors.second
    putOrRemove(LAST_KNOWN_BATTERY_KEY, state.batteryLevel)
    putOrRemove(LAST_KNOWN_SNR_KEY, state.snr)
    putOrRemove(LAST_KNOWN_RSSI_KEY, state.rssi)
    putOrRemove(LAST_KNOWN_HOPS_KEY, state.hopsAway)
    this[LAST_KNOWN_LAST_HEARD_KEY] = state.lastHeard
}

private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
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
    suspend fun currentInputs(): NodeTrackerInputs = checkedAgainstDb(inputs.value)

    /**
     * [inputs], with each empty node map checked against the database the same way, so an empty map is passed on only
     * when the open DB really is empty.
     */
    val checkedInputs: Flow<NodeTrackerInputs> = inputs.map { checkedAgainstDb(it) }

    private suspend fun checkedAgainstDb(current: NodeTrackerInputs): NodeTrackerInputs =
        if (current.nodes.isEmpty()) current.copy(nodes = nodeRepository.getNodeDbSnapshot()) else current
}
