/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import java.text.DateFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private val ONLINE_WINDOW_HOURS = 2.hours

/**
 * Returns a short string representing the time if it's within the last 24 hours, otherwise returns a short string
 * representing the date.
 *
 * @param time The time in milliseconds
 * @return Formatted date or time string, or null if time is 0
 */
fun getShortDate(time: Long): String? {
    if (time == 0L) return null
    val instant = time.toInstant()
    val isWithin24Hours = (nowInstant - instant) <= Duration.parse("24h")

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(instant.toDate())
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(instant.toDate())
    }
}

/**
 * Returns a short string representing the time if it's within the last 24 hours, otherwise returns a combined short
 * date/time string.
 *
 * @param time The time in milliseconds
 * @return Formatted date/time string
 */
fun getShortDateTime(time: Long): String {
    val instant = time.toInstant()
    val isWithin24Hours = (nowInstant - instant) <= Duration.parse("24h")

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(instant.toDate())
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(instant.toDate())
    }
}

/**
 * Formats a duration in seconds as a human-readable uptime string (e.g., "1d 2h 3m 4s").
 *
 * @param seconds The duration in seconds.
 * @return A formatted uptime string.
 */
fun formatUptime(seconds: Int): String = formatUptime(seconds.toLong())

/**
 * Formats a duration in seconds as a human-readable uptime string (e.g., "1d 2h 3m 4s").
 *
 * @param seconds The duration in seconds.
 * @return A formatted uptime string.
 */
private fun formatUptime(seconds: Long): String {
    if (seconds == 0L) return "0s"
    return seconds.seconds.toComponents { days, hours, minutes, secs, _ ->
        listOfNotNull(
            "${days}d".takeIf { days > 0 },
            "${hours}h".takeIf { hours > 0 },
            "${minutes}m".takeIf { minutes > 0 },
            "${secs}s".takeIf { secs > 0 },
        )
            .joinToString(" ")
    }
}

/**
 * Calculates the threshold in seconds for considering a node "online".
 *
 * @return The epoch seconds threshold.
 */
fun onlineTimeThreshold(): Int = (nowInstant - ONLINE_WINDOW_HOURS).epochSeconds.toInt()

/**
 * Calculates the remaining mute time in days and hours.
 *
 * @param remainingMillis The remaining time in milliseconds
 * @return Pair of (days, hours), where days is Int and hours is Double
 */
fun formatMuteRemainingTime(remainingMillis: Long): Pair<Int, Double> {
    val duration = remainingMillis.milliseconds
    if (duration <= Duration.ZERO) return 0 to 0.0
    val totalHours = duration.toDouble(DurationUnit.HOURS)
    return (totalHours / HOURS_PER_DAY).toInt() to (totalHours % HOURS_PER_DAY)
}
