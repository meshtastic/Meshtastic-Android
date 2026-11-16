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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.SignalSample
import org.meshtastic.core.model.SignalSurveySession

/** Why a running survey is logging nothing. */
enum class SignalSurveyBlocker {
    NOT_CONNECTED,
    LOCATION_PERMISSION,
    LOCATION_OFF,
    NO_FIX,
}

/**
 * Logs the signal of each packet the connected radio hears against where the radio is. Passive: it sends nothing. Each
 * [start] opens a session, which [stop] or the end of the process closes.
 */
interface SignalSurveyRepository {
    val recording: StateFlow<Boolean>

    /** The session being recorded into, or null while idle. */
    val activeSessionId: StateFlow<Long?>

    /** Null while idle, and while a running survey can log. */
    val blocker: StateFlow<SignalSurveyBlocker?>

    /** Every session of the connected radio, newest first. */
    val sessions: Flow<List<SignalSurveySession>>

    /** Starts a survey; a no-op while one is already on. */
    fun start()

    fun stop()

    /** The readings of one session, re-emitted as it grows. */
    fun samples(sessionId: Long): Flow<List<SignalSample>>

    suspend fun samplesOnce(sessionId: Long): List<SignalSample>

    suspend fun delete(sessionId: Long)
}
