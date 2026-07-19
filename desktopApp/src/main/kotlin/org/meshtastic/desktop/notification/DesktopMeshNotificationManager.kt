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
package org.meshtastic.desktop.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.desktop_notification_title
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.low_battery_message
import org.meshtastic.core.resources.low_battery_title
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/**
 * Desktop implementation of [MeshNotificationManager].
 *
 * Converts mesh-layer notification events into domain [Notification] objects and dispatches them through
 * [NotificationManager], which ultimately surfaces them as Compose Desktop tray notifications.
 *
 * Android-only concepts (notification channels, foreground-service state updates) are intentionally no-ops.
 *
 * Registered manually in `desktopPlatformStubsModule` -- do **not** add `@Single` to avoid double-registration with the
 * `@ComponentScan("org.meshtastic.desktop")` in [DesktopDiModule][org.meshtastic.desktop.di.DesktopDiModule].
 */
@Suppress("TooManyFunctions")
class DesktopMeshNotificationManager(private val notificationManager: NotificationManager) : MeshNotificationManager {

    /**
     * Bridges the non-suspend [MeshNotificationManager] entry points to the suspending [NotificationManager.dispatch].
     */
    @Suppress("InjectDispatcher")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun clearNotifications() {
        notificationManager.cancelAll()
    }

    override fun initChannels() {
        // No-op: desktop has no Android notification channels.
    }

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        // No-op: desktop has no foreground service notification.
    }

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = message,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
                id = contactKey.hashCode(),
            ),
        )
    }

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = message,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
            ),
        )
    }

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = emoji,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
            ),
        )
    }

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {
        val notification =
            Notification(title = name, message = alert, category = Notification.Category.Alert, contactKey = contactKey)
        scope.launch { notificationManager.dispatch(notification) }
    }

    override fun showNewNodeSeenNotification(node: Node) {
        scope.launch {
            notificationManager.dispatch(
                Notification(
                    title = getString(Res.string.new_node_seen, node.user.short_name),
                    message = node.user.long_name,
                    category = Notification.Category.NodeEvent,
                ),
            )
        }
    }

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {
        scope.launch {
            notificationManager.dispatch(
                Notification(
                    title = getString(Res.string.low_battery_title, node.user.short_name),
                    message = getString(Res.string.low_battery_message, node.user.long_name, node.batteryLevel ?: 0),
                    category = Notification.Category.Battery,
                    id = node.num,
                ),
            )
        }
    }

    override fun showClientNotification(clientNotification: ClientNotification) {
        scope.launch {
            notificationManager.dispatch(
                Notification(
                    title = getString(Res.string.desktop_notification_title),
                    message = clientNotification.message,
                    category = Notification.Category.Alert,
                    id = clientNotification.toString().hashCode(),
                ),
            )
        }
    }

    override fun cancelMessageNotification(contactKey: String) {
        notificationManager.cancel(contactKey.hashCode())
    }

    override fun cancelLowBatteryNotification(node: Node) {
        notificationManager.cancel(node.num)
    }

    override fun clearClientNotification(notification: ClientNotification) {
        notificationManager.cancel(notification.toString().hashCode())
    }
}
