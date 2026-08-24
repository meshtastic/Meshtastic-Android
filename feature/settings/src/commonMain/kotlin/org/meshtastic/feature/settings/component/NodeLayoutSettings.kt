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
package org.meshtastic.feature.settings.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_layout_channel
import org.meshtastic.core.resources.node_layout_compact
import org.meshtastic.core.resources.node_layout_compact_fields_header
import org.meshtastic.core.resources.node_layout_complete
import org.meshtastic.core.resources.node_layout_complete_description
import org.meshtastic.core.resources.node_layout_device_role
import org.meshtastic.core.resources.node_layout_distance_and_bearing
import org.meshtastic.core.resources.node_layout_hops_away
import org.meshtastic.core.resources.node_layout_last_heard_time
import org.meshtastic.core.resources.node_layout_log_icons
import org.meshtastic.core.resources.node_layout_power
import org.meshtastic.core.resources.node_layout_preview
import org.meshtastic.core.resources.node_layout_relative_last_heard
import org.meshtastic.core.resources.node_layout_section_title
import org.meshtastic.core.resources.node_layout_signal_direct_only
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.component.NodeItemCompact
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.NodeListSettingsState
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

/** Node layout density picker and compact field toggles for the Settings screen. */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun NodeLayoutSettings(
    state: NodeListSettingsState,
    onDensityChange: (NodeListDensity) -> Unit,
    onShowPowerChange: (Boolean) -> Unit,
    onShowLastHeardChange: (Boolean) -> Unit,
    onLastHeardIsRelativeChange: (Boolean) -> Unit,
    onShowLocationChange: (Boolean) -> Unit,
    onShowHopsChange: (Boolean) -> Unit,
    onShowSignalChange: (Boolean) -> Unit,
    onShowChannelChange: (Boolean) -> Unit,
    onShowRoleChange: (Boolean) -> Unit,
    onShowTelemetryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSection(modifier = modifier, title = stringResource(Res.string.node_layout_section_title)) {
        // Density picker
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NodeListDensity.entries.forEachIndexed { index, option ->
                val label =
                    when (option) {
                        NodeListDensity.COMPLETE -> stringResource(Res.string.node_layout_complete)
                        NodeListDensity.COMPACT -> stringResource(Res.string.node_layout_compact)
                    }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, NodeListDensity.entries.size),
                    onClick = { onDensityChange(option) },
                    selected = state.density == option,
                    label = { Text(label) },
                )
            }
        }

        // Live preview — positioned above toggles so it doesn't jump with list size changes
        Text(
            text = stringResource(Res.string.node_layout_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )

        val previewNode = remember { previewSampleNode() }
        val localNode = remember { previewLocalNode() }

        Box(modifier = Modifier.animateContentSize().padding(bottom = 8.dp)) {
            when (state.density) {
                NodeListDensity.COMPLETE ->
                    NodeItem(
                        thisNode = localNode,
                        thatNode = previewNode,
                        distanceUnits = 0,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        showTelemetry = state.showTelemetry,
                    )

                NodeListDensity.COMPACT ->
                    NodeItemCompact(
                        thisNode = localNode,
                        thatNode = previewNode,
                        distanceUnits = 0,
                        showPower = state.showPower,
                        showLastHeard = state.showLastHeard,
                        lastHeardIsRelative = state.lastHeardIsRelative,
                        showLocation = state.showLocation,
                        showHops = state.showHops,
                        showSignal = state.showSignal,
                        showChannel = state.showChannel,
                        showRole = state.showRole,
                        showTelemetry = state.showTelemetry,
                    )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(4.dp))

        // Shared toggle — applies to both layouts
        SwitchPreference(
            title = stringResource(Res.string.node_layout_log_icons),
            checked = state.showTelemetry,
            enabled = true,
            onCheckedChange = onShowTelemetryChange,
        )

        if (state.density == NodeListDensity.COMPLETE) {
            Text(
                text = stringResource(Res.string.node_layout_complete_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            // Compact-specific toggles
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Text(
                text = stringResource(Res.string.node_layout_compact_fields_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_power),
                    checked = state.showPower,
                    enabled = true,
                    onCheckedChange = onShowPowerChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_last_heard_time),
                    checked = state.showLastHeard,
                    enabled = true,
                    onCheckedChange = onShowLastHeardChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_relative_last_heard),
                    checked = state.lastHeardIsRelative,
                    enabled = state.showLastHeard,
                    onCheckedChange = onLastHeardIsRelativeChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_distance_and_bearing),
                    checked = state.showLocation,
                    enabled = true,
                    onCheckedChange = onShowLocationChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_hops_away),
                    checked = state.showHops,
                    enabled = true,
                    onCheckedChange = onShowHopsChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_signal_direct_only),
                    checked = state.showSignal,
                    enabled = true,
                    onCheckedChange = onShowSignalChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_channel),
                    checked = state.showChannel,
                    enabled = true,
                    onCheckedChange = onShowChannelChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.node_layout_device_role),
                    checked = state.showRole,
                    enabled = true,
                    onCheckedChange = onShowRoleChange,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Suppress("MagicNumber")
internal fun previewSampleNode(hopsAway: Int = 1): Node = Node(
    num = 0x1A2B3C4D,
    user =
    User(
        id = "!1a2b3c4d",
        long_name = "Solar Hilltop",
        short_name = "SoHi",
        hw_model = HardwareModel.TBEAM,
        role = Config.DeviceConfig.Role.ROUTER,
        public_key = ByteArray(32) { (it * 7).toByte() }.toByteString(),
    ),
    position = Position(latitude_i = 338125110, longitude_i = -1179189760, altitude = 138, sats_in_view = 8),
    lastHeard = (nowSeconds - 300).toInt(),
    channel = 1,
    snr = 10.25F,
    rssi = -67,
    deviceMetrics =
    DeviceMetrics(
        channel_utilization = 3.2F,
        air_util_tx = 1.8F,
        battery_level = 92,
        voltage = 4.1F,
        uptime_seconds = 86400,
    ),
    environmentMetrics =
    EnvironmentMetrics(temperature = 24.5F, relative_humidity = 45.0F, barometric_pressure = 1013.25F),
    isFavorite = true,
    hopsAway = hopsAway,
)

/** Local device node used as reference point for distance calculation in previews. */
@Suppress("MagicNumber")
internal fun previewLocalNode(): Node = Node(
    num = 0xDEADBEEF.toInt(),
    user =
    User(
        id = "!deadbeef",
        long_name = "My Radio",
        short_name = "MyRd",
        hw_model = HardwareModel.HELTEC_V3,
        role = Config.DeviceConfig.Role.CLIENT,
    ),
    position = Position(latitude_i = 338000000, longitude_i = -1179000000, altitude = 50, sats_in_view = 10),
    lastHeard = (nowSeconds - 30).toInt(),
)
