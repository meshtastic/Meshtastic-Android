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
package org.meshtastic.core.common.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Where a timestamp falls relative to the local calendar day, for chat date separators. */
enum class RelativeDay {
    Today,
    Yesterday,
    Older,
}

/**
 * True when both timestamps fall on the same local calendar day.
 *
 * Compared on calendar date rather than an elapsed-millis window, so a separator lands on midnight rather than 24 hours
 * after the previous message.
 */
fun isSameLocalDay(firstMillis: Long, secondMillis: Long, timeZone: TimeZone = systemTimeZone): Boolean =
    firstMillis.toInstant().toLocalDateTime(timeZone).date == secondMillis.toInstant().toLocalDateTime(timeZone).date

fun relativeDayOf(
    timestampMillis: Long,
    referenceMillis: Long = nowMillis,
    timeZone: TimeZone = systemTimeZone,
): RelativeDay {
    val day = timestampMillis.toInstant().toLocalDateTime(timeZone).date
    val today = referenceMillis.toInstant().toLocalDateTime(timeZone).date
    return when (day) {
        today -> RelativeDay.Today
        today.minus(DatePeriod(days = 1)) -> RelativeDay.Yesterday
        else -> RelativeDay.Older
    }
}
