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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.repository.SignalSurveyRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

data class SurveySessionUi(val session: SignalSurveySession, val visible: Boolean, val recording: Boolean)

data class SignalSurveyUiState(
    val recording: Boolean = false,
    val blocker: SignalSurveyBlocker? = null,
    val sessions: List<SurveySessionUi> = emptyList(),
    val heatmap: Boolean = false,
) {
    /** Readings in the session being recorded, or in every session while idle. */
    val readingCount: Int
        get() = sessions.filter { it.recording || !recording }.sumOf { it.session.readingCount }
}

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class SignalSurveyViewModel(
    private val repository: SignalSurveyRepository,
    private val visibility: SignalSurveyVisibility,
    cellSource: SignalSurveyCellSource,
    nodeRepository: NodeRepository,
) : ViewModel() {

    private val cellMeters = MutableStateFlow(SURVEY_CELL_METERS)

    /** The tapped spot rather than the tapped square, which is rebuilt at a new size on every zoom step. */
    private val selectedSpot = MutableStateFlow<SurveySpot?>(null)

    val state: StateFlow<SignalSurveyUiState> =
        combine(
            combine(repository.recording, repository.blocker, ::Pair),
            repository.sessions,
            repository.activeSessionId,
            visibility.sessionIds,
            visibility.heatmap,
        ) { (recording, blocker), sessions, activeId, visible, heatmap ->
            SignalSurveyUiState(
                recording = recording,
                blocker = blocker,
                sessions =
                sessions.map { SurveySessionUi(it, visible = it.id in visible, recording = it.id == activeId) },
                heatmap = heatmap,
            )
        }
            .stateInWhileSubscribed(initialValue = SignalSurveyUiState())

    /** The squares to draw, sized for the map's zoom. Empty while the map draws a heatmap instead. */
    val cells: StateFlow<List<SurveyCellUi>> =
        visibility.heatmap
            .flatMapLatest { heatmap -> if (heatmap) flowOf(emptyList()) else cellSource.cells(cellMeters) }
            .stateInWhileSubscribed(initialValue = emptyList())

    /**
     * The points of the heatmap: one per street-level square, so a long stop in one spot adds no more heat than a pass
     * through it. Empty while the map draws squares.
     */
    val heatPoints: StateFlow<List<SurveyCellUi>> =
        visibility.heatmap
            .flatMapLatest { heatmap ->
                if (heatmap) cellSource.cells(flowOf(SURVEY_CELL_METERS)) else flowOf(emptyList())
            }
            .stateInWhileSubscribed(initialValue = emptyList())

    val selectedCell: StateFlow<SurveyCellUi?> =
        combine(cells, selectedSpot) { cells, spot -> spot?.let { cells.containing(it) } }
            .stateInWhileSubscribed(initialValue = null)

    /**
     * Lines from the tapped square to each node heard directly there that shares a position, ending where that node
     * says it is (at the precision it chose), nearest first.
     */
    val selectedLinks: StateFlow<SurveyLinks> =
        combine(selectedCell, nodeRepository.nodeDBbyNum) { selected, nodes ->
            selected?.let { surveyLinks(it, nodes) } ?: SurveyLinks()
        }
            .stateInWhileSubscribed(initialValue = SurveyLinks())

    fun setRecording(recording: Boolean) = if (recording) repository.start() else repository.stop()

    fun setVisible(sessionId: Long, visible: Boolean) {
        visibility.sessionIds.update { if (visible) it + sessionId else it - sessionId }
    }

    fun setHeatmap(heatmap: Boolean) {
        visibility.heatmap.value = heatmap
    }

    /** Tells the overlay how far the map is zoomed, in 256 px tile zoom levels, so squares stay large enough to see. */
    fun setCamera(zoom: Double, latitude: Double) {
        cellMeters.value = surveyCellMeters(zoom, latitude)
    }

    /** Selects the square [id] names, by its session and center, so the selection survives the map zooming. */
    fun selectCell(id: String?) {
        selectedSpot.value = id?.let { cellId -> cells.value.firstOrNull { it.id == cellId } }?.spot()
    }

    fun delete(sessionId: Long) {
        visibility.sessionIds.update { it - sessionId }
        viewModelScope.launch { repository.delete(sessionId) }
    }

    /**
     * The CSV of the session [sessionId] that started at [startedAtMillis]. The start time pins it to one radio: ids
     * repeat across radios, and the radio can change while a file picker is open.
     */
    suspend fun csv(sessionId: Long, startedAtMillis: Long): ByteArray? {
        val session = repository.sessions.first().firstOrNull { it.id == sessionId }
        return if (session?.startedAtMillis != startedAtMillis) {
            null
        } else {
            repository.samplesOnce(sessionId).takeIf { it.isNotEmpty() }?.toSurveyCsv()?.encodeToByteArray()
        }
    }
}
