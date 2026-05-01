/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "discord_queue")
data class DiscordQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long,
    val fromNode: Int,
    val sent: Boolean = false
)
