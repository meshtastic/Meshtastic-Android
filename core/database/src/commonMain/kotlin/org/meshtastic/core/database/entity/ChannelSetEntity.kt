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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import org.meshtastic.proto.ChannelSet

/**
 * The connected device's channel set, stored per-device (one row per Room database).
 *
 * The channel set was historically a single **global** DataStore (`channel_set.pb`) while messages are per-device, so
 * switching between devices rendered one device's messages against another device's channels — the same channel then
 * appeared multiple times in the conversation list (#4623). Storing it here, in the per-device database, keeps it in
 * lockstep with the messages it labels. The whole [ChannelSet] proto (settings + lora_config) is persisted as one row
 * so semantics match the old whole-proto DataStore exactly.
 */
@Entity(tableName = "channel_set")
data class ChannelSetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "channel_set") val channelSet: ChannelSet,
) {
    companion object {
        /** There is only ever one channel set per device, so every row uses this fixed primary key. */
        const val SINGLETON_ID = 0
    }
}
