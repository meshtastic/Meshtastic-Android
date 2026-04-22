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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.administration
import org.meshtastic.core.resources.connect_radio_for_remote_admin
import org.meshtastic.core.resources.establishing_session
import org.meshtastic.core.resources.firmware
import org.meshtastic.core.resources.firmware_edition
import org.meshtastic.core.resources.installed_firmware_version
import org.meshtastic.core.resources.latest_alpha_firmware
import org.meshtastic.core.resources.latest_stable_firmware
import org.meshtastic.core.resources.refresh_metadata
import org.meshtastic.core.resources.remote_admin
import org.meshtastic.core.resources.session_active
import org.meshtastic.core.resources.session_refresh_required
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.ForkLeft
import org.meshtastic.core.ui.icon.Icecream
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
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
    sessionStatus: SessionStatus,
    isEnsuringSession: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionCard(title = Res.string.administration) {
            Column {
                // Local nodes don't need a session — they short-circuit straight to the settings screen.
                if (metricsState.isLocal) {
                    ListItem(
                        text = stringResource(Res.string.remote_admin),
                        leadingIcon = MeshtasticIcons.Settings,
                        onClick = { onAction(NodeDetailAction.OpenRemoteAdmin(node.num)) },
                    )
                } else {
                    RemoteAdminListItem(
                        nodeNum = node.num,
                        sessionStatus = sessionStatus,
                        isEnsuringSession = isEnsuringSession,
                        onAction = onAction,
                    )

                    SectionDivider()

                    ListItem(
                        text = stringResource(Res.string.refresh_metadata),
                        leadingIcon = MeshtasticIcons.Memory,
                        trailingIcon = null,
                        enabled = !isEnsuringSession,
                        onClick = { onAction(NodeDetailAction.RefreshMetadata(node.num)) },
                    )
                }
            }
        }

        val firmwareVersion = node.metadata?.firmware_version
        val firmwareEdition = metricsState.firmwareEdition
        if (firmwareVersion != null || (firmwareEdition != null && metricsState.isLocal)) {
            FirmwareSection(metricsState, firmwareEdition, firmwareVersion, onFirmwareSelect)
        }
    }
}

/**
 * Single primary affordance for opening the remote-admin screen. Replaces the prior two-row, no-feedback flow that
 * required the user to know they had to tap "Metadata" first to populate `node.metadata` before "Remote Administration"
 * un-greyed out. The session passkey freshness — not the metadata insert — is the real gate (see
 * `firmware/src/modules/AdminModule.cpp:1460-1481`), and is now reflected via an [AssistChip] + inline progress.
 */
@Composable
private fun RemoteAdminListItem(
    nodeNum: Int,
    sessionStatus: SessionStatus,
    isEnsuringSession: Boolean,
    onAction: (NodeDetailAction) -> Unit,
) {
    val supportingTextRes =
        when (sessionStatus) {
            SessionStatus.NoSession -> Res.string.connect_radio_for_remote_admin
            is SessionStatus.Active -> null
            is SessionStatus.Stale -> Res.string.session_refresh_required
        }
    val chipLabelRes =
        when (sessionStatus) {
            SessionStatus.NoSession -> null
            is SessionStatus.Active -> Res.string.session_active
            is SessionStatus.Stale -> Res.string.session_refresh_required
        }

    Column {
        BasicListItem(
            text = stringResource(Res.string.remote_admin),
            leadingIcon = MeshtasticIcons.Settings,
            supportingText = supportingTextRes?.let { stringResource(it) },
            enabled = !isEnsuringSession,
            trailingContent =
            chipLabelRes?.let { res ->
                {
                    AssistChip(
                        onClick = { onAction(NodeDetailAction.OpenRemoteAdmin(nodeNum)) },
                        label = { androidx.compose.material3.Text(stringResource(res)) },
                        enabled = !isEnsuringSession,
                        colors =
                        if (sessionStatus is SessionStatus.Active) {
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            },
            onClick = { onAction(NodeDetailAction.OpenRemoteAdmin(nodeNum)) },
        )
        AnimatedVisibility(visible = isEnsuringSession) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.Text(
                    text = stringResource(Res.string.establishing_session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
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
                        FirmwareEdition.VANILLA -> MeshtasticIcons.Icecream
                        else -> MeshtasticIcons.ForkLeft
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
        leadingIcon = MeshtasticIcons.Memory,
        supportingText = version.substringBeforeLast("."),
        copyable = true,
        leadingIconTint = statusColor,
        trailingIcon = null,
    )

    SectionDivider()

    ListItem(
        text = stringResource(Res.string.latest_stable_firmware),
        leadingIcon = MeshtasticIcons.Memory,
        supportingText = latestStable.id.substringBeforeLast(".").replace("v", ""),
        copyable = true,
        leadingIconTint = MaterialTheme.colorScheme.StatusGreen,
        trailingIcon = null,
        onClick = { onFirmwareSelect(latestStable) },
    )

    SectionDivider()

    ListItem(
        text = stringResource(Res.string.latest_alpha_firmware),
        leadingIcon = MeshtasticIcons.Memory,
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
