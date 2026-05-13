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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_layout_channel
import org.meshtastic.core.resources.node_layout_compact
import org.meshtastic.core.resources.node_layout_complete
import org.meshtastic.core.resources.node_layout_complete_description
import org.meshtastic.core.resources.node_layout_device_role
import org.meshtastic.core.resources.node_layout_distance_and_bearing
import org.meshtastic.core.resources.node_layout_hops_away
import org.meshtastic.core.resources.node_layout_last_heard_time
import org.meshtastic.core.resources.node_layout_log_icons
import org.meshtastic.core.resources.node_layout_power
import org.meshtastic.core.resources.node_layout_relative_last_heard
import org.meshtastic.core.resources.node_layout_section_title
import org.meshtastic.core.resources.node_layout_signal_direct_only
import org.meshtastic.core.ui.component.SwitchPreference

/** Node layout density picker and compact field toggles for the Settings screen. */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun NodeLayoutSettings(
    density: NodeListDensity,
    onDensityChange: (NodeListDensity) -> Unit,
    showPower: Boolean,
    onShowPowerChange: (Boolean) -> Unit,
    showLastHeard: Boolean,
    onShowLastHeardChange: (Boolean) -> Unit,
    lastHeardIsRelative: Boolean,
    onLastHeardIsRelativeChange: (Boolean) -> Unit,
    showLocation: Boolean,
    onShowLocationChange: (Boolean) -> Unit,
    showHops: Boolean,
    onShowHopsChange: (Boolean) -> Unit,
    showSignal: Boolean,
    onShowSignalChange: (Boolean) -> Unit,
    showChannel: Boolean,
    onShowChannelChange: (Boolean) -> Unit,
    showRole: Boolean,
    onShowRoleChange: (Boolean) -> Unit,
    showTelemetry: Boolean,
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
                    selected = density == option,
                    label = { Text(label) },
                )
            }
        }

        if (density == NodeListDensity.COMPLETE) {
            Text(
                text = stringResource(Res.string.node_layout_complete_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            // Compact toggles ordered by layout position
            SwitchPreference(
                title = stringResource(Res.string.node_layout_power),
                checked = showPower,
                enabled = true,
                onCheckedChange = onShowPowerChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_last_heard_time),
                checked = showLastHeard,
                enabled = true,
                onCheckedChange = onShowLastHeardChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_relative_last_heard),
                checked = lastHeardIsRelative,
                enabled = showLastHeard,
                onCheckedChange = onLastHeardIsRelativeChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_distance_and_bearing),
                checked = showLocation,
                enabled = true,
                onCheckedChange = onShowLocationChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_hops_away),
                checked = showHops,
                enabled = true,
                onCheckedChange = onShowHopsChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_signal_direct_only),
                checked = showSignal,
                enabled = true,
                onCheckedChange = onShowSignalChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_channel),
                checked = showChannel,
                enabled = true,
                onCheckedChange = onShowChannelChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_device_role),
                checked = showRole,
                enabled = true,
                onCheckedChange = onShowRoleChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.node_layout_log_icons),
                checked = showTelemetry,
                enabled = true,
                onCheckedChange = onShowTelemetryChange,
            )
        }
    }
}
