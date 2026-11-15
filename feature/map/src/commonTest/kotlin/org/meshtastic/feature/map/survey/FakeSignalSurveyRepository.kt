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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.meshtastic.core.model.SignalSample
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.repository.SignalSurveyRepository

/** An in-memory survey: sessions are keyed by id and hold whatever readings [add] gave them. */
internal class FakeSignalSurveyRepository : SignalSurveyRepository {
    override val recording = MutableStateFlow(false)
    override val activeSessionId = MutableStateFlow<Long?>(null)
    override val blocker = MutableStateFlow<SignalSurveyBlocker?>(null)
    val readings = MutableStateFlow<Map<Long, List<SignalSample>>>(emptyMap())
    val presets = mutableMapOf<Long, String?>()
    val deleted = mutableListOf<Long>()

    override val sessions: Flow<List<SignalSurveySession>> = readings.map { all ->
        all.map { (id, samples) -> SignalSurveySession(id, id, null, presets[id], samples.size) }
            .sortedByDescending { it.id }
    }

    override fun start() {
        recording.value = true
    }

    override fun stop() {
        recording.value = false
    }

    override fun samples(sessionId: Long): Flow<List<SignalSample>> = readings.map { it[sessionId].orEmpty() }

    override suspend fun samplesOnce(sessionId: Long) = readings.value[sessionId].orEmpty()

    override suspend fun delete(sessionId: Long) {
        deleted += sessionId
        readings.update { it - sessionId }
    }

    fun add(sessionId: Long, vararg samples: SignalSample, preset: String? = "LONG_FAST") {
        presets[sessionId] = preset
        readings.update { it + (sessionId to it[sessionId].orEmpty() + samples) }
    }
}

internal fun reading(lat: Double, lon: Double = -95.0, snr: Float? = 5f, rssi: Int = -90) =
    SignalSample(0, lat, lon, 5f, 1, rssi, snr, direct = true)
