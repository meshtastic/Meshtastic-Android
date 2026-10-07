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

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.notificationId
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.firmware_update_available
import org.meshtastic.core.resources.firmware_update_notification_android
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.low_battery_message
import org.meshtastic.core.resources.low_battery_title
import org.meshtastic.core.resources.mesh_beacon_notification_body
import org.meshtastic.core.resources.mesh_beacon_notification_title
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/**
 * Desktop implementation of [MeshNotificationManager]: turns each mesh event into a [Notification] record and hands it
 * to [NotificationManager], which gates it on the user's notification preferences and shows it through the OS.
 *
 * Android-only concepts (notification channels, the foreground-service notification, updating a posted notification in
 * place) are no-ops here.
 *
 * Registered manually in `DesktopRuntimeModule` -- do **not** add `@Single` to avoid double-registration with the
 * `@ComponentScan("org.meshtastic.desktop")` in [DesktopDiModule][org.meshtastic.desktop.di.DesktopDiModule].
 */
@Suppress("TooManyFunctions")
class DesktopMeshNotificationManager(private val notificationManager: NotificationManager) : MeshNotificationManager {

    private val lowBatteryEpisodes = mutableMapOf<Int, Any>()

    override fun clearNotifications() {
        synchronized(lowBatteryEpisodes) { lowBatteryEpisodes.clear() }
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
        notificationManager.dispatch(Notification(title = name, message = message, isSilent = isSilent))
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
            Notification(title = name, message = emoji, category = Notification.Category.Message, isSilent = isSilent),
        )
    }

    override suspend fun showAlertNotification(contactKey: String, name: String, alert: String) {
        notificationManager.dispatch(
            Notification(title = name, message = alert, category = Notification.Category.Alert),
        )
    }

    override suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer) {
        notificationManager.dispatch(
            Notification(
                title = getStringSuspend(Res.string.mesh_beacon_notification_title),
                message = offer.message.ifBlank { getStringSuspend(Res.string.mesh_beacon_notification_body) },
                category = Notification.Category.MeshBeacon,
            ),
        )
    }

    override suspend fun showNewNodeSeenNotification(node: Node, title: String) {
        notificationManager.dispatch(
            Notification(
                title = title,
                message = node.user.long_name,
                category = Notification.Category.NodeEvent,
                id = node.num,
            ),
        )
    }

    override fun cancelNewNodeNotification(nodeNum: Int) {
        notificationManager.cancel(nodeNum)
    }

    // An OS notification cannot be refreshed in place, and re-posting would alert again, so only an episode's first
    // reading shows.
    override suspend fun notifyLowBattery(node: Node, isRemote: Boolean) {
        val episode = Any()
        if (synchronized(lowBatteryEpisodes) { lowBatteryEpisodes.putIfAbsent(node.num, episode) } != null) return
        var shown = false
        try {
            val notification =
                Notification(
                    title = getStringSuspend(Res.string.low_battery_title, node.user.short_name),
                    message =
                    getStringSuspend(Res.string.low_battery_message, node.user.long_name, node.batteryLevel ?: 0),
                    category = Notification.Category.Battery,
                    id = node.num,
                )
            // Recovery may have ended this episode while its text was resolving.
            if (synchronized(lowBatteryEpisodes) { lowBatteryEpisodes[node.num] !== episode }) return
            shown = notificationManager.dispatch(notification)
        } finally {
            // A warning that never showed ends its own episode, so the next low reading tries again.
            if (!shown) synchronized(lowBatteryEpisodes) { lowBatteryEpisodes.remove(node.num, episode) }
        }
    }

    override fun cancelLowBatteryNotification(nodeNum: Int) {
        synchronized(lowBatteryEpisodes) { lowBatteryEpisodes.remove(nodeNum) }
        notificationManager.cancel(nodeNum)
    }

    override suspend fun showClientNotification(
        clientNotification: ClientNotification,
        title: String,
        severity: Notification.Type,
    ) {
        notificationManager.dispatch(
            Notification(
                title = title,
                message = clientNotification.message,
                type = severity,
                category = Notification.Category.Client,
                id = clientNotification.notificationId(),
            ),
        )
    }

    override fun clearClientNotification(clientNotification: ClientNotification) {
        notificationManager.cancel(clientNotification.notificationId())
    }

    override suspend fun showFirmwareUpdateNotification(notice: FirmwareUpdateNotice): Boolean =
        notificationManager.dispatch(
            Notification(
                title = getStringSuspend(Res.string.firmware_update_available),
                message =
                getStringSuspend(
                    Res.string.firmware_update_notification_android,
                    notice.currentVersion,
                    notice.stableVersion,
                ),
                category = Notification.Category.Service,
                id = notice.notificationKey.hashCode(),
            ),
        )

    // The reconnect-blocked notice is about an Android runtime permission; desktop never raises it.
    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean = false

    override suspend fun cancelMessageNotification(contactKey: String) {
        notificationManager.cancel(contactKey.hashCode())
    }
}
