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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis

/**
 * Tracks per-request start times and reports round-trip durations for request/response handlers.
 *
 * Request handlers (traceroute, neighbor-info, …) call [start] when issuing a request keyed by its id, then
 * [appendDuration] when the matching response arrives to annotate the user-facing text with how long the round trip
 * took. Start times are stored in an atomic immutable map so [start] (any coroutine) and [appendDuration] (the handler
 * scope) never race.
 */
internal class RequestTimer {

    private val startTimes = atomic(persistentMapOf<Int, Long>())

    /** Records the start time for [requestId]. */
    fun start(requestId: Int) {
        startTimes.update { it.put(requestId, nowMillis) }
    }

    /**
     * Consumes the start time recorded for [requestId] and appends a `Duration: N s` line to [text], logging completion
     * under [label]. Returns [text] unchanged when no start time was recorded for the id.
     */
    fun appendDuration(requestId: Int, text: String, label: String): String {
        val start = startTimes.value[requestId]
        startTimes.update { it.remove(requestId) }
        if (start == null) return text
        val seconds = (nowMillis - start) / MILLIS_PER_SECOND
        Logger.i { "$label $requestId complete in $seconds s" }
        return "$text\n\nDuration: ${NumberFormatter.format(seconds, 1)} s"
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
