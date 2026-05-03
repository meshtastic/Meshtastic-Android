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
package org.meshtastic.core.ui.util

import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.now
import org.meshtastic.core.resources.unknown
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats a given Unix timestamp (in seconds) into a relative "time ago" string.
 *
 * For durations less than a minute, it returns "now". For longer durations, it uses DateFormatter to generate a
 * concise, localized representation (e.g., "5m ago", "2h ago").
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
        DateFormatter.formatRelativeTime(lastSeenDuration.inWholeMilliseconds)
    }
}
