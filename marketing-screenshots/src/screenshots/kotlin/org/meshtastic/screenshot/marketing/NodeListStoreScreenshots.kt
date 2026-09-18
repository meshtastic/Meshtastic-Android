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

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lucianosantos.storescreenshots.FormFactor
import dev.lucianosantos.storescreenshots.ScreenshotStyle
import dev.lucianosantos.storescreenshots.StoreScreenshotsTest
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

/** SPIKE (throwaway): does a commonMain CMP screen render under this plugin's Robolectric NATIVE path? */
class NodeListStoreScreenshots : StoreScreenshotsTest(FormFactor.Phone, style = ScreenshotStyle(edgeToEdge = false)) {

    @Test
    fun nodeList() =
        screenshot(
            locales = listOf("en-US", "de-DE"),
            titleRes = R.string.screenshot_nodes_title,
            descriptionRes = R.string.screenshot_nodes_desc,
            fileName = "01_nodes",
        ) {
            NodeListMarketingScreen()
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeListMarketingScreen() {
    val ourNode = SampleMesh.nodes.first()
    AppTheme(darkTheme = false, dynamicColor = false) {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.nodes)) }) }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
                items(SampleMesh.nodes, key = { it.num }) { node ->
                    NodeItem(
                        thisNode = ourNode,
                        thatNode = node,
                        distanceUnits = MeasurementSystem.METRIC,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        isActive = node.num == ourNode.num,
                    )
                }
            }
        }
    }
}

/** A plausible small mesh: 10 nodes, batteries 40-95 %, hops 0-3, most with a position near the local node. */
private object SampleMesh {
    // Fixed "now" so lastHeard reads as minutes/hours, not years. 2026-09-18T12:00:00Z.
    private const val NOW = 1789732800

    private fun node(
        num: Int,
        longName: String,
        shortName: String,
        hw: HardwareModel,
        role: Config.DeviceConfig.Role,
        battery: Int,
        hops: Int,
        heardAgoSec: Int,
        latI: Int?,
        lonI: Int?,
        snr: Float,
        rssi: Int,
        favorite: Boolean = false,
    ): Node =
        Node(
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
                    if (latI != null && lonI != null) {
                        it.latitude_i = latI
                        it.longitude_i = lonI
                        it.altitude = 120
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
            node(0x2b3c4d5e, "Base Camp", "BASE", HardwareModel.HELTEC_V3, Config.DeviceConfig.Role.CLIENT, 95, 0, 5, 338125110, -1179189760, 0f, 0, favorite = true),
            node(0x1a2b3c4d, "Ridge Repeater", "RDGE", HardwareModel.RAK4631, Config.DeviceConfig.Role.ROUTER, 88, 0, 40, 338225110, -1179089760, 11.5f, -68, favorite = true),
            node(0x3c4d5e6f, "Trailhead", "TRLH", HardwareModel.TBEAM, Config.DeviceConfig.Role.CLIENT, 72, 1, 180, 338025110, -1179289760, 8.25f, -92),
            node(0x4d5e6f70, "River Crossing", "RIVR", HardwareModel.T_ECHO, Config.DeviceConfig.Role.CLIENT_MUTE, 64, 1, 420, 337925110, -1179389760, 6.0f, -101),
            node(0x5e6f7081, "Summit Solar", "SMMT", HardwareModel.RAK4631, Config.DeviceConfig.Role.ROUTER, 91, 2, 900, 338625110, -1178789760, 3.5f, -110, favorite = true),
            node(0x6f708192, "Kayak Dan", "KDAN", HardwareModel.HELTEC_WIRELESS_TRACKER, Config.DeviceConfig.Role.TRACKER, 58, 2, 1500, 337825110, -1179489760, 2.75f, -113),
            node(0x708192a3, "Old Fire Lookout", "LOOK", HardwareModel.TBEAM, Config.DeviceConfig.Role.CLIENT, 47, 3, 3600, 338925110, -1178489760, -1.5f, -118),
            node(0x8192a3b4.toInt(), "Sarah's Truck", "SRAH", HardwareModel.T_DECK, Config.DeviceConfig.Role.CLIENT, 83, 1, 240, 338075110, -1179239760, 9.0f, -85),
            node(0x92a3b4c5.toInt(), "Marina Dock", "DOCK", HardwareModel.HELTEC_V3, Config.DeviceConfig.Role.CLIENT, 40, 3, 7200, null, null, -4.0f, -121),
            node(0xa3b4c5d6.toInt(), "Ham Shack", "SHCK", HardwareModel.STATION_G2, Config.DeviceConfig.Role.CLIENT, 77, 2, 660, 338325110, -1178989760, 4.5f, -104),
        )
}
