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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.administration
import org.meshtastic.core.strings.firmware
import org.meshtastic.core.strings.firmware_edition
import org.meshtastic.core.strings.installed_firmware_version
import org.meshtastic.core.strings.latest_alpha_firmware
import org.meshtastic.core.strings.latest_stable_firmware
import org.meshtastic.core.strings.remote_admin
import org.meshtastic.core.strings.request_metadata
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.FirmwareEdition

@Composable
fun AdministrationSection(
    node: Node,
    metricsState: MetricsState,
    onAction: (NodeDetailAction) -> Unit,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = Res.string.administration, modifier = modifier) {
        Column {
            ListItem(
                text = stringResource(Res.string.request_metadata),
                leadingIcon = Icons.Rounded.Memory,
                trailingIcon = null,
                onClick = {
                    onAction(NodeDetailAction.TriggerServiceAction(ServiceAction.GetDeviceMetadata(node.num)))
                },
            )

            SectionDivider()

            ListItem(
                text = stringResource(Res.string.remote_admin),
                leadingIcon = Icons.Rounded.Settings,
                enabled = metricsState.isLocal || node.metadata != null,
            ) {
                onAction(NodeDetailAction.Navigate(SettingsRoutes.Settings(node.num)))
            }
        }
    }

    val firmwareVersion = node.metadata?.firmware_version
    val firmwareEdition = metricsState.firmwareEdition
    if (firmwareVersion != null || (firmwareEdition != null && metricsState.isLocal)) {
        FirmwareSection(metricsState, firmwareEdition, firmwareVersion, onFirmwareSelect)
    }
}

@Composable
private fun FirmwareSection(
    metricsState: MetricsState,
    firmwareEdition: FirmwareEdition?,
    firmwareVersion: String?,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
) {
    SectionCard(title = Res.string.firmware) {
        Column {
            firmwareEdition?.let { edition ->
                val icon =
                    when (edition) {
                        FirmwareEdition.VANILLA -> Icons.Rounded.Icecream
                        else -> Icons.Rounded.ForkLeft
                    }

                ListItem(
                    text = stringResource(Res.string.firmware_edition),
                    leadingIcon = icon,
                    supportingText = edition.name,
                    copyable = true,
                    trailingIcon = null,
                )
            }

            firmwareVersion?.let { version ->
                FirmwareVersionItems(metricsState, version, firmwareEdition != null, onFirmwareSelect)
            }
        }
    }
}

@Composable
private fun FirmwareVersionItems(
    metricsState: MetricsState,
    version: String,
    hasEdition: Boolean,
    onFirmwareSelect: (FirmwareRelease) -> Unit,
) {
    val latestStable = metricsState.latestStableFirmware
    val latestAlpha = metricsState.latestAlphaFirmware

    val deviceVersion = DeviceVersion(version.substringBeforeLast("."))
    val statusColor = deviceVersion.determineFirmwareStatusColor(latestStable, latestAlpha)

    if (hasEdition) SectionDivider()

    ListItem(
        text = stringResource(Res.string.installed_firmware_version),
        leadingIcon = Icons.Rounded.Memory,
        supportingText = version.substringBeforeLast("."),
        copyable = true,
        leadingIconTint = statusColor,
        trailingIcon = null,
    )

    SectionDivider()

    ListItem(
        text = stringResource(Res.string.latest_stable_firmware),
        leadingIcon = Icons.Rounded.Memory,
        supportingText = latestStable.id.substringBeforeLast(".").replace("v", ""),
        copyable = true,
        leadingIconTint = MaterialTheme.colorScheme.StatusGreen,
        trailingIcon = null,
        onClick = { onFirmwareSelect(latestStable) },
    )

    SectionDivider()

    ListItem(
        text = stringResource(Res.string.latest_alpha_firmware),
        leadingIcon = Icons.Rounded.Memory,
        supportingText = latestAlpha.id.substringBeforeLast(".").replace("v", ""),
        copyable = true,
        leadingIconTint = MaterialTheme.colorScheme.StatusYellow,
        trailingIcon = null,
        onClick = { onFirmwareSelect(latestAlpha) },
    )
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
