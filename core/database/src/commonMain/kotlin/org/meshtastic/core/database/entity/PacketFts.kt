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
import androidx.room3.Fts5

/**
 * FTS5 virtual table that mirrors [Packet.messageText] for full-text search. Room auto-generates INSERT/UPDATE/DELETE
 * triggers to keep this table in sync with the content entity ([Packet]).
 */
@Fts5(contentEntity = Packet::class)
@Entity(tableName = "packet_fts")
data class PacketFts(@ColumnInfo(name = "message_text") val messageText: String)
