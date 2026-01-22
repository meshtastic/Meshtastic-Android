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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.actions
import org.meshtastic.core.strings.direct_message
import org.meshtastic.core.strings.favorite
import org.meshtastic.core.strings.ignore
import org.meshtastic.core.strings.mute_notifications
import org.meshtastic.core.strings.remove
import org.meshtastic.core.strings.share_contact
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable

private enum class DialogType {
    FAVORITE,
    IGNORE,
    MUTE,
    REMOVE,
}

@Composable
fun DeviceActions(
    node: Node,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    metricsState: MetricsState,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
) {
    var displayedDialog by remember { mutableStateOf<DialogType?>(null) }

    NodeActionDialogs(
        node = node,
        displayFavoriteDialog = displayedDialog == DialogType.FAVORITE,
        displayIgnoreDialog = displayedDialog == DialogType.IGNORE,
        displayMuteDialog = displayedDialog == DialogType.MUTE,
        displayRemoveDialog = displayedDialog == DialogType.REMOVE,
        onDismissMenuRequest = { displayedDialog = null },
        onConfirmFavorite = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Favorite(it))) },
        onConfirmIgnore = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Ignore(it))) },
        onConfirmMute = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Mute(it))) },
        onConfirmRemove = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(it))) },
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = Res.string.actions) {
            PrimaryActionsRow(
                node = node,
                isLocal = isLocal,
                onAction = onAction,
                onFavoriteClick = { displayedDialog = DialogType.FAVORITE },
            )
            SectionDivider(Modifier.padding(vertical = 8.dp))
            ManagementActions(
                node = node,
                onIgnoreClick = { displayedDialog = DialogType.IGNORE },
                onMuteClick = { displayedDialog = DialogType.MUTE },
                onRemoveClick = { displayedDialog = DialogType.REMOVE },
            )
        }

        TelemetricActionsSection(
            node = node,
            availableLogs = availableLogs,
            lastTracerouteTime = lastTracerouteTime,
            lastRequestNeighborsTime = lastRequestNeighborsTime,
            metricsState = metricsState,
            onAction = onAction,
        )
    }
}

@Composable
private fun PrimaryActionsRow(
    node: Node,
    isLocal: Boolean,
    onAction: (NodeDetailAction) -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!node.isEffectivelyUnmessageable && !isLocal) {
            Button(
                onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node))) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.direct_message))
            }
        }

        OutlinedButton(
            onClick = { onAction(NodeDetailAction.ShareContact) },
            modifier = if (node.isEffectivelyUnmessageable || isLocal) Modifier.weight(1f) else Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.QrCode2, contentDescription = null)
            if (node.isEffectivelyUnmessageable || isLocal) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_contact))
            }
        }

        IconToggleButton(checked = node.isFavorite, onCheckedChange = { onFavoriteClick() }) {
            Icon(
                imageVector = if (node.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = stringResource(Res.string.favorite),
                tint = if (node.isFavorite) Color.Yellow else LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun ManagementActions(
    node: Node,
    onIgnoreClick: () -> Unit,
    onMuteClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Column {
        SwitchListItem(
            text = stringResource(Res.string.ignore),
            leadingIcon =
            if (node.isIgnored) {
                Icons.AutoMirrored.Outlined.VolumeMute
            } else {
                Icons.AutoMirrored.Default.VolumeUp
            },
            checked = node.isIgnored,
            onClick = onIgnoreClick,
        )

        if (node.capabilities.canMuteNode) {
            SwitchListItem(
                text = stringResource(Res.string.mute_notifications),
                leadingIcon =
                if (node.isMuted) {
                    Icons.AutoMirrored.Filled.VolumeOff
                } else {
                    Icons.AutoMirrored.Default.VolumeUp
                },
                checked = node.isMuted,
                onClick = onMuteClick,
            )
        }

        ListItem(
            text = stringResource(Res.string.remove),
            leadingIcon = Icons.Rounded.Delete,
            trailingIcon = null,
            textColor = MaterialTheme.colorScheme.error,
            leadingIconTint = MaterialTheme.colorScheme.error,
            onClick = onRemoveClick,
        )
    }
}
