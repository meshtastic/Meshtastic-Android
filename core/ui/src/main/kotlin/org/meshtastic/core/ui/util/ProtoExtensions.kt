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
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.unknown_age
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import kotlin.time.Duration.Companion.days

private const val SECONDS_TO_MILLIS = 1000L

@Composable
fun Position.formatPositionTime(): String {
    val currentTime = nowMillis
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = (time ?: 0) * SECONDS_TO_MILLIS < sixMonthsAgo
    val timeText =
        if (isOlderThanSixMonths) {
            stringResource(Res.string.unknown_age)
        } else {
            DateUtils.formatDateTime(
                LocalContext.current,
                (time ?: 0) * SECONDS_TO_MILLIS,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
            )
        }
    return timeText
}

fun MeshPacket.toPosition(): Position? {
    val decoded = decoded ?: return null
    return if (decoded.want_response != true) {
        decoded.payload.let { runCatching { Position.ADAPTER.decode(it) }.getOrNull() }
    } else {
        null
    }
}

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists. Only changes are included in the
 * resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
fun getChannelList(new: List<ChannelSettings>, old: List<ChannelSettings>): List<Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) {
            add(
                Channel(
                    role =
                    when (i) {
                        0 -> Channel.Role.PRIMARY
                        in 1..new.lastIndex -> Channel.Role.SECONDARY
                        else -> Channel.Role.DISABLED
                    },
                    index = i,
                    settings = new.getOrNull(i) ?: ChannelSettings(),
                ),
            )
        }
    }
}
