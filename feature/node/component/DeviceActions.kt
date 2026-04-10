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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.NotFavorite
import org.meshtastic.core.ui.icon.QrCode2
import org.meshtastic.core.ui.icon.VolumeMute
import org.meshtastic.core.ui.icon.VolumeOff
import org.meshtastic.core.ui.icon.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.actions
import org.meshtastic.core.resources.direct_message
import org.meshtastic.core.resources.favorite
import org.meshtastic.core.resources.ignore
import org.meshtastic.core.resources.mute_always
import org.meshtastic.core.resources.remove
import org.meshtastic.core.resources.share_contact
import org.meshtastic.core.resources.unmute
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable

@Composable
fun DeviceActions(
    node: Node,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
) {
    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayMuteDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }

    NodeActionDialogs(
        node = node,
        displayFavoriteDialog = displayFavoriteDialog,
        displayIgnoreDialog = displayIgnoreDialog,
        displayMuteDialog = displayMuteDialog,
        displayRemoveDialog = displayRemoveDialog,
        onDismissMenuRequest = {
            displayFavoriteDialog = false
            displayIgnoreDialog = false
            displayMuteDialog = false
            displayRemoveDialog = false
        },
        onConfirmFavorite = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Favorite(it))) },
        onConfirmIgnore = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Ignore(it))) },
        onConfirmMute = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Mute(it))) },
        onConfirmRemove = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(it))) },
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        DeviceActionsContent(
            node = node,
            isLocal = isLocal,
            lastTracerouteTime = lastTracerouteTime,
            lastRequestNeighborsTime = lastRequestNeighborsTime,
            onAction = onAction,
            onFavoriteClick = { displayFavoriteDialog = true },
            onIgnoreClick = { displayIgnoreDialog = true },
            onMuteClick = { displayMuteDialog = true },
            onRemoveClick = { displayRemoveDialog = true },
        )
    }
}

@Composable
private fun DeviceActionsContent(
    node: Node,
    isLocal: Boolean,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
    onFavoriteClick: () -> Unit,
    onIgnoreClick: () -> Unit,
    onMuteClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = stringResource(Res.string.actions),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        PrimaryActionsRow(node, isLocal, onAction, onFavoriteClick)

        if (!isLocal) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            RemoteDeviceActions(
                node = node,
                lastTracerouteTime = lastTracerouteTime,
                lastRequestNeighborsTime = lastRequestNeighborsTime,
                onAction = onAction,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        ManagementActions(node, onIgnoreClick, onMuteClick, onRemoveClick)
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
                Icon(MeshtasticIcons.Message, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.direct_message))
            }
        }

        OutlinedButton(
            onClick = { onAction(NodeDetailAction.ShareContact) },
            modifier = if (node.isEffectivelyUnmessageable || isLocal) Modifier.weight(1f) else Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(MeshtasticIcons.QrCode2, contentDescription = null)
            if (node.isEffectivelyUnmessageable || isLocal) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_contact))
            }
        }

        IconToggleButton(checked = node.isFavorite, onCheckedChange = { onFavoriteClick() }) {
            Icon(
                imageVector = if (node.isFavorite) MeshtasticIcons.Favorite else MeshtasticIcons.NotFavorite,
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
                MeshtasticIcons.VolumeMute
            } else {
                MeshtasticIcons.VolumeUp
            },
            checked = node.isIgnored,
            onClick = onIgnoreClick,
        )

        SwitchListItem(
            text = stringResource(if (node.isMuted) Res.string.unmute else Res.string.mute_always),
            leadingIcon = if (node.isMuted) {
                MeshtasticIcons.VolumeOff
            } else {
                MeshtasticIcons.VolumeUp
            },
            checked = node.isMuted,
            onClick = onMuteClick,
        )

        ListItem(
            text = stringResource(Res.string.remove),
            leadingIcon = MeshtasticIcons.Delete,
            trailingIcon = null,
            textColor = MaterialTheme.colorScheme.error,
            leadingIconTint = MaterialTheme.colorScheme.error,
            onClick = onRemoveClick,
        )
    }
}
