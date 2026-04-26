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
package org.meshtastic.feature.auto

/**
 * Snapshot of local device statistics displayed in the Status tab.
 *
 * Mirrors the key metrics shown by [org.meshtastic.feature.widget.LocalStatsWidget]:
 * battery, channel/air utilization, node counts, uptime, and traffic counters.
 */
internal data class CarLocalStats(
    val batteryLevel: Int = 0,
    val hasBattery: Boolean = false,
    val channelUtilization: Float = 0f,
    val airUtilization: Float = 0f,
    val totalNodes: Int = 0,
    val onlineNodes: Int = 0,
    val uptimeSeconds: Int = 0,
    val numPacketsTx: Int = 0,
    val numPacketsRx: Int = 0,
    val numRxDupe: Int = 0,
)
