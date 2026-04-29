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
    tableName = "discovery_preset_result",
    foreignKeys =
    [
        ForeignKey(
            entity = DiscoverySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class DiscoveryPresetResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "preset_name") val presetName: String,
    @ColumnInfo(name = "dwell_duration_seconds", defaultValue = "0") val dwellDurationSeconds: Long = 0,
    @ColumnInfo(name = "unique_nodes", defaultValue = "0") val uniqueNodes: Int = 0,
    @ColumnInfo(name = "direct_neighbor_count", defaultValue = "0") val directNeighborCount: Int = 0,
    @ColumnInfo(name = "mesh_neighbor_count", defaultValue = "0") val meshNeighborCount: Int = 0,
    @ColumnInfo(name = "message_count", defaultValue = "0") val messageCount: Int = 0,
    @ColumnInfo(name = "sensor_packet_count", defaultValue = "0") val sensorPacketCount: Int = 0,
    @ColumnInfo(name = "avg_channel_utilization", defaultValue = "0.0") val avgChannelUtilization: Double = 0.0,
    @ColumnInfo(name = "avg_airtime_rate", defaultValue = "0.0") val avgAirtimeRate: Double = 0.0,
    @ColumnInfo(name = "packet_success_rate", defaultValue = "0.0") val packetSuccessRate: Double = 0.0,
    @ColumnInfo(name = "packet_failure_rate", defaultValue = "0.0") val packetFailureRate: Double = 0.0,
    @ColumnInfo(name = "ai_summary") val aiSummary: String? = null,
    @ColumnInfo(name = "num_packets_tx", defaultValue = "0") val numPacketsTx: Int = 0,
    @ColumnInfo(name = "num_packets_rx", defaultValue = "0") val numPacketsRx: Int = 0,
    @ColumnInfo(name = "num_packets_rx_bad", defaultValue = "0") val numPacketsRxBad: Int = 0,
    @ColumnInfo(name = "num_rx_dupe", defaultValue = "0") val numRxDupe: Int = 0,
    @ColumnInfo(name = "num_tx_relay", defaultValue = "0") val numTxRelay: Int = 0,
    @ColumnInfo(name = "num_tx_relay_canceled", defaultValue = "0") val numTxRelayCanceled: Int = 0,
    @ColumnInfo(name = "num_online_nodes", defaultValue = "0") val numOnlineNodes: Int = 0,
    @ColumnInfo(name = "num_total_nodes", defaultValue = "0") val numTotalNodes: Int = 0,
    @ColumnInfo(name = "uptime_seconds", defaultValue = "0") val uptimeSeconds: Int = 0,
)
