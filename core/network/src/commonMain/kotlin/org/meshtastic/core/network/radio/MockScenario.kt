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
package org.meshtastic.core.network.radio

import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position as ProtoPosition

/**
 * The mesh a [MockRadioTransport] simulates: our node, its peers, and the history it seeds on connect. The address
 * suffix after `m` picks it, so a scenario other than [DEMO] is reachable only by naming it, never from the picker.
 */
@Suppress("detekt:MagicNumber")
internal data class MockScenario(
    val myNode: Int,
    val longName: String,
    val shortName: String,
    val hwModel: HardwareModel,
    val firmwareVersion: String,
    val nodeStatus: String,
    val position: SimPosition,
    val peers: List<SimPeer>,
    /** (index into [peers], text) pairs, oldest first. */
    val channelConversation: List<Pair<Int, String>>,
    val directPeerIndex: Int,
    /** Texts from [directPeerIndex] to us, oldest first. */
    val directConversation: List<String>,
    /** The first peers, which report device telemetry on connect and, with [liveTelemetry], every tick after. */
    val telemetryPeerCount: Int,
    val weatherPeerIndexes: List<Int>,
    /** Status line of peer 1, the one that reports a node status. */
    val peerNodeStatus: String,
    /** Keep reporting telemetry after the seed pass. Off where a stable picture matters more than a live one. */
    val liveTelemetry: Boolean,
) {
    companion object {
        /** The suffix that selects [SHOWCASE]: `mshowcase`. */
        const val SHOWCASE_ADDRESS = "showcase"

        fun forAddress(address: String): MockScenario = if (address == SHOWCASE_ADDRESS) SHOWCASE else DEMO

        private const val DEMO_NODE = 0x42424242

        /**
         * Demo Mode's mesh. Deterministic on purpose, so the demo is the same every launch for support requests and
         * store reviews. Spread over ~15 km so the map has something to fit.
         */
        val DEMO =
            MockScenario(
                myNode = DEMO_NODE,
                longName = "Demo Handset",
                shortName = "DEMO",
                hwModel = HardwareModel.ANDROID_SIM,
                firmwareVersion = "9.9.9.abcdefg",
                nodeStatus = "Running Demo Mode — no radio attached.",
                position = SimPosition(latitude = 32.776665, longitude = -96.796989, altitude = 138),
                peers =
                listOf(
                    SimPeer(
                        num = DEMO_NODE + 1,
                        longName = "Riverside Base",
                        shortName = "RVSD",
                        hwModel = HardwareModel.HELTEC_V3,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = 32.802,
                        longitude = -96.769,
                        altitude = 152,
                        batteryLevel = 92,
                        voltage = 4.09f,
                        snr = 11.5f,
                        rssi = -62,
                        hops = 0,
                        secondsSinceHeard = 45,
                        uptimeSeconds = 128_400,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 2,
                        longName = "Trail Runner",
                        shortName = "TRLR",
                        hwModel = HardwareModel.TRACKER_T1000_E,
                        role = Config.DeviceConfig.Role.TRACKER,
                        latitude = 32.7605,
                        longitude = -96.8305,
                        altitude = 145,
                        batteryLevel = 64,
                        voltage = 3.87f,
                        snr = 6.25f,
                        rssi = -84,
                        hops = 0,
                        secondsSinceHeard = 130,
                        uptimeSeconds = 41_900,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 3,
                        longName = "Oak Cliff Repeater",
                        shortName = "OAKR",
                        hwModel = HardwareModel.RAK4631,
                        role = Config.DeviceConfig.Role.ROUTER,
                        latitude = 32.7395,
                        longitude = -96.8215,
                        altitude = 189,
                        batteryLevel = 100,
                        voltage = 4.14f,
                        snr = 9.0f,
                        rssi = -71,
                        hops = 0,
                        secondsSinceHeard = 20,
                        uptimeSeconds = 903_600,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 4,
                        longName = "Deep Ellum Handheld",
                        shortName = "DEEP",
                        hwModel = HardwareModel.T_DECK,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = 32.7842,
                        longitude = -96.7845,
                        altitude = 141,
                        batteryLevel = 47,
                        voltage = 3.74f,
                        snr = -2.5f,
                        rssi = -103,
                        hops = 1,
                        secondsSinceHeard = 320,
                        uptimeSeconds = 9_800,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 5,
                        longName = "Rooftop Weather",
                        shortName = "WTHR",
                        hwModel = HardwareModel.HELTEC_MESH_NODE_T114,
                        role = Config.DeviceConfig.Role.SENSOR,
                        latitude = 32.8145,
                        longitude = -96.8055,
                        altitude = 205,
                        batteryLevel = 88,
                        voltage = 4.02f,
                        snr = 4.75f,
                        rssi = -91,
                        hops = 1,
                        secondsSinceHeard = 210,
                        uptimeSeconds = 512_000,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 6,
                        longName = "Lakeside Solar",
                        shortName = "LAKE",
                        hwModel = HardwareModel.STATION_G2,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = 32.8365,
                        longitude = -96.7325,
                        altitude = 167,
                        batteryLevel = 73,
                        voltage = 3.94f,
                        snr = 1.5f,
                        rssi = -98,
                        hops = 2,
                        secondsSinceHeard = 640,
                        uptimeSeconds = 254_300,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 7,
                        longName = "Bike Courier",
                        shortName = "BIKE",
                        hwModel = HardwareModel.TBEAM,
                        role = Config.DeviceConfig.Role.TRACKER,
                        latitude = 32.7688,
                        longitude = -96.7492,
                        altitude = 134,
                        batteryLevel = 31,
                        voltage = 3.62f,
                        snr = -6.5f,
                        rssi = -112,
                        hops = 2,
                        secondsSinceHeard = 1_180,
                        uptimeSeconds = 3_600,
                    ),
                    SimPeer(
                        num = DEMO_NODE + 8,
                        longName = "Field Kit Echo",
                        shortName = "ECHO",
                        hwModel = HardwareModel.T_ECHO,
                        role = Config.DeviceConfig.Role.CLIENT_MUTE,
                        latitude = 32.7215,
                        longitude = -96.7738,
                        altitude = 158,
                        batteryLevel = 56,
                        voltage = 3.81f,
                        snr = 3.25f,
                        rssi = -95,
                        hops = 1,
                        secondsSinceHeard = 2_400,
                        uptimeSeconds = 76_500,
                    ),
                ),
                channelConversation =
                listOf(
                    0 to "Morning all — base station is back online after the power cut.",
                    2 to "Copy that. Repeater on Oak Cliff is holding steady, 100% battery.",
                    1 to "Out on the trail loop, signal is solid the whole way today.",
                    4 to "Rooftop sensor reading 18.5C and 47% humidity if anyone cares.",
                    0 to "Nice. Net check complete, everyone reporting in.",
                ),
                directPeerIndex = 0,
                directConversation =
                listOf(
                    "Hey, are you still planning to bring the spare antenna tomorrow?",
                    "No rush — just let me know before you set off.",
                ),
                telemetryPeerCount = 4,
                weatherPeerIndexes = listOf(4),
                peerNodeStatus = "Solar powered, up on the ridge.",
                liveTelemetry = true,
            )

        private const val SHOWCASE_NODE = 0x2b3c4d5e
        private const val SHOWCASE_LAT = 32.7767
        private const val SHOWCASE_LON = -96.797

        /** A peer [minutes] ago, 30 s into that minute so a slow frame cannot tip a "3 min" label over. */
        private fun heard(minutes: Int): Int = if (minutes == 0) 5 else minutes * 60 + 30

        /**
         * The store listing's mesh: a hiking group around a base camp, every node named for a place or a person, and a
         * channel thread that tells one morning. Static after the seed pass, so every capture shows the same numbers.
         */
        val SHOWCASE =
            MockScenario(
                myNode = SHOWCASE_NODE,
                longName = "Base Camp",
                shortName = "BASE",
                hwModel = HardwareModel.HELTEC_V3,
                firmwareVersion = "2.7.26.54e0d8d",
                nodeStatus = "Base camp radio, on grid power.",
                position = SimPosition(latitude = SHOWCASE_LAT, longitude = SHOWCASE_LON, altitude = 150),
                peers =
                listOf(
                    SimPeer(
                        num = 0x1a2b3c4d,
                        longName = "Ridge Top",
                        shortName = "RDGE",
                        hwModel = HardwareModel.RAK4631,
                        role = Config.DeviceConfig.Role.ROUTER,
                        latitude = SHOWCASE_LAT + 0.04,
                        longitude = SHOWCASE_LON + 0.03,
                        altitude = 214,
                        batteryLevel = 88,
                        voltage = 4.02f,
                        snr = 11.5f,
                        rssi = -68,
                        hops = 0,
                        secondsSinceHeard = heard(2),
                        uptimeSeconds = 19 * 86_400 + 7 * 3_600,
                    ),
                    SimPeer(
                        num = 0x5e6f7081,
                        longName = "Summit Solar",
                        shortName = "SMMT",
                        hwModel = HardwareModel.RAK4631,
                        role = Config.DeviceConfig.Role.ROUTER,
                        latitude = SHOWCASE_LAT + 0.05,
                        longitude = SHOWCASE_LON - 0.04,
                        altitude = 249,
                        batteryLevel = 91,
                        voltage = 4.09f,
                        snr = 3.5f,
                        rssi = -110,
                        hops = 2,
                        secondsSinceHeard = heard(15),
                        uptimeSeconds = 63 * 86_400 + 2 * 3_600,
                    ),
                    SimPeer(
                        num = 0x3c4d5e6f,
                        longName = "Trailhead",
                        shortName = "TRLH",
                        hwModel = HardwareModel.TBEAM,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = SHOWCASE_LAT - 0.03,
                        longitude = SHOWCASE_LON + 0.045,
                        altitude = 231,
                        batteryLevel = 72,
                        voltage = 3.89f,
                        snr = 8.25f,
                        rssi = -92,
                        hops = 1,
                        secondsSinceHeard = heard(3),
                        uptimeSeconds = 6 * 3_600,
                    ),
                    SimPeer(
                        num = 0x8192a3b4.toInt(),
                        longName = "Sarah's Truck",
                        shortName = "SRAH",
                        hwModel = HardwareModel.T_DECK,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = SHOWCASE_LAT + 0.025,
                        longitude = SHOWCASE_LON + 0.01,
                        altitude = 176,
                        batteryLevel = 83,
                        voltage = 3.98f,
                        snr = 9.0f,
                        rssi = -85,
                        hops = 1,
                        secondsSinceHeard = heard(4),
                        uptimeSeconds = 4 * 3_600,
                    ),
                    SimPeer(
                        num = 0x4d5e6f70,
                        longName = "River Crossing",
                        shortName = "RIVR",
                        hwModel = HardwareModel.T_ECHO,
                        role = Config.DeviceConfig.Role.CLIENT_MUTE,
                        latitude = SHOWCASE_LAT - 0.045,
                        longitude = SHOWCASE_LON - 0.02,
                        altitude = 128,
                        batteryLevel = 64,
                        voltage = 3.84f,
                        snr = 6.0f,
                        rssi = -101,
                        hops = 1,
                        secondsSinceHeard = heard(7),
                        uptimeSeconds = 2 * 86_400,
                    ),
                    SimPeer(
                        num = 0xa3b4c5d6.toInt(),
                        longName = "Ham Shack",
                        shortName = "SHCK",
                        hwModel = HardwareModel.STATION_G2,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = SHOWCASE_LAT - 0.02,
                        longitude = SHOWCASE_LON - 0.035,
                        altitude = 162,
                        batteryLevel = 77,
                        voltage = 3.92f,
                        snr = 4.5f,
                        rssi = -104,
                        hops = 2,
                        secondsSinceHeard = heard(11),
                        uptimeSeconds = 41 * 86_400,
                    ),
                    SimPeer(
                        num = 0x6f708192,
                        longName = "Kayak Dan",
                        shortName = "KDAN",
                        hwModel = HardwareModel.HELTEC_WIRELESS_TRACKER,
                        role = Config.DeviceConfig.Role.TRACKER,
                        latitude = SHOWCASE_LAT + 0.012,
                        longitude = SHOWCASE_LON - 0.042,
                        altitude = 120,
                        batteryLevel = 58,
                        voltage = 3.79f,
                        snr = 2.75f,
                        rssi = -113,
                        hops = 2,
                        secondsSinceHeard = heard(25),
                        uptimeSeconds = 3 * 3_600,
                    ),
                    SimPeer(
                        num = 0x708192a3,
                        longName = "Old Fire Lookout",
                        shortName = "LOOK",
                        hwModel = HardwareModel.TBEAM,
                        role = Config.DeviceConfig.Role.CLIENT,
                        latitude = SHOWCASE_LAT - 0.01,
                        longitude = SHOWCASE_LON + 0.02,
                        altitude = 268,
                        batteryLevel = 47,
                        voltage = 3.72f,
                        snr = -1.5f,
                        rssi = -118,
                        hops = 3,
                        secondsSinceHeard = heard(60),
                        uptimeSeconds = 9 * 86_400,
                    ),
                ),
                channelConversation =
                listOf(
                    2 to "Heading up from the trailhead now, 4 of us",
                    3 to "Parked at the overflow lot, radio on",
                    2 to "Made the ridge, good signal back to base",
                    6 to "Water at the crossing is low, safe to ford",
                    3 to "Bringing the truck around to the lower lot at 3",
                    2 to "Lunch at the lookout, back on the air in 30",
                ),
                directPeerIndex = 6,
                directConversation = listOf("Paddling past the crossing now.", "Can you see me on the map yet?"),
                telemetryPeerCount = 4,
                weatherPeerIndexes = listOf(0),
                peerNodeStatus = "Solar powered, up on the summit.",
                liveTelemetry = false,
            )
    }
}

internal data class SimPeer(
    val num: Int,
    val longName: String,
    val shortName: String,
    val hwModel: HardwareModel,
    val role: Config.DeviceConfig.Role,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val batteryLevel: Int,
    val voltage: Float,
    val snr: Float,
    val rssi: Int,
    val hops: Int,
    val secondsSinceHeard: Int,
    val uptimeSeconds: Int,
)

/** Latitude/longitude/altitude triple, converted to the proto's scaled-integer representation on demand. */
@Suppress("detekt:MagicNumber")
internal data class SimPosition(val latitude: Double, val longitude: Double, val altitude: Int) {
    fun toProto() = ProtoPosition.Builder()
        .also { wb ->
            wb.latitude_i = org.meshtastic.core.model.Position.degI(latitude)
            wb.longitude_i = org.meshtastic.core.model.Position.degI(longitude)
            wb.altitude = altitude
            wb.time = nowSeconds.toInt()
            // 32 bits is "full precision"; the coarse end of the scale draws a large uncertainty circle instead of
            // placing the node where it actually is.
            wb.precision_bits = 32
            wb.sats_in_view = 9
            wb.location_source = ProtoPosition.LocSource.LOC_INTERNAL
        }
        .build()
}
