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
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "discovered_node",
    foreignKeys =
    [
        ForeignKey(
            entity = DiscoveryPresetResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_result_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["preset_result_id"]), Index(value = ["node_num"])],
)
data class DiscoveredNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "preset_result_id") val presetResultId: Long,
    @ColumnInfo(name = "node_num") val nodeNum: Long,
    @ColumnInfo(name = "short_name") val shortName: String? = null,
    @ColumnInfo(name = "long_name") val longName: String? = null,
    @ColumnInfo(name = "neighbor_type", defaultValue = "'direct'") val neighborType: String = "direct",
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "distance_from_user") val distanceFromUser: Double? = null,
    @ColumnInfo(name = "hop_count", defaultValue = "0") val hopCount: Int = 0,
    @ColumnInfo(name = "snr", defaultValue = "0") val snr: Float = 0f,
    @ColumnInfo(name = "rssi", defaultValue = "0") val rssi: Int = 0,
    @ColumnInfo(name = "message_count", defaultValue = "0") val messageCount: Int = 0,
    @ColumnInfo(name = "sensor_packet_count", defaultValue = "0") val sensorPacketCount: Int = 0,
)
