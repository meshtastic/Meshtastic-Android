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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.now
import org.meshtastic.core.resources.unknown
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats a given Unix timestamp (in seconds) into a relative "time ago" string.
 *
 * For durations less than a minute, it returns [nowText]. For longer durations, it uses DateFormatter to generate a
 * concise, localized representation (e.g., "5m ago", "2h ago").
 *
 * @param lastSeenUnixSeconds The Unix timestamp in seconds to be formatted.
 * @param unknownText Text to display when the timestamp is invalid (≤ 0).
 * @param nowText Text to display when the duration is less than a minute.
 * @return A [String] representing the relative time that has passed.
 */
fun formatAgo(lastSeenUnixSeconds: Int, unknownText: String, nowText: String): String {
    if (lastSeenUnixSeconds <= 0) return unknownText

    val lastSeenDuration = lastSeenUnixSeconds.seconds
    val currentDuration = nowMillis.milliseconds
    val diff = (currentDuration - lastSeenDuration).absoluteValue

    return if (diff < 1.minutes) {
        nowText
    } else {
        DateFormatter.formatRelativeTime(lastSeenDuration.inWholeMilliseconds)
    }
}

/** Composable convenience overload that resolves string resources automatically. */
@Composable
fun formatAgo(lastSeenUnixSeconds: Int): String =
    formatAgo(lastSeenUnixSeconds, stringResource(Res.string.unknown), stringResource(Res.string.now))
