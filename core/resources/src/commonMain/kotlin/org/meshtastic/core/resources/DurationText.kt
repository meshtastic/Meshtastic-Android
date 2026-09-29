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
package org.meshtastic.core.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DurationUnitLabels
import kotlin.time.DurationUnit
import org.meshtastic.core.common.util.formatDuration as formatDurationWith

/** [totalSeconds] as translated duration text, e.g. `1d 2h 3m`; see [formatDurationWith] for [smallest]. */
@Composable
fun formatDuration(totalSeconds: Long, smallest: DurationUnit = DurationUnit.SECONDS): String {
    // Loaded without arguments on purpose: each template keeps its %1$d for formatDurationWith to fill.
    val labels =
        DurationUnitLabels(
            days = stringResource(Res.string.duration_days_short),
            hours = stringResource(Res.string.duration_hours_short),
            minutes = stringResource(Res.string.duration_minutes_short),
            seconds = stringResource(Res.string.duration_seconds_short),
        )
    return formatDurationWith(totalSeconds, labels, smallest)
}

/** The suspending counterpart of [formatDuration], for text built outside composition such as notifications. */
suspend fun formatDurationSuspend(totalSeconds: Long, smallest: DurationUnit = DurationUnit.SECONDS): String {
    val labels =
        DurationUnitLabels(
            days = getStringSuspend(Res.string.duration_days_short),
            hours = getStringSuspend(Res.string.duration_hours_short),
            minutes = getStringSuspend(Res.string.duration_minutes_short),
            seconds = getStringSuspend(Res.string.duration_seconds_short),
        )
    return formatDurationWith(totalSeconds, labels, smallest)
}
