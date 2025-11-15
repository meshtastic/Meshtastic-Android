/*
 * Copyright (c) 2025 Meshtastic LLC
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

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private const val ONLINE_WINDOW_HOURS = 2

// return time if within 24 hours, otherwise date
fun getShortDate(time: Long): String? {
    val date = if (time != 0L) Date(time) else return null
    val isWithin24Hours = System.currentTimeMillis() - date.time <= TimeUnit.DAYS.toMillis(1)

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}

// return time if within 24 hours, otherwise date/time
fun getShortDateTime(time: Long): String {
    val date = Date(time)
    val isWithin24Hours = System.currentTimeMillis() - date.time <= TimeUnit.DAYS.toMillis(1)

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }
}

fun formatUptime(seconds: Int): String = formatUptime(seconds.toLong())

private fun formatUptime(seconds: Long): String {
    val days = TimeUnit.SECONDS.toDays(seconds)
    val hours = TimeUnit.SECONDS.toHours(seconds) % TimeUnit.DAYS.toHours(1)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % TimeUnit.HOURS.toMinutes(1)
    val secs = seconds % TimeUnit.MINUTES.toSeconds(1)

    return listOfNotNull(
        "${days}d".takeIf { days > 0 },
        "${hours}h".takeIf { hours > 0 },
        "${minutes}m".takeIf { minutes > 0 },
        "${secs}s".takeIf { secs > 0 },
    )
        .joinToString(" ")
}

fun onlineTimeThreshold(): Int {
    val currentSeconds = System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(1)
    return (currentSeconds - TimeUnit.HOURS.toSeconds(ONLINE_WINDOW_HOURS.toLong())).toInt()
}

/**
 * Calculates the remaining mute time in days and hours.
 *
 * @param remainingMillis The remaining time in milliseconds
 * @return Pair of (days, hours), where days is Int and hours is Double
 */
fun formatMuteRemainingTime(remainingMillis: Long): Pair<Int, Double> {
    if (remainingMillis <= 0) return Pair(0, 0.0)
    val totalHours = remainingMillis.toDouble() / TimeUnit.HOURS.toMillis(1)
    val days = (totalHours / TimeUnit.DAYS.toHours(1)).toInt()
    val hours = totalHours % TimeUnit.DAYS.toHours(1)
    return Pair(days, hours)
}
