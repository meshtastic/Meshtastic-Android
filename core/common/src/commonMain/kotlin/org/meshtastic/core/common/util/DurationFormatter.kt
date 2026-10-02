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

import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Formats [totalSeconds] as its non-zero day, hour, minute and second parts, largest first: `1d 2h 3m 4s`. Parts finer
 * than [smallest] are dropped, and a duration with no part left prints zero of [smallest]. Negative input counts as
 * zero.
 */
fun formatDuration(
    totalSeconds: Long,
    labels: DurationUnitLabels,
    smallest: DurationUnit = DurationUnit.SECONDS,
): String {
    val parts =
        totalSeconds
            .coerceAtLeast(0)
            .seconds
            .toComponents { days, hours, minutes, seconds, _ ->
                listOf(
                    DurationUnit.DAYS to days,
                    DurationUnit.HOURS to hours.toLong(),
                    DurationUnit.MINUTES to minutes.toLong(),
                    DurationUnit.SECONDS to seconds.toLong(),
                )
            }
            .filter { (unit, _) -> unit >= smallest }
    val shown = parts.filter { (_, count) -> count > 0 }.ifEmpty { listOf(parts.last().first to 0L) }
    return shown.joinToString(" ") { (unit, count) -> formatString(labels.template(unit), count) }
}

/**
 * The per-unit templates [formatDuration] fills with a count as `%1$d`. Screens use the translated set from
 * `core:resources`; [English] is for text that is never translated, such as an exported report.
 */
data class DurationUnitLabels(val days: String, val hours: String, val minutes: String, val seconds: String) {
    companion object {
        val English = DurationUnitLabels(days = "%1\$dd", hours = "%1\$dh", minutes = "%1\$dm", seconds = "%1\$ds")
    }
}

private fun DurationUnitLabels.template(unit: DurationUnit): String = when (unit) {
    DurationUnit.DAYS -> days
    DurationUnit.HOURS -> hours
    DurationUnit.MINUTES -> minutes
    else -> seconds
}
