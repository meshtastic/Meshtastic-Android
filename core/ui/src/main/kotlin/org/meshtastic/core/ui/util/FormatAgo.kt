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
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.now

@Suppress("MagicNumber")
@Composable
fun formatAgo(lastSeenUnix: Int, currentTimeMillis: Long = System.currentTimeMillis()): String {
    val timeInMillis = lastSeenUnix * 1000L
    val diff = currentTimeMillis - timeInMillis

    return if (diff < 60_000L) {
        stringResource(Res.string.now)
    } else {
        DateUtils.getRelativeTimeSpanString(
            timeInMillis,
            currentTimeMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
            .toString()
    }
}
