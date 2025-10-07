/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.node.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.MeshProtos
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SettingsItem
import org.meshtastic.core.ui.component.SettingsItemDetail
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction

@Suppress("LongMethod")
@Composable
fun AdministrationSection(
    node: Node,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelected: (FirmwareRelease) -> Unit,
) {
    TitledCard(stringResource(id = R.string.administration)) {
        SettingsItem(
            text = stringResource(id = R.string.request_metadata),
            leadingIcon = Icons.Default.Memory,
            trailingContent = {},
            onClick = {
                onAction(
                    NodeDetailAction.TriggerServiceAction(
                        ServiceAction.GetDeviceMetadata(
                            node.num
                        )
                    )
                )
            },
        )
        SettingsItem(
            text = stringResource(id = R.string.remote_admin),
            leadingIcon = Icons.Default.Settings,
            enabled = metricsState.isLocal || node.metadata != null,
        ) {
            onAction(NodeDetailAction.Navigate(SettingsRoutes.Settings(node.num)))
        }
    }
    val firmwareVersion = node.metadata?.firmwareVersion
    val firmwareEdition = metricsState.firmwareEdition
    if (firmwareVersion != null || (firmwareEdition != null && metricsState.isLocal)) {
        TitledCard(stringResource(R.string.firmware)) {
            firmwareEdition?.let {
                val icon =
                    when (it) {
                        MeshProtos.FirmwareEdition.VANILLA -> Icons.Default.Icecream
                        else -> Icons.Default.ForkLeft
                    }

                SettingsItemDetail(
                    text = stringResource(R.string.firmware_edition),
                    icon = icon,
                    supportingText = it.name,
                )
            }
            firmwareVersion?.let { firmwareVersion ->
                val latestStable = metricsState.latestStableFirmware
                val latestAlpha = metricsState.latestAlphaFirmware

                val deviceVersion = DeviceVersion(firmwareVersion.substringBeforeLast("."))
                val statusColor =
                    deviceVersion.determineFirmwareStatusColor(latestStable, latestAlpha)

                SettingsItemDetail(
                    text = stringResource(R.string.installed_firmware_version),
                    icon = Icons.Default.Memory,
                    supportingText = firmwareVersion.substringBeforeLast("."),
                    iconTint = statusColor,
                )
                HorizontalDivider()
                SettingsItemDetail(
                    text = stringResource(R.string.latest_stable_firmware),
                    icon = Icons.Default.Memory,
                    supportingText = latestStable.id.substringBeforeLast(".").replace("v", ""),
                    iconTint = MaterialTheme.colorScheme.StatusGreen,
                    onClick = { onFirmwareSelected(latestStable) },
                )
                SettingsItemDetail(
                    text = stringResource(R.string.latest_alpha_firmware),
                    icon = Icons.Default.Memory,
                    supportingText = latestAlpha.id.substringBeforeLast(".").replace("v", ""),
                    iconTint = MaterialTheme.colorScheme.StatusYellow,
                    onClick = { onFirmwareSelected(latestAlpha) },
                )
            }
        }
    }
}

@Composable
private fun DeviceVersion.determineFirmwareStatusColor(
    latestStable: FirmwareRelease,
    latestAlpha: FirmwareRelease,
): Color {
    val stableVersion = latestStable.asDeviceVersion()
    val alphaVersion = latestAlpha.asDeviceVersion()
    return when {
        this < stableVersion -> MaterialTheme.colorScheme.StatusRed
        this == stableVersion -> MaterialTheme.colorScheme.StatusGreen
        this in stableVersion..alphaVersion -> MaterialTheme.colorScheme.StatusYellow
        this > alphaVersion -> MaterialTheme.colorScheme.StatusOrange
        else -> MaterialTheme.colorScheme.onSurface
    }
}
