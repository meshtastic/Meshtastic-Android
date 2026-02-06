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
@file:Suppress("Wrapping", "UnusedImports", "SpacingAroundColon")

package org.meshtastic.core.ui.timezone

import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule
import java.util.Locale
import kotlin.math.abs

/**
 * Generates a POSIX time zone string from a [ZoneId]. Uses the specification found
 * [here](https://www.postgresql.org/docs/current/datetime-posix-timezone-specs.html).
 */
@Suppress("ReturnCount")
fun ZoneId.toPosixString(): String {
    val rules = this.rules

    if (rules.isFixedOffset || rules.transitionRules.isEmpty()) {
        val now = Instant.now()
        val zdt = ZonedDateTime.ofInstant(now, this)
        return "${formatAbbreviation(zdt.timeZoneShortName())}${formatPosixOffset(zdt.offset)}"
    }

    val springRule = rules.transitionRules.lastOrNull { it.offsetAfter.totalSeconds > it.offsetBefore.totalSeconds }
    val fallRule = rules.transitionRules.lastOrNull { it.offsetAfter.totalSeconds < it.offsetBefore.totalSeconds }

    if (springRule == null || fallRule == null) {
        val now = Instant.now()
        val zdt = ZonedDateTime.ofInstant(now, this)
        return "${formatAbbreviation(zdt.timeZoneShortName())}${formatPosixOffset(zdt.offset)}"
    }

    return buildString {
        val stdAbbrev = getTransitionAbbreviation(this@toPosixString, fallRule)
        val dstAbbrev = getTransitionAbbreviation(this@toPosixString, springRule)

        append(formatAbbreviation(stdAbbrev))
        append(formatPosixOffset(springRule.offsetBefore))
        append(formatAbbreviation(dstAbbrev))

        @Suppress("MagicNumber")
        if (springRule.offsetAfter.totalSeconds - springRule.offsetBefore.totalSeconds != 3600) {
            append(formatPosixOffset(springRule.offsetAfter))
        }

        append(formatTransitionRule(springRule))
        append(formatTransitionRule(fallRule))
    }
}

internal fun ZonedDateTime.timeZoneShortName(): String {
    val formatter = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH)
    val shortName = format(formatter)
    return if (shortName.startsWith("GMT")) "GMT" else shortName
}

fun formatAbbreviation(abbrev: String): String = if (abbrev.all { it.isLetter() }) abbrev else "<$abbrev>"

internal fun getTransitionAbbreviation(zone: ZoneId, rule: ZoneOffsetTransitionRule): String {
    val transition = rule.createTransition(Year.now().value)
    return ZonedDateTime.ofInstant(transition.instant, zone).timeZoneShortName()
}

@Suppress("MagicNumber")
fun formatPosixOffset(offset: ZoneOffset): String {
    val offsetSeconds = -offset.totalSeconds
    val hours = offsetSeconds / 3600
    val remainingSeconds = abs(offsetSeconds) % 3600
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    return buildString {
        if (offsetSeconds < 0 && hours == 0) append("-")
        append(hours)
        if (minutes != 0 || seconds != 0) {
            append(":%02d".format(Locale.ENGLISH, minutes))
            if (seconds != 0) {
                append(":%02d".format(Locale.ENGLISH, seconds))
            }
        }
    }
}

@Suppress("MagicNumber")
internal fun formatTransitionRule(rule: ZoneOffsetTransitionRule): String {
    val month = rule.month.value
    val dayOfWeek = rule.dayOfWeek.value % 7
    val dayIndicator = rule.dayOfMonthIndicator

    val occurrence =
        when {
            dayIndicator < 0 -> 5
            dayIndicator > rule.month.length(false) - 7 -> 5
            else -> ((dayIndicator - 1) / 7) + 1
        }

    val wallTime =
        when (rule.timeDefinition) {
            ZoneOffsetTransitionRule.TimeDefinition.UTC ->
                rule.localTime.plusSeconds(rule.offsetBefore.totalSeconds.toLong())

            ZoneOffsetTransitionRule.TimeDefinition.STANDARD -> {
                if (rule.offsetAfter.totalSeconds > rule.offsetBefore.totalSeconds) {
                    rule.localTime
                } else {
                    rule.localTime.plusSeconds(
                        (rule.offsetBefore.totalSeconds - rule.offsetAfter.totalSeconds).toLong(),
                    )
                }
            }

            else -> rule.localTime
        }

    return buildString {
        append(",M$month.$occurrence.$dayOfWeek")
        if (wallTime.hour != 2 || wallTime.minute != 0 || wallTime.second != 0) {
            append("/${wallTime.hour}")
            if (wallTime.minute != 0 || wallTime.second != 0) {
                append(":%02d".format(Locale.ENGLISH, wallTime.minute))
                if (wallTime.second != 0) {
                    append(":%02d".format(Locale.ENGLISH, wallTime.second))
                }
            }
        }
    }
}
