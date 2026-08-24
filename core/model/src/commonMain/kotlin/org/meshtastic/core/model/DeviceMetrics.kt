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
package org.meshtastic.core.model

import org.meshtastic.core.common.util.nowSeconds

data class DeviceMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val batteryLevel: Int = 0,
    val voltage: Float,
    val channelUtilization: Float,
    val airUtilTx: Float,
    val uptimeSeconds: Int,
) {
    companion object {
        @Suppress("MagicNumber")
        fun currentTime() = nowSeconds.toInt()
    }

    /** Create our model object from a protobuf. */
    constructor(
        p: org.meshtastic.proto.DeviceMetrics,
        telemetryTime: Int = currentTime(),
    ) : this(
        telemetryTime,
        p.battery_level ?: 0,
        p.voltage ?: 0f,
        p.channel_utilization ?: 0f,
        p.air_util_tx ?: 0f,
        p.uptime_seconds ?: 0,
    )
}
