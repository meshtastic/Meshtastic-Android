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
package org.meshtastic.feature.settings.radio

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_delete_fill0
import org.meshtastic.core.resources.ic_person_add
import org.meshtastic.core.resources.ic_star
import org.meshtastic.core.resources.ic_star_border
import org.meshtastic.core.resources.remote_admin_add_and_favorite_node
import org.meshtastic.core.resources.remote_admin_add_and_favorite_node_summary
import org.meshtastic.core.resources.remote_admin_add_node
import org.meshtastic.core.resources.remote_admin_add_node_summary
import org.meshtastic.core.resources.remote_admin_confirm_add_and_favorite
import org.meshtastic.core.resources.remote_admin_confirm_add_node
import org.meshtastic.core.resources.remote_admin_confirm_remove_favorite
import org.meshtastic.core.resources.remote_admin_confirm_remove_node
import org.meshtastic.core.resources.remote_admin_remove_favorite
import org.meshtastic.core.resources.remote_admin_remove_favorite_summary
import org.meshtastic.core.resources.remote_admin_remove_node
import org.meshtastic.core.resources.remote_admin_remove_node_summary
import org.meshtastic.proto.Config

/**
 * Remote NodeDB management commands offered when administering another node.
 *
 * Firmware's `add_contact` auto-favorites on every role except `CLIENT_BASE`, whereas `set_favorite_node` works on
 * `CLIENT_BASE` only once the peer exists in the target's NodeDB.
 */
enum class RemoteNodeDbAction(
    val icon: DrawableResource,
    val title: StringResource,
    val summary: StringResource,
    val confirmMessage: StringResource,
    val requiresPublicKey: Boolean,
    val isDestructive: Boolean,
) {
    ADD_AND_FAVORITE(
        icon = Res.drawable.ic_star,
        title = Res.string.remote_admin_add_and_favorite_node,
        summary = Res.string.remote_admin_add_and_favorite_node_summary,
        confirmMessage = Res.string.remote_admin_confirm_add_and_favorite,
        requiresPublicKey = true,
        isDestructive = false,
    ),
    ADD_NODE(
        icon = Res.drawable.ic_person_add,
        title = Res.string.remote_admin_add_node,
        summary = Res.string.remote_admin_add_node_summary,
        confirmMessage = Res.string.remote_admin_confirm_add_node,
        requiresPublicKey = true,
        isDestructive = false,
    ),
    REMOVE_FAVORITE(
        icon = Res.drawable.ic_star_border,
        title = Res.string.remote_admin_remove_favorite,
        summary = Res.string.remote_admin_remove_favorite_summary,
        confirmMessage = Res.string.remote_admin_confirm_remove_favorite,
        requiresPublicKey = false,
        isDestructive = false,
    ),
    REMOVE_NODE(
        icon = Res.drawable.ic_delete_fill0,
        title = Res.string.remote_admin_remove_node,
        summary = Res.string.remote_admin_remove_node_summary,
        confirmMessage = Res.string.remote_admin_confirm_remove_node,
        requiresPublicKey = false,
        isDestructive = true,
    ),
}

/**
 * Returns the remote NodeDB actions appropriate for a target node with [role].
 *
 * `CLIENT_BASE` separates adding a contact from favoriting it (favorites trigger rebroadcasting), so both "Add &
 * favorite node" and "Add node" are offered. All other roles auto-favorite on `add_contact`, so only "Add & favorite
 * node" is shown alongside removals.
 */
fun remoteNodeDbActionsForRole(role: Config.DeviceConfig.Role?): List<RemoteNodeDbAction> =
    if (role == Config.DeviceConfig.Role.CLIENT_BASE) {
        listOf(
            RemoteNodeDbAction.ADD_AND_FAVORITE,
            RemoteNodeDbAction.ADD_NODE,
            RemoteNodeDbAction.REMOVE_FAVORITE,
            RemoteNodeDbAction.REMOVE_NODE,
        )
    } else {
        listOf(
            RemoteNodeDbAction.ADD_AND_FAVORITE,
            RemoteNodeDbAction.REMOVE_FAVORITE,
            RemoteNodeDbAction.REMOVE_NODE,
        )
    }
