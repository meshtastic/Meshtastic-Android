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
package org.meshtastic.feature.map.survey

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.repository.SignalSurveyRepository
import org.meshtastic.core.ui.component.Quality
import kotlin.math.pow

/** What the map and the AR view show of the survey. Outlives both screens, so a choice holds across a tab switch. */
@Single
class SignalSurveyVisibility(repository: SignalSurveyRepository, scope: ServiceScope) {
    /** The sessions drawn. */
    val sessionIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Whether the map draws a heatmap rather than squares. */
    val heatmap = MutableStateFlow(false)

    init {
        // A session shows from the moment it starts. Held here, once, so a screen opened mid-recording cannot re-show
        // a session the user has hidden.
        scope.launch {
            repository.activeSessionId.filterNotNull().collect { id -> sessionIds.update { it + id } }
        }
    }
}

/**
 * One square as a map draws it. [id] is stable across updates, so a tapped square stays selected as it gains readings.
 */
data class SurveyCellUi(
    val id: String,
    val cell: SurveyCell,
    val quality: Quality,
    /** Each node heard directly in this square, rated the same way as the square. */
    val directQuality: Map<Int, Quality> = emptyMap(),
    val sessionId: Long = 0,
)

/** A tapped square of one session, kept while the squares around it are rebuilt at another size. */
data class SurveySpot(val sessionId: Long, val south: Double, val west: Double, val north: Double, val east: Double) {
    val latitude: Double
        get() = (south + north) * CENTER

    val longitude: Double
        get() = (west + east) * CENTER
}

private const val CENTER = 0.5

internal fun SurveyCellUi.spot() = SurveySpot(sessionId, cell.south, cell.west, cell.north, cell.east)

private val SurveyCellUi.centerLatitude: Double
    get() = (cell.south + cell.north) * CENTER
private val SurveyCellUi.centerLongitude: Double
    get() = (cell.west + cell.east) * CENTER

/**
 * The square of [spot]'s session at whatever size the squares are now: the one holding the tapped square's center after
 * zooming out, or after zooming in, the nearest smaller square inside it that holds readings.
 */
internal fun List<SurveyCellUi>.containing(spot: SurveySpot): SurveyCellUi? {
    val own = filter { it.sessionId == spot.sessionId }
    return own.firstOrNull {
        spot.latitude in it.cell.south..it.cell.north && spot.longitude in it.cell.west..it.cell.east
    }
        ?: own.filter { it.centerLatitude in spot.south..spot.north && it.centerLongitude in spot.west..spot.east }
            .minByOrNull { (it.centerLatitude - spot.latitude).pow(2) + (it.centerLongitude - spot.longitude).pow(2) }
}

/** The rated squares of every visible session, shared by the map and the AR view so the two cannot disagree. */
@Single
class SignalSurveyCellSource(
    private val repository: SignalSurveyRepository,
    private val visibility: SignalSurveyVisibility,
) {
    /** The squares of every visible session at [cellMeters], oldest session first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cells(cellMeters: Flow<Double>): Flow<List<SurveyCellUi>> =
        combine(visibility.sessionIds, sessionPresets(), cellMeters) { visible, presets, meters ->
            Triple(visible.filter { it in presets }.sorted(), presets, meters)
        }
            .distinctUntilChanged()
            .flatMapLatest { (ids, presets, meters) ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(ids.map { id -> sessionCells(id, presets[id], meters) }) { perSession ->
                        perSession.flatMap { it }
                    }
                }
            }

    /** Preset name per session id; also the set of sessions that still exist. */
    private fun sessionPresets(): Flow<Map<Long, String?>> =
        repository.sessions.map { sessions -> sessions.associate { it.id to it.modemPreset } }.distinctUntilChanged()

    private fun sessionCells(sessionId: Long, presetName: String?, meters: Double): Flow<List<SurveyCellUi>> {
        val preset = modemPresetNamed(presetName)
        return repository.samples(sessionId).map { samples ->
            samples.toSurveyCells(meters).map { cell ->
                SurveyCellUi(
                    id = "$sessionId:${cell.south}:${cell.west}:$meters",
                    cell = cell,
                    quality = cell.quality(preset),
                    directQuality = cell.direct.mapValues { (_, reception) -> reception.quality(preset) },
                    sessionId = sessionId,
                )
            }
        }
    }
}
