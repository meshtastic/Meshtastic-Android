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
package org.meshtastic.core.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.BatteryInfoPreviewParameterProvider
import org.meshtastic.core.ui.component.ChannelInfo
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.component.DistanceInfo
import org.meshtastic.core.ui.component.ElevationInfo
import org.meshtastic.core.ui.component.HopsInfo
import org.meshtastic.core.ui.component.IAQScale
import org.meshtastic.core.ui.component.IaqDisplayMode
import org.meshtastic.core.ui.component.IndoorAirQuality
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.QrDialog
import org.meshtastic.core.ui.component.SatelliteCountInfo
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.component.SecurityState
import org.meshtastic.core.ui.component.SignalInfo
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket

class ComponentScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun BatteryInfoTest(@PreviewParameter(BatteryInfoPreviewParameterProvider::class) info: Pair<Int?, Float?>) {
        AppTheme { MaterialBatteryInfo(level = info.first, voltage = info.second) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SignalInfoTest(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
        AppTheme { SignalInfo(node = node) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun NodeChipTest(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
        AppTheme { NodeChip(node = node) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun DistanceInfoTest() {
        AppTheme { DistanceInfo(distance = "12.3 km") }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ElevationInfoTest() {
        AppTheme { ElevationInfo(altitude = 1234, system = Config.DisplayConfig.DisplayUnits.METRIC, suffix = "m") }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun HopsInfoTest() {
        AppTheme { HopsInfo(hops = 3) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SatelliteCountInfoTest() {
        AppTheme { SatelliteCountInfo(satCount = 8) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ChannelInfoTest() {
        AppTheme { ChannelInfo(channel = 1) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ListItemTest() {
        AppTheme { ListItem(text = "Example Item", leadingIcon = Icons.Rounded.Android) {} }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SwitchListItemTest() {
        AppTheme { SwitchListItem(checked = true, text = "Example Switch", onClick = {}) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun TitledCardTest() {
        AppTheme {
            Surface { TitledCard(title = "Example Title") { Box(modifier = Modifier.fillMaxWidth().height(50.dp)) } }
        }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun IAQScaleTest() {
        AppTheme { IAQScale() }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun IndoorAirQualityPillTest() {
        AppTheme { IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Pill) }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun AutoLinkTextTest() {
        AppTheme { AutoLinkText("Check out https://meshtastic.org for more info!") }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SecurityIconTest() {
        AppTheme { Column { SecurityState.entries.forEach { state -> SecurityIcon(securityState = state) } } }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun TransportIconTest() {
        AppTheme {
            Column {
                TransportIcon(transport = MeshPacket.TransportMechanism.TRANSPORT_INTERNAL.value, viaMqtt = false)
                TransportIcon(transport = MeshPacket.TransportMechanism.TRANSPORT_MQTT.value, viaMqtt = true)
                TransportIcon(transport = MeshPacket.TransportMechanism.TRANSPORT_MULTICAST_UDP.value, viaMqtt = false)
            }
        }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ChannelSelectionTest() {
        AppTheme {
            ChannelSelection(
                index = 0,
                title = "LongFast",
                enabled = true,
                isSelected = true,
                onSelected = {},
                channel = Channel.default,
            )
        }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun QrDialogTest() {
        AppTheme {
            QrDialog(
                title = "Share Contact",
                uri = Uri.parse("https://meshtastic.org/u/dummy"),
                qrCode = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
                onDismiss = {},
            )
        }
    }
}
