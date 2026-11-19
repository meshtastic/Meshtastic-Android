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
package org.meshtastic.core.repository

/**
 * A notification as the dispatch primitive ([NotificationManager]) sees it.
 *
 * @property deepLink the path under `DEEP_LINK_BASE_URI` that a click opens, from `NotificationDeepLinks`; null opens
 *   the app where it was.
 * @property onlyAlertOnce replacing a showing notification of the same [tag] updates it without alerting again.
 */
data class Notification(
    val title: String,
    val message: String,
    val type: Type = Type.Info,
    val category: Category = Category.Message,
    val isSilent: Boolean = false,
    val id: Int? = null,
    val deepLink: String? = null,
    val onlyAlertOnce: Boolean = false,
) {
    /**
     * One slot per category and [id], the way Android keys each notification by a tag and id: a newer notification
     * replaces the one in its slot, and a new-node notice never replaces a low-battery one for the same node.
     */
    val tag: String?
        get() = id?.let { tagFor(category, it) }

    enum class Type {
        None,
        Info,
        Warning,
        Error,
    }

    enum class Category {
        Message,

        /** Emoji reactions to messages. */
        Reaction,

        /** Waypoints received from the mesh. */
        Waypoint,
        NodeEvent,
        Battery,
        Alert,
        Service,

        /** Advisory Mesh Beacon invitations from other meshes. */
        MeshBeacon,

        /** Notices from the radio's firmware (ClientNotification). */
        Client,
    }

    companion object {
        fun tagFor(category: Category, id: Int): String = "${category.name}-$id"
    }
}
