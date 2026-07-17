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
 * Platform-agnostic notification dispatch primitive. Posts opaque [Notification] records, cancels by id, or wipes all
 * active notifications. Intended as the lowest layer of the notification stack.
 *
 * Domain-specific notification builders (mesh message arrivals, low-battery alerts, etc.) live in
 * [MeshNotificationManager], which composes over this dispatcher.
 */
interface NotificationManager {
    /** Returns true only when the platform accepted the notification for delivery. */
    fun dispatch(notification: Notification): Boolean

    fun cancel(id: Int)

    fun cancelAll()
}
