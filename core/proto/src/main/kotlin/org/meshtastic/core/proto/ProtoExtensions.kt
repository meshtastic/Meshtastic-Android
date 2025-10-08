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

package org.meshtastic.core.proto

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ChannelProtos.ChannelSettings
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.MeshProtos.Position
import org.meshtastic.proto.channel
import org.meshtastic.proto.channelSettings
import java.text.DateFormat
import kotlin.time.Duration.Companion.days

private const val SECONDS_TO_MILLIS = 1000L

@Composable
fun MeshProtos.Position.formatPositionTime(dateFormat: DateFormat): String {
    val currentTime = System.currentTimeMillis()
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = time * SECONDS_TO_MILLIS < sixMonthsAgo
    val timeText =
        if (isOlderThanSixMonths) {
            stringResource(id = org.meshtastic.core.strings.R.string.unknown_age)
        } else {
            dateFormat.format(time * SECONDS_TO_MILLIS)
        }
    return timeText
}

fun MeshPacket.toPosition(): Position? = if (!decoded.wantResponse) {
    runCatching { Position.parseFrom(decoded.payload) }.getOrNull()
} else {
    null
}

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists. Only changes are included in the
 * resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
fun getChannelList(new: List<ChannelSettings>, old: List<ChannelSettings>): List<ChannelProtos.Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) {
            add(
                channel {
                    role =
                        when (i) {
                            0 -> ChannelProtos.Channel.Role.PRIMARY
                            in 1..new.lastIndex -> ChannelProtos.Channel.Role.SECONDARY
                            else -> ChannelProtos.Channel.Role.DISABLED
                        }
                    index = i
                    settings = new.getOrNull(i) ?: channelSettings {}
                },
            )
        }
    }
}
