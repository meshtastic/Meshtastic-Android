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
package org.meshtastic.feature.car.screens

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import org.meshtastic.feature.car.model.ChannelUi

/**
 * Builds channel chip actions for the messaging screen header. Each chip shows channel name + unread badge, single-tap
 * switches.
 */
object ChannelChipBuilder {

    fun buildChannelActionStrip(channels: List<ChannelUi>, onChannelSelected: (Int) -> Unit): ActionStrip {
        val builder = ActionStrip.Builder()

        channels.forEach { channel ->
            val title =
                if (channel.unreadCount > 0) {
                    "${channel.name} (${channel.unreadCount})"
                } else {
                    channel.name
                }

            builder.addAction(
                Action.Builder().setTitle(title).setOnClickListener { onChannelSelected(channel.index) }.build(),
            )
        }

        return builder.build()
    }
}
