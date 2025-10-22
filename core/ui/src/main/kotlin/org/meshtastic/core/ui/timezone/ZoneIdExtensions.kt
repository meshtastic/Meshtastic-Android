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

@file:Suppress("Wrapping", "UnusedImports", "SpacingAroundColon")

package org.meshtastic.core.ui.timezone

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/**
 * Generates a POSIX time zone string from a [ZoneId]. Uses the specification found
 * [here](https://www.postgresql.org/docs/current/datetime-posix-timezone-specs.html).
 */
fun ZoneId.toPosixString(): String {
    val now = Instant.now()
    val upcomingTransition = rules.nextTransition(now)

    // No upcoming transition means this time zone does not support DST.
    if (upcomingTransition == null) {
        with(now.asZonedDateTime()) {
            return "${timeZoneShortName()}${formattedOffsetString()}"
        }
    }

    val upcomingInstant = upcomingTransition.instant
    val followingTransition = rules.nextTransition(upcomingInstant)

    val (stdTransition, dstTransition) =
        if (rules.isDaylightSavings(upcomingInstant)) {
            followingTransition to upcomingTransition
        } else {
            upcomingTransition to followingTransition
        }

    val stdDate = stdTransition.instant.asZonedDateTime()
    val dstDate = dstTransition.instant.asZonedDateTime()

    return buildString {
        append(stdDate.timeZoneShortName())
        append(stdDate.formattedOffsetString())
        append(dstDate.timeZoneShortName())

        // Don't append the DST offset if it is only 1 hour off.
        @Suppress("MagicNumber")
        if (abs(stdDate.offset.totalSeconds - dstDate.offset.totalSeconds) != 3600) {
            append(dstDate.formattedOffsetString())
        }

        append(dstTransition.dateTimeBefore.transitionRuleString())
        append(stdTransition.dateTimeBefore.transitionRuleString())
    }
}

/** Returns the time zone short. e.g. "EST" or "EDT". */
private fun ZonedDateTime.timeZoneShortName(): String {
    val formatter = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH)
    val shortName = format(formatter)
    return if (shortName.startsWith("GMT")) "GMT" else shortName
}

/**
 * Returns the time zone offset string with the format "<HOURS>:<MINUTES>:<SECONDS>". Minutes and seconds are only shown
 * if they are non-zero.
 */
@Suppress("MagicNumber")
private fun ZonedDateTime.formattedOffsetString(): String {
    val offsetSeconds = -offset.totalSeconds

    val hours = offsetSeconds / 3600
    val remainingSeconds = abs(offsetSeconds) % 3600
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    return buildString {
        append(hours)
        appendMinSec(minutes = minutes, seconds = seconds) { ":%02d".format(Locale.ENGLISH, it) }
    }
}

/**
 * Returns a transition rule string with the format
 * ",M<MONTH>.<WEEK_OF_MONTH>.<DAY_OF_WEEK>/<HOURS>:<MINUTES>:<SECONDS>". Time is omitted if it is 2:00:00, since that
 * is the spec default. Otherwise, append time with non-zero values.
 */
@Suppress("MagicNumber")
private fun LocalDateTime.transitionRuleString() = buildString {
    val weekOfMonth = get(ChronoField.ALIGNED_WEEK_OF_MONTH)
    val dayOfWeek = get(WeekFields.of(DayOfWeek.SUNDAY, 7).dayOfWeek()) - 1
    append(",M$monthValue.$weekOfMonth.$dayOfWeek")

    when {
        // No-op for spec default
        hour == 2 && minute == 0 && second == 0 -> Unit
        else -> {
            append("/$hour")
            appendMinSec(minutes = minute, seconds = second) { ":$it" }
        }
    }
}

private inline fun StringBuilder.appendMinSec(minutes: Int, seconds: Int, format: (Int) -> String) {
    if (minutes != 0 || seconds != 0) {
        // This covers both "30m:30s" and "00m:30s"
        append(format(minutes))
        // This prevents "30m:00s"
        if (seconds != 0) append(format(seconds))
    }
}

context(zoneId: ZoneId)
private fun Instant.asZonedDateTime() = ZonedDateTime.ofInstant(this, zoneId)
