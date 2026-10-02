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

/** A notification as the desktop dispatch primitive ([NotificationManager]) sees it. */
data class Notification(
    val title: String,
    val message: String,
    val type: Type = Type.Info,
    val category: Category = Category.Message,
    val isSilent: Boolean = false,
    val id: Int? = null,
) {
    enum class Type {
        None,
        Info,
        Warning,
        Error,
    }

    enum class Category {
        Message,
        NodeEvent,
        Battery,
        Alert,
        Service,

        /** Advisory Mesh Beacon invitations from other meshes. */
        MeshBeacon,

        /** Notices from the radio's firmware (ClientNotification). */
        Client,
    }
}
