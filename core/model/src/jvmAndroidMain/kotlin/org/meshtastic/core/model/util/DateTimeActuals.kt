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
package org.meshtastic.core.model.util

import org.meshtastic.core.common.util.nowInstant
import org.meshtastic.core.common.util.toDate
import org.meshtastic.core.common.util.toInstant
import java.text.DateFormat
import kotlin.time.Duration.Companion.hours

private val DAY_DURATION = 24.hours

/**
 * Returns a short string representing the time if it's within the last 24 hours, otherwise returns a combined short
 * date/time string.
 *
 * @param time The time in milliseconds
 * @return Formatted date/time string
 */
actual fun getShortDateTime(time: Long): String {
    val instant = time.toInstant()
    val isWithin24Hours = (nowInstant - instant) <= DAY_DURATION

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(instant.toDate())
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(instant.toDate())
    }
}
