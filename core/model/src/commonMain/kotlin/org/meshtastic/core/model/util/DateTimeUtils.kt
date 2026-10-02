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

import org.meshtastic.core.model.util.TimeConstants.HOURS_PER_DAY
import kotlin.time.Duration.Companion.milliseconds

/**
 * Returns a short string representing the time if it's within the last 24 hours, otherwise returns a combined short
 * date/time string.
 *
 * @param time The time in milliseconds
 * @return Formatted date/time string
 */
expect fun getShortDateTime(time: Long): String

/**
 * Calculates the remaining mute time in days and hours.
 *
 * @param remainingMillis The remaining time in milliseconds
 * @return Pair of (days, hours), where days is Int and hours is Double
 */
fun formatMuteRemainingTime(remainingMillis: Long): Pair<Int, Double> {
    val duration = remainingMillis.milliseconds
    if (duration <= kotlin.time.Duration.ZERO) return 0 to 0.0
    val totalHours = duration.toDouble(kotlin.time.DurationUnit.HOURS)
    return (totalHours / HOURS_PER_DAY).toInt() to (totalHours % HOURS_PER_DAY)
}
