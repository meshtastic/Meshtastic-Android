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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "discovery_session")
data class DiscoverySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "presets_scanned") val presetsScanned: String,
    @ColumnInfo(name = "home_preset") val homePreset: String,
    @ColumnInfo(name = "total_unique_nodes", defaultValue = "0") val totalUniqueNodes: Int = 0,
    @ColumnInfo(name = "avg_channel_utilization", defaultValue = "0.0") val avgChannelUtilization: Double = 0.0,
    @ColumnInfo(name = "total_messages", defaultValue = "0") val totalMessages: Int = 0,
    @ColumnInfo(name = "total_sensor_packets", defaultValue = "0") val totalSensorPackets: Int = 0,
    @ColumnInfo(name = "furthest_node_distance", defaultValue = "0.0") val furthestNodeDistance: Double = 0.0,
    @ColumnInfo(name = "completion_status", defaultValue = "'complete'") val completionStatus: String = "complete",
    @ColumnInfo(name = "ai_summary") val aiSummary: String? = null,
    @ColumnInfo(name = "user_latitude", defaultValue = "0.0") val userLatitude: Double = 0.0,
    @ColumnInfo(name = "user_longitude", defaultValue = "0.0") val userLongitude: Double = 0.0,
    @ColumnInfo(name = "total_dwell_seconds", defaultValue = "0") val totalDwellSeconds: Long = 0,
)
