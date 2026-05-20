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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.node.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_favorite
import org.meshtastic.core.resources.ignore
import org.meshtastic.core.resources.message
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.remove
import org.meshtastic.core.resources.remove_favorite
import org.meshtastic.core.resources.remove_ignored
import org.meshtastic.core.resources.trace_route
import org.meshtastic.core.resources.unmute
import org.meshtastic.core.ui.icon.DeleteNode
import org.meshtastic.core.ui.icon.DoDisturb
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.NotFavorite
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.icon.VolumeOff
import org.meshtastic.core.ui.icon.VolumeUp
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

/**
 * Shared context menu for node actions (favorite, mute, message, trace route, ignore, remove).
 *
 * Used by both Android and Desktop adaptive node list screens.
 */
@Composable
fun NodeContextMenu(
    expanded: Boolean,
    node: Node,
    onFavorite: () -> Unit,
    onMute: () -> Unit,
    onMessage: () -> Unit,
    onTraceRoute: () -> Unit,
    onIgnore: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            FavoriteMenuItem(node, onFavorite, onDismiss)
            if (node.capabilities.canMuteNode) {
                MuteMenuItem(node, onMute, onDismiss)
            }
            MessageMenuItem(node, onMessage, onDismiss)
            TraceRouteMenuItem(node, onTraceRoute, onDismiss)
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            IgnoreMenuItem(node, onIgnore, onDismiss)
            RemoveMenuItem(node, onRemove, onDismiss)
        }
    }
}

@Composable
private fun FavoriteMenuItem(node: Node, onFavorite: () -> Unit, onDismiss: () -> Unit) {
    val isFavorite = node.isFavorite
    DropdownMenuItem(
        onClick = {
            onFavorite()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = if (isFavorite) MeshtasticIcons.Favorite else MeshtasticIcons.NotFavorite,
                contentDescription = null,
            )
        },
        text = { Text(stringResource(if (isFavorite) Res.string.remove_favorite else Res.string.add_favorite)) },
    )
}

@Composable
private fun IgnoreMenuItem(node: Node, onIgnore: () -> Unit, onDismiss: () -> Unit) {
    val isIgnored = node.isIgnored
    DropdownMenuItem(
        onClick = {
            onIgnore()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isIgnored) MeshtasticIcons.DoDisturb else MeshtasticIcons.DoDisturb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(if (isIgnored) Res.string.remove_ignored else Res.string.ignore),
                color = MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}

@Composable
private fun MuteMenuItem(node: Node, onMute: () -> Unit, onDismiss: () -> Unit) {
    val isMuted = node.isMuted
    DropdownMenuItem(
        onClick = {
            onMute()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isMuted) MeshtasticIcons.VolumeOff else MeshtasticIcons.VolumeUp,
                contentDescription = null,
            )
        },
        text = { Text(text = stringResource(if (isMuted) Res.string.unmute else Res.string.mute_notifications)) },
    )
}

@Composable
private fun MessageMenuItem(node: Node, onMessage: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onMessage()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = { Icon(imageVector = MeshtasticIcons.Message, contentDescription = null) },
        text = { Text(text = stringResource(Res.string.message)) },
    )
}

@Composable
private fun TraceRouteMenuItem(node: Node, onTraceRoute: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onTraceRoute()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = { Icon(imageVector = MeshtasticIcons.Route, contentDescription = null) },
        text = { Text(text = stringResource(Res.string.trace_route)) },
    )
}

@Composable
private fun RemoveMenuItem(node: Node, onRemove: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onRemove()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = MeshtasticIcons.DeleteNode,
                contentDescription = null,
                tint = if (node.isIgnored) LocalContentColor.current else MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.remove),
                color = if (node.isIgnored) Color.Unspecified else MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}
