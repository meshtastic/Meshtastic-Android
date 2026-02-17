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
package org.meshtastic.core.ui.util

import android.text.format.DateUtils
import com.meshtastic.core.strings.getString
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.now
import org.meshtastic.core.strings.unknown
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats a given Unix timestamp (in seconds) into a relative "time ago" string.
 *
 * For durations less than a minute, it returns "now". For longer durations, it uses Android's
 * `DateUtils.getRelativeTimeSpanString` to generate a concise, localized, and abbreviated representation (e.g., "5m
 * ago", "2h ago").
 *
 * @param lastSeenUnixSeconds The Unix timestamp in seconds to be formatted.
 * @return A [String] representing the relative time that has passed.
 */
fun formatAgo(lastSeenUnixSeconds: Int): String {
    if (lastSeenUnixSeconds <= 0) return getString(Res.string.unknown)

    val lastSeenDuration = lastSeenUnixSeconds.seconds
    val currentDuration = nowMillis.milliseconds
    val diff = (currentDuration - lastSeenDuration).absoluteValue

    return if (diff < 1.minutes) {
        getString(Res.string.now)
    } else {
        DateUtils.getRelativeTimeSpanString(
            lastSeenDuration.inWholeMilliseconds,
            currentDuration.inWholeMilliseconds,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
            .toString()
    }
}
