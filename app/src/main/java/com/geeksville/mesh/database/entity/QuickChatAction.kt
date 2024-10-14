package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_chat")
data class QuickChatAction(
    @PrimaryKey(autoGenerate = true) val uuid: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "mode") val mode: Mode,
    @ColumnInfo(name = "position") val position: Int
) {
    enum class Mode {
        Append,
        Instant,
    }
}
