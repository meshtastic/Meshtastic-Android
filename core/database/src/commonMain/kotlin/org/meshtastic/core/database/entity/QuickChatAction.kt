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
import org.meshtastic.core.model.QuickChatAction as QuickChatActionModel

@Entity(tableName = "quick_chat")
data class QuickChatAction(
    @PrimaryKey(autoGenerate = true) val uuid: Long = 0L,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "message") val message: String = "",
    @ColumnInfo(name = "mode") val mode: Mode = Mode.Instant,
    @ColumnInfo(name = "position") val position: Int,
) {
    enum class Mode {
        Append,
        Instant,
    }
}

fun QuickChatAction.asExternalModel() = QuickChatActionModel(
    uuid = uuid,
    name = name,
    message = message,
    mode =
    when (mode) {
        QuickChatAction.Mode.Append -> QuickChatActionModel.Mode.Append
        QuickChatAction.Mode.Instant -> QuickChatActionModel.Mode.Instant
    },
    position = position,
)

fun QuickChatActionModel.asEntity() = QuickChatAction(
    uuid = uuid,
    name = name,
    message = message,
    mode =
    when (mode) {
        QuickChatActionModel.Mode.Append -> QuickChatAction.Mode.Append
        QuickChatActionModel.Mode.Instant -> QuickChatAction.Mode.Instant
    },
    position = position,
)
