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

import org.meshtastic.proto.ClientNotification

/**
 * Platform-agnostic notification dispatch primitive. Posts opaque [Notification] records, cancels by id, or wipes all
 * active notifications. Intended as the lowest layer of the notification stack.
 *
 * Domain-specific notification builders (mesh message arrivals, low-battery alerts, etc.) live in
 * [MeshNotificationManager], which composes over this dispatcher.
 */
interface NotificationManager {
    /**
     * Returns true only when the platform accepted the notification for delivery. Suspends until the platform resolves
     * the send, so the result reflects the actual outcome rather than an optimistic guess (desktop native senders shell
     * out to OS tools and complete asynchronously).
     */
    suspend fun dispatch(notification: Notification): Boolean

    /** Platform hook for ClientNotifications that should use a native presentation instead of the global modal. */
    fun suppressClientNotificationModal(notification: ClientNotification): Boolean = false

    /** Platform-specific ClientNotification delivery; defaults to the ordinary notification path. */
    suspend fun dispatchClientNotification(
        notification: Notification,
        clientNotification: ClientNotification,
    ): Boolean = dispatch(notification)

    fun cancel(id: Int)

    fun cancelAll()
}
