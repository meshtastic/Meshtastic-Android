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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Raw Intl/Date JS interop for this one function, isolated here per core:ble's WebBluetoothApi.kt idiom.
private fun currentTimeZoneOffsetMinutes(): Int = js("new Date().getTimezoneOffset()")

private fun currentTimeZoneAbbreviation(): String = js(
    """(new Intl.DateTimeFormat(undefined, { timeZoneName: 'short' })
            .formatToParts(new Date()).find((p) => p.type === 'timeZoneName')?.value ?? 'UTC')""",
)

private const val MINUTES_PER_HOUR = 60

/**
 * Web -- a real, current-moment value built from `Intl`/`Date`, not a hardcoded fallback (unlike iOS's "GMT0").
 * `Date.getTimezoneOffset()` already uses POSIX's sign convention (positive west of UTC), so no negation is needed,
 * unlike the JVM actual's `java.time.ZoneOffset` (which is positive east of UTC and must be flipped).
 *
 * Known limitation, not fixed here: unlike the JVM actual's full `ZoneOffsetTransitionRule` walk, this reflects only
 * today's offset -- a zone that observes DST will be off by the DST delta for half the year, since the browser has no
 * API enumerating future transition dates the way `java.time` does. The result is only a prefill for an editable text
 * field (the "use phone tz" button below), never applied silently, so an occasionally-imprecise value is an acceptable,
 * visible tradeoff.
 */
@Composable
actual fun rememberSystemTimeZonePosixString(): String = remember {
    val offsetMinutes = currentTimeZoneOffsetMinutes()
    val hours = offsetMinutes / MINUTES_PER_HOUR
    val minutes = kotlin.math.abs(offsetMinutes % MINUTES_PER_HOUR)
    val abbreviation = currentTimeZoneAbbreviation()
    buildString {
        append(if (abbreviation.all { it.isLetter() }) abbreviation else "<$abbreviation>")
        if (offsetMinutes < 0 && hours == 0) append("-")
        append(hours)
        if (minutes != 0) append(":").append(minutes.toString().padStart(2, '0'))
    }
}
