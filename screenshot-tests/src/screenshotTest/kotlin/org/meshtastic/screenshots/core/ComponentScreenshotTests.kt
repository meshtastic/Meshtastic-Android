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
package org.meshtastic.screenshots.core

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.ChannelInfoPreview
import org.meshtastic.core.ui.component.ChannelItemPreview
import org.meshtastic.core.ui.component.DistanceInfoPreview
import org.meshtastic.core.ui.component.HopsInfoPreview
import org.meshtastic.core.ui.component.LastHeardInfoPreview
import org.meshtastic.core.ui.component.ListItemDisabledPreview
import org.meshtastic.core.ui.component.ListItemPreview
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.MaterialBluetoothSignalInfo
import org.meshtastic.core.ui.component.SatelliteCountInfoPreview
import org.meshtastic.core.ui.component.SignalInfo
import org.meshtastic.core.ui.component.SwitchListItemPreview
import org.meshtastic.core.ui.component.TitledCardPreview
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotChannelItem() {
    ChannelItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotListItem() {
    ListItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotListItemDisabled() {
    ListItemDisabledPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSwitchListItem() {
    SwitchListItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotHopsInfo() {
    HopsInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSignalInfoSimple() {
    AppTheme { SignalInfo(node = Node(num = 1, lastHeard = 0, channel = 0, snr = 12.5F, rssi = -42, hopsAway = 0)) }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSignalInfo(@PreviewParameter(NodePreviewParameterProvider::class) node: Node) {
    AppTheme { SignalInfo(node = node) }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDistanceInfo() {
    DistanceInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotLastHeardInfo() {
    LastHeardInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMaterialBatteryInfo() {
    AppTheme { MaterialBatteryInfo(level = 85, voltage = 3.7F) }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTitledCard() {
    TitledCardPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotChannelInfo() {
    ChannelInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSatelliteCountInfo() {
    SatelliteCountInfoPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMaterialBluetoothSignalInfo() {
    AppTheme { Surface { MaterialBluetoothSignalInfo(rssi = -65) } }
}
