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
@file:Suppress("MagicNumber")

package org.meshtastic.desktop.spike

import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import java.io.File

/** SPIKE (throwaway): a plausible small mesh around Dallas. Nine nodes, so nothing clusters (CLUSTER_MIN_POINTS = 10). */
internal object SpikeMesh {
    // Fixed "now" so lastHeard reads as minutes/hours, not years. 2026-09-18T12:00:00Z.
    const val NOW = 1789732800

    const val DALLAS_LAT = 32.7767
    const val DALLAS_LON = -96.797

    /** Where every spike artifact lands: the module dir is the test cwd, so this is `desktopApp/build/spike/`. */
    val outDir: File = File("build/spike").apply { mkdirs() }

    val runTag: String = System.getenv("SPIKE_RUN_TAG") ?: "run"

    private fun node(
        num: Int,
        longName: String,
        shortName: String,
        hw: HardwareModel,
        role: Config.DeviceConfig.Role,
        battery: Int,
        hops: Int,
        heardAgoSec: Int,
        dLat: Double?,
        dLon: Double?,
        snr: Float,
        rssi: Int,
        favorite: Boolean = false,
    ): Node = Node(
        num = num,
        user =
        User.Builder()
            .also {
                it.id = "!${num.toUInt().toString(16).padStart(8, '0')}"
                it.long_name = longName
                it.short_name = shortName
                it.hw_model = hw
                it.role = role
            }
            .build(),
        position =
        Position.Builder()
            .also {
                if (dLat != null && dLon != null) {
                    it.latitude_i = ((DALLAS_LAT + dLat) * 1e7).toInt()
                    it.longitude_i = ((DALLAS_LON + dLon) * 1e7).toInt()
                    it.altitude = 140
                    it.sats_in_view = 8
                }
            }
            .build(),
        lastHeard = NOW - heardAgoSec,
        channel = 0,
        snr = snr,
        rssi = rssi,
        deviceMetrics =
        DeviceMetrics.Builder()
            .also {
                it.battery_level = battery
                it.voltage = 3.6f + battery / 250f
                it.channel_utilization = 4.2f
                it.air_util_tx = 1.1f
                it.uptime_seconds = 86_400
            }
            .build(),
        hopsAway = hops,
        isFavorite = favorite,
    )

    val nodes: List<Node> =
        listOf(
            node(0x2b3c4d5e, "Base Camp", "BASE", HardwareModel.HELTEC_V3, Config.DeviceConfig.Role.CLIENT, 95, 0, 5, 0.0, 0.0, 0f, 0, favorite = true),
            node(0x1a2b3c4d, "Ridge Repeater", "RDGE", HardwareModel.RAK4631, Config.DeviceConfig.Role.ROUTER, 88, 0, 40, 0.04, 0.03, 11.5f, -68, favorite = true),
            node(0x3c4d5e6f, "Trailhead", "TRLH", HardwareModel.TBEAM, Config.DeviceConfig.Role.CLIENT, 72, 1, 180, -0.03, 0.045, 8.25f, -92),
            node(0x4d5e6f70, "River Crossing", "RIVR", HardwareModel.T_ECHO, Config.DeviceConfig.Role.CLIENT_MUTE, 64, 1, 420, -0.045, -0.02, 6.0f, -101),
            node(0x5e6f7081, "Summit Solar", "SMMT", HardwareModel.RAK4631, Config.DeviceConfig.Role.ROUTER, 91, 2, 900, 0.05, -0.04, 3.5f, -110, favorite = true),
            node(0x6f708192, "Kayak Dan", "KDAN", HardwareModel.HELTEC_WIRELESS_TRACKER, Config.DeviceConfig.Role.TRACKER, 58, 2, 1500, 0.015, -0.05, 2.75f, -113),
            node(0x708192a3, "Old Fire Lookout", "LOOK", HardwareModel.TBEAM, Config.DeviceConfig.Role.CLIENT, 47, 3, 3600, -0.01, 0.02, -1.5f, -118),
            node(0x8192a3b4.toInt(), "Sarah's Truck", "SRAH", HardwareModel.T_DECK, Config.DeviceConfig.Role.CLIENT, 83, 1, 240, 0.025, 0.01, 9.0f, -85),
            node(0xa3b4c5d6.toInt(), "Ham Shack", "SHCK", HardwareModel.STATION_G2, Config.DeviceConfig.Role.CLIENT, 77, 2, 660, -0.02, -0.035, 4.5f, -104),
        )
}
