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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.decodeBase64
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.TemperatureUnit
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** What one Telemetry widget instance renders. */
internal sealed interface TelemetryWidgetState {
    /** No node chosen yet, or the chosen one is missing from a populated node DB. Tapping opens the picker. */
    data object NodeNotFound : TelemetryWidgetState

    /**
     * [readings] are the tiles in order: every reading the node has reported, less the hidden ones. A reading with no
     * value in [snapshot] is one the node has stopped sending and shows a dash. [updatedAt] is the newest reading's
     * time in epoch seconds, null when no stored log dates it.
     */
    data class Showing(
        val nodeNum: Int,
        val shortName: String,
        val colors: Pair<Int, Int>,
        val channelLabels: List<String>,
        val snapshot: TelemetrySnapshot,
        val readings: List<TelemetryReading>,
        val updatedAt: Int?,
        val isStale: Boolean,
        val units: MeasurementSystem,
        val isFahrenheit: Boolean,
    ) : TelemetryWidgetState
}

/** Everything an instance needs besides its own settings and its node's log, shared by every instance. */
internal data class TelemetryWidgetInputs(
    val nodes: Map<Int, Node> = emptyMap(),
    val ourNodeNum: Int? = null,
    val units: MeasurementSystem = MeasurementSystem.METRIC,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

/** A reading older than this is labeled as old (meshtastic/design#166). */
internal val STALE_AFTER = 2.hours

internal fun telemetryWidgetState(
    nodeNum: Int?,
    hidden: Set<TelemetryReading>,
    seen: Set<TelemetryReading>,
    inputs: TelemetryWidgetInputs,
    history: List<Telemetry>,
    lastKnown: TelemetryLastKnown? = null,
    now: Long = nowSeconds,
): TelemetryWidgetState {
    val node = nodeNum?.let { inputs.nodes[it] }
    val known =
        when {
            node != null -> TelemetryLastKnown.of(node, telemetrySnapshot(node, history))

            // An empty node DB means no radio's DB is open (disconnected, or no device selected), not that the node
            // left.
            nodeNum != null && inputs.nodes.isEmpty() -> lastKnown?.takeIf { it.nodeNum == nodeNum }

            else -> null
        } ?: return TelemetryWidgetState.NodeNotFound
    val reported = seen + everReported(history) + known.snapshot.reported
    val updatedAt = known.snapshot.time
    val threshold = now - STALE_AFTER.inWholeSeconds
    // Without a logged time the node's last heard is an upper bound: when even that is old, so is every reading.
    val isStale = if (updatedAt != null) updatedAt < threshold else known.lastHeard > 0 && known.lastHeard < threshold
    return TelemetryWidgetState.Showing(
        nodeNum = known.nodeNum,
        shortName = known.shortName,
        colors = known.colors,
        channelLabels = known.channelLabels,
        snapshot = known.snapshot,
        readings = TelemetryReading.all.filter { it in reported && it !in hidden },
        updatedAt = updatedAt,
        isStale = isStale,
        units = inputs.units,
        isFahrenheit = inputs.temperatureUnit == TemperatureUnit.FAHRENHEIT,
    )
}

/**
 * The nodes the picker offers: those that have reported any telemetry this widget shows, ours first, then newest,
 * filtered by [query] against the long name, short name and node id.
 */
internal fun telemetryCandidates(nodes: Collection<Node>, ourNodeNum: Int?, query: String = ""): List<Node> = nodes
    .filter { !it.isIgnored && (it.hasEnvironmentMetrics || it.hasAirQualityMetrics || it.hasPowerMetrics) }
    .filter { it.matchesQuery(query) }
    .filter {
        TelemetrySnapshot(it.environmentMetrics, it.airQualityMetrics, it.powerMetrics).reported.isNotEmpty()
    }
    .sortedWith(
        compareByDescending<Node> { it.num == ourNodeNum }.thenByDescending { it.lastHeard }.thenBy { it.num },
    )

/** The node a new widget starts on: ours when it reports anything this widget shows. */
internal fun defaultTelemetryNode(nodes: Collection<Node>, ourNodeNum: Int?): Node? =
    telemetryCandidates(nodes, ourNodeNum).firstOrNull()?.takeIf { it.num == ourNodeNum }

/** What a Telemetry instance renders from its node besides the log, so a change to it is a reason to redraw. */
internal fun Node.telemetryKey(): List<Any> =
    listOf(environmentMetrics, airQualityMetrics, powerMetrics, powerChannelLabels, user.short_name, colors)

/** Writes what the setup screen chose; a new node starts over on page one with nothing remembered from the old one. */
internal fun MutablePreferences.storeTelemetrySettings(nodeNum: Int, hidden: Set<TelemetryReading>) {
    // Readings remembered for another node would show as dashes on this one.
    if (this[TELEMETRY_NODE_NUM_KEY] != nodeNum) remove(TELEMETRY_SEEN_KEY)
    this[TELEMETRY_NODE_NUM_KEY] = nodeNum
    putReadingSet(TELEMETRY_HIDDEN_KEY, hidden)
    this[TELEMETRY_PAGE_KEY] = 0
}

/**
 * Adds [reported] to what the instance remembers and stores [known], unless setup has since moved the instance to
 * another node. The stored set is merged rather than replaced, since this composition's copy of it may be older.
 */
internal fun MutablePreferences.rememberReported(
    nodeNum: Int,
    reported: Set<TelemetryReading>,
    known: TelemetryLastKnown,
) {
    if (this[TELEMETRY_NODE_NUM_KEY] != nodeNum) return
    putReadingSet(TELEMETRY_SEEN_KEY, readingSet(TELEMETRY_SEEN_KEY) + reported)
    storeLastKnown(known)
}

/** Per-instance Glance state keys. */
internal val TELEMETRY_NODE_NUM_KEY = intPreferencesKey("telemetry_node_num")
internal val TELEMETRY_HIDDEN_KEY = stringSetPreferencesKey("telemetry_hidden")
internal val TELEMETRY_SEEN_KEY = stringSetPreferencesKey("telemetry_seen")
internal val TELEMETRY_PAGE_KEY = intPreferencesKey("telemetry_page")

internal fun Preferences.readingSet(key: Preferences.Key<Set<String>>): Set<TelemetryReading> =
    this[key].orEmpty().mapNotNull(TelemetryReading::fromId).toSet()

internal fun MutablePreferences.putReadingSet(key: Preferences.Key<Set<String>>, readings: Set<TelemetryReading>) {
    this[key] = readings.map { it.id }.toSet()
}

/** What an instance last rendered from a node DB, kept for when no DB is open. */
internal data class TelemetryLastKnown(
    val nodeNum: Int,
    val shortName: String,
    val colors: Pair<Int, Int>,
    val channelLabels: List<String>,
    val snapshot: TelemetrySnapshot,
    val lastHeard: Int,
) {
    companion object {
        fun of(node: Node, snapshot: TelemetrySnapshot) = TelemetryLastKnown(
            nodeNum = node.num,
            shortName = node.user.short_name.ifEmpty { "???" },
            colors = node.colors,
            // No label at all reads back from Glance state as no list, so it is stored that way too.
            channelLabels = node.powerChannelLabels.takeIf { labels -> labels.any { it.isNotBlank() } }.orEmpty(),
            snapshot = snapshot,
            lastHeard = node.lastHeard,
        )
    }
}

private val LAST_NUM_KEY = intPreferencesKey("telemetry_last_num")
private val LAST_SHORT_NAME_KEY = stringPreferencesKey("telemetry_last_short_name")
private val LAST_FOREGROUND_KEY = intPreferencesKey("telemetry_last_foreground")
private val LAST_BACKGROUND_KEY = intPreferencesKey("telemetry_last_background")
private val LAST_LABELS_KEY = stringPreferencesKey("telemetry_last_channel_labels")
private val LAST_ENVIRONMENT_KEY = stringPreferencesKey("telemetry_last_environment")
private val LAST_AIR_QUALITY_KEY = stringPreferencesKey("telemetry_last_air_quality")
private val LAST_POWER_KEY = stringPreferencesKey("telemetry_last_power")
private val LAST_TIME_KEY = intPreferencesKey("telemetry_last_time")
private val LAST_HEARD_KEY = intPreferencesKey("telemetry_last_heard")

// A unit separator cannot appear in a label typed on a phone keyboard.
private const val LABEL_SEPARATOR = "\u001F"

/** [storeLastKnown] writes every key in one atomic edit, so the node number stands for the whole set. */
internal fun Preferences.lastKnownTelemetry(): TelemetryLastKnown? = this[LAST_NUM_KEY]?.let { num ->
    TelemetryLastKnown(
        nodeNum = num,
        shortName = this[LAST_SHORT_NAME_KEY].orEmpty(),
        colors = (this[LAST_FOREGROUND_KEY] ?: 0) to (this[LAST_BACKGROUND_KEY] ?: 0),
        channelLabels = this[LAST_LABELS_KEY]?.takeIf { it.isNotEmpty() }?.split(LABEL_SEPARATOR).orEmpty(),
        snapshot =
        TelemetrySnapshot(
            environment = this[LAST_ENVIRONMENT_KEY]?.decodeBase64()?.let(EnvironmentMetrics.ADAPTER::decode),
            airQuality = this[LAST_AIR_QUALITY_KEY]?.decodeBase64()?.let(AirQualityMetrics.ADAPTER::decode),
            power = this[LAST_POWER_KEY]?.decodeBase64()?.let(PowerMetrics.ADAPTER::decode),
            time = this[LAST_TIME_KEY],
        ),
        lastHeard = this[LAST_HEARD_KEY] ?: 0,
    )
}

internal fun MutablePreferences.storeLastKnown(known: TelemetryLastKnown) {
    this[LAST_NUM_KEY] = known.nodeNum
    this[LAST_SHORT_NAME_KEY] = known.shortName
    this[LAST_FOREGROUND_KEY] = known.colors.first
    this[LAST_BACKGROUND_KEY] = known.colors.second
    this[LAST_LABELS_KEY] = known.channelLabels.joinToString(LABEL_SEPARATOR)
    putOrRemove(
        LAST_ENVIRONMENT_KEY,
        known.snapshot.environment?.let { EnvironmentMetrics.ADAPTER.encodeByteString(it).base64() },
    )
    putOrRemove(
        LAST_AIR_QUALITY_KEY,
        known.snapshot.airQuality?.let { AirQualityMetrics.ADAPTER.encodeByteString(it).base64() },
    )
    putOrRemove(LAST_POWER_KEY, known.snapshot.power?.let { PowerMetrics.ADAPTER.encodeByteString(it).base64() })
    putOrRemove(LAST_TIME_KEY, known.snapshot.time)
    this[LAST_HEARD_KEY] = known.lastHeard
}

private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
}

@Single
class TelemetryWidgetDataSource(
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
    localeUnitsProvider: LocaleUnitsProvider,
    scope: ApplicationCoroutineScope,
) {
    internal val inputs: StateFlow<TelemetryWidgetInputs> =
        combine(
            nodeRepository.nodeDBbyNum,
            nodeRepository.myNodeInfo,
            localeUnitsProvider.measurementSystem,
            localeUnitsProvider.temperatureUnit,
        ) { nodes, myNodeInfo, units, temperatureUnit ->
            TelemetryWidgetInputs(nodes, myNodeInfo?.myNodeNum, units, temperatureUnit)
        }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = TelemetryWidgetInputs())

    /** The current inputs, reading the database directly while the repository's flow still holds its empty start. */
    internal suspend fun currentInputs(): TelemetryWidgetInputs = checkedAgainstDb(inputs.value)

    /** [inputs], with each empty node map checked against the database, as [NodeTrackerWidgetDataSource] does. */
    internal val checkedInputs: Flow<TelemetryWidgetInputs> = inputs.map { checkedAgainstDb(it) }

    private suspend fun checkedAgainstDb(current: TelemetryWidgetInputs): TelemetryWidgetInputs =
        if (current.nodes.isEmpty()) current.copy(nodes = nodeRepository.getNodeDbSnapshot()) else current

    /** The node's stored telemetry, newest first. Empty when logging is off. */
    fun history(nodeNum: Int): Flow<List<Telemetry>> = meshLogRepository.getTelemetryFrom(nodeNum)

    /** The log as it stands, for a render that cannot wait on the flow; empty if the database is slow to answer. */
    suspend fun currentHistory(nodeNum: Int): List<Telemetry> =
        withTimeoutOrNull(HISTORY_TIMEOUT) { history(nodeNum).first() }.orEmpty()

    private companion object {
        val HISTORY_TIMEOUT = 2.seconds
    }
}
