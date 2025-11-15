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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.actions
import org.meshtastic.core.strings.favorite
import org.meshtastic.core.strings.ignore
import org.meshtastic.core.strings.remove
import org.meshtastic.core.strings.share_contact
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.node.model.NodeDetailAction

@Composable
fun DeviceActions(
    node: Node,
    lastTracerouteTime: Long?,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
) {
    var displayFavoriteDialog by remember { mutableStateOf(false) }
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }

    NodeActionDialogs(
        node = node,
        displayFavoriteDialog = displayFavoriteDialog,
        displayIgnoreDialog = displayIgnoreDialog,
        displayRemoveDialog = displayRemoveDialog,
        onDismissMenuRequest = {
            displayFavoriteDialog = false
            displayIgnoreDialog = false
            displayRemoveDialog = false
        },
        onConfirmFavorite = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Favorite(it))) },
        onConfirmIgnore = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Ignore(it))) },
        onConfirmRemove = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(it))) },
    )
    TitledCard(title = stringResource(Res.string.actions), modifier = modifier) {
        ListItem(
            text = stringResource(Res.string.share_contact),
            leadingIcon = Icons.Rounded.QrCode2,
            trailingIcon = null,
            onClick = { onAction(NodeDetailAction.ShareContact) },
        )
        if (!isLocal) {
            InsetDivider()
            RemoteDeviceActions(node = node, lastTracerouteTime = lastTracerouteTime, onAction = onAction)
        }

        InsetDivider()

        SwitchListItem(
            text = stringResource(Res.string.favorite),
            leadingIcon = if (node.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            leadingIconTint = if (node.isFavorite) Color.Yellow else LocalContentColor.current,
            checked = node.isFavorite,
            onClick = { displayFavoriteDialog = true },
        )

        InsetDivider()

        SwitchListItem(
            text = stringResource(Res.string.ignore),
            leadingIcon =
            if (node.isIgnored) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Default.VolumeUp,
            checked = node.isIgnored,
            onClick = { displayIgnoreDialog = true },
        )

        InsetDivider()

        ListItem(
            text = stringResource(Res.string.remove),
            leadingIcon = Icons.Rounded.Delete,
            trailingIcon = null,
            onClick = { displayRemoveDialog = true },
        )
    }
}
