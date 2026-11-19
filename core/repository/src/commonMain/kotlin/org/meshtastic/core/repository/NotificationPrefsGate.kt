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

/** Whether the user's preferences let a notification of [category] show, on every platform that dispatches records. */
suspend fun NotificationPrefs.allows(category: Notification.Category): Boolean = when (category) {
    Notification.Category.Message -> messagesEnabled.value
    Notification.Category.Reaction -> loadReactionsEnabled()
    Notification.Category.Waypoint -> loadNewWaypointsEnabled()
    Notification.Category.NodeEvent -> nodeEventsEnabled.value
    Notification.Category.MeshBeacon -> nodeEventsEnabled.value
    Notification.Category.Battery -> lowBatteryEnabled.value
    Notification.Category.Alert -> true
    Notification.Category.Client -> true
    Notification.Category.Service -> true
}
