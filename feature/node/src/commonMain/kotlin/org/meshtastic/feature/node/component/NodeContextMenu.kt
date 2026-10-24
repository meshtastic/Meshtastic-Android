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
package org.meshtastic.feature.node.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_favorite
import org.meshtastic.core.resources.edit_name
import org.meshtastic.core.resources.ignore
import org.meshtastic.core.resources.mesh_hud_open
import org.meshtastic.core.resources.message
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.open_compass
import org.meshtastic.core.resources.remove
import org.meshtastic.core.resources.remove_favorite
import org.meshtastic.core.resources.remove_ignored
import org.meshtastic.core.resources.set_display_name
import org.meshtastic.core.resources.share_contact
import org.meshtastic.core.resources.trace_route
import org.meshtastic.core.resources.unmute
import org.meshtastic.core.resources.update_status
import org.meshtastic.core.ui.icon.CenterFocusStrong
import org.meshtastic.core.ui.icon.Compass
import org.meshtastic.core.ui.icon.DeleteNode
import org.meshtastic.core.ui.icon.DoDisturb
import org.meshtastic.core.ui.icon.Edit
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.NotFavorite
import org.meshtastic.core.ui.icon.Notes
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.core.ui.icon.VolumeOff
import org.meshtastic.core.ui.icon.VolumeUp
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.util.rememberMeshHudSupported

/** Mute is `toggle_muted_node` sent to our own node, so our firmware, not the muted node's, must support it. */
internal fun canMuteFrom(ourNode: Node?): Boolean = ourNode?.capabilities?.canMuteNode == true

/**
 * Shared context menu for node actions (favorite, mute, message, trace route, compass, mesh view, ignore, remove).
 *
 * Used by both Android and Desktop adaptive node list screens.
 */
@Composable
fun NodeContextMenu(
    expanded: Boolean,
    node: Node,
    ourNode: Node?,
    onFavorite: () -> Unit,
    onMute: () -> Unit,
    onMessage: () -> Unit,
    onTraceRoute: () -> Unit,
    lastTracerouteTime: Long?,
    onIgnore: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    onOpenMeshView: () -> Unit = {},
    onOpenCompass: () -> Unit = {},
    onSetDisplayName: () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            FavoriteMenuItem(node, onFavorite, onDismiss)
            if (canMuteFrom(ourNode)) {
                MuteMenuItem(node, onMute, onDismiss)
            }
            MessageMenuItem(node, onMessage, onDismiss)
            MenuItem(
                MeshtasticIcons.Edit,
                stringResource(Res.string.set_display_name),
                onSetDisplayName,
                onDismiss,
            )
            TraceRouteMenuItem(node, lastTracerouteTime, onTraceRoute, onDismiss)
            if (node.validPosition != null) {
                MenuItem(MeshtasticIcons.Compass, stringResource(Res.string.open_compass), onOpenCompass, onDismiss)
                if (rememberMeshHudSupported()) {
                    MenuItem(
                        MeshtasticIcons.CenterFocusStrong,
                        stringResource(Res.string.mesh_hud_open),
                        onOpenMeshView,
                        onDismiss,
                    )
                }
            }
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            IgnoreMenuItem(node, onIgnore, onDismiss)
            RemoveMenuItem(node, onRemove, onDismiss)
        }
    }
}

/**
 * A menu of its own: favoriting, muting, messaging or removing yourself is meaningless (design#115). Each action is
 * absent, never disabled, when its callback is null: update status, edit the broadcast name in device settings, and
 * share our contact.
 */
@Composable
fun LocalNodeContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onUpdateStatus: (() -> Unit)? = null,
    onEditName: (() -> Unit)? = null,
    onShareContact: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            onUpdateStatus?.let { LocalMenuItem(MeshtasticIcons.Notes, Res.string.update_status, it, onDismiss) }
            onEditName?.let { LocalMenuItem(MeshtasticIcons.Edit, Res.string.edit_name, it, onDismiss) }
            onShareContact?.let { LocalMenuItem(MeshtasticIcons.Share, Res.string.share_contact, it, onDismiss) }
        }
    }
}

@Composable
private fun LocalMenuItem(icon: ImageVector, label: StringResource, onClick: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onClick()
            onDismiss()
        },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        text = { Text(text = stringResource(label)) },
    )
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
private fun TraceRouteMenuItem(
    node: Node,
    lastTracerouteTime: Long?,
    onTraceRoute: () -> Unit,
    onDismiss: () -> Unit,
) {
    var coolingDown by remember { mutableStateOf(false) }
    LaunchedEffect(lastTracerouteTime) {
        val endTime = (lastTracerouteTime ?: 0L) + COOL_DOWN_TIME_MS
        coolingDown = nowMillis < endTime
        while (coolingDown) {
            delay(COOLDOWN_TICK_MS)
            coolingDown = nowMillis < endTime
        }
    }
    DropdownMenuItem(
        onClick = {
            onTraceRoute()
            onDismiss()
        },
        enabled = !node.isIgnored && !coolingDown,
        leadingIcon = { Icon(imageVector = MeshtasticIcons.Route, contentDescription = null) },
        text = { Text(text = stringResource(Res.string.trace_route)) },
    )
}

private const val COOLDOWN_TICK_MS = 500L

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onClick()
            onDismiss()
        },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        text = { Text(text = text) },
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
