package com.geeksville.mesh.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tapbacks")
data class TapBack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Int,
    val userId: String,
    val emoji: String,
    val timestamp: Long,
)