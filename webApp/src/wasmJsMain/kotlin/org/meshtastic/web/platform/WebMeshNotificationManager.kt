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
package org.meshtastic.web.platform

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
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
 * Web implementation of [MeshNotificationManager], the desktop one minus JVM locks (a page has one thread): turns each
 * mesh event into a [Notification] record for [NotificationManager], which gates it on the user's preferences and the
 * browser's permission.
 *
 * Android-only concepts (notification channels, the foreground-service notification) are no-ops here.
 */
@Suppress("TooManyFunctions")
class WebMeshNotificationManager(private val notificationManager: NotificationManager) : MeshNotificationManager {

    private val lowBatteryEpisodes = mutableMapOf<Int, Any>()

    override fun clearNotifications() {
        lowBatteryEpisodes.clear()
        notificationManager.cancelAll()
    }

    override fun initChannels() {
        // No-op: a browser has no notification channels.
    }

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        // No-op: a browser tab has no foreground service notification.
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
        notificationManager.dispatch(
            Notification(
                title = name,
                message = message,
                category = Notification.Category.Waypoint,
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
            Notification(title = name, message = emoji, category = Notification.Category.Reaction, isSilent = isSilent),
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
                message = node.effectiveLongName,
                category = Notification.Category.NodeEvent,
                id = node.num,
            ),
        )
    }

    override fun cancelNewNodeNotification(nodeNum: Int) {
        notificationManager.cancel(nodeNum)
    }

    // Re-posting under the same tag would alert again, so only an episode's first reading shows.
    override suspend fun notifyLowBattery(node: Node, isRemote: Boolean) {
        val episode = Any()
        if (node.num in lowBatteryEpisodes) return
        lowBatteryEpisodes[node.num] = episode
        var shown = false
        try {
            val notification =
                Notification(
                    title = getStringSuspend(Res.string.low_battery_title, node.user.short_name),
                    message =
                    getStringSuspend(
                        Res.string.low_battery_message,
                        node.effectiveLongName,
                        node.batteryLevel ?: 0,
                    ),
                    category = Notification.Category.Battery,
                    id = node.num,
                )
            // Recovery may have ended this episode while its text was resolving.
            if (lowBatteryEpisodes[node.num] !== episode) return
            shown = notificationManager.dispatch(notification)
        } finally {
            // A warning that never showed ends its own episode, so the next low reading tries again.
            if (!shown && lowBatteryEpisodes[node.num] === episode) lowBatteryEpisodes.remove(node.num)
        }
    }

    override fun cancelLowBatteryNotification(nodeNum: Int) {
        lowBatteryEpisodes.remove(nodeNum)
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

    // The reconnect-blocked notice is about an Android runtime permission; the web never raises it.
    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean = false

    override suspend fun cancelMessageNotification(contactKey: String) {
        notificationManager.cancel(contactKey.hashCode())
    }
}
