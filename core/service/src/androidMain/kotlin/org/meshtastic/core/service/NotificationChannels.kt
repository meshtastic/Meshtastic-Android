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
package org.meshtastic.core.service

internal object NotificationChannels {
    const val SERVICE = "my_service"
    const val MESSAGES = "my_messages"
    const val BROADCASTS = "my_broadcasts"
    const val WAYPOINTS = "my_waypoints"
    const val ALERTS = "my_alerts"
    const val NEW_NODES = "new_nodes"
    const val LOW_BATTERY = "low_battery"
    const val LOW_BATTERY_REMOTE = "low_battery_remote"
    const val CLIENT = "client_notifications"

    // Legacy enum-name channel IDs introduced by alpha channel routing.
    val LEGACY_CATEGORY_IDS = listOf("Message", "NodeEvent", "Battery", "Alert", "Service")
}
