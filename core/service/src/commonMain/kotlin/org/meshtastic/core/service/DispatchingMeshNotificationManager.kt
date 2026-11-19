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

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.navigation.NotificationDeepLinks
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
 * The [MeshNotificationManager] for platforms that show plain notifications through a [NotificationManager] (desktop
 * and web): turns each mesh event into a [Notification] record in the slot and with the click destination Android uses
 * for the same event, and leaves the gating on preferences and permission to the [NotificationManager].
 *
 * Android-only concepts (notification channels, the foreground-service notification, conversation history) are no-ops
 * here.
 */
@Suppress("TooManyFunctions")
class DispatchingMeshNotificationManager(private val notificationManager: NotificationManager) :
    MeshNotificationManager {

    private val lock = SynchronizedObject()
    private val lowBatteryEpisodes = mutableMapOf<Int, Any>()

    override fun clearNotifications() {
        synchronized(lock) { lowBatteryEpisodes.clear() }
        notificationManager.cancelAll()
    }

    override fun initChannels() {
        // No-op: only Android has notification channels.
    }

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        // No-op: only Android has a foreground-service notification.
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
                deepLink = NotificationDeepLinks.conversation(contactKey),
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
                id = contactKey.hashCode(),
                deepLink = NotificationDeepLinks.waypoint(waypointId),
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
                category = Notification.Category.Reaction,
                isSilent = isSilent,
                id = contactKey.hashCode(),
                deepLink = NotificationDeepLinks.conversation(contactKey),
            ),
        )
    }

    override suspend fun showAlertNotification(contactKey: String, name: String, alert: String) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = alert,
                category = Notification.Category.Alert,
                id = contactKey.hashCode(),
                deepLink = NotificationDeepLinks.conversation(contactKey),
            ),
        )
    }

    override suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer) {
        notificationManager.dispatch(
            Notification(
                title = getStringSuspend(Res.string.mesh_beacon_notification_title),
                message = offer.message.ifBlank { getStringSuspend(Res.string.mesh_beacon_notification_body) },
                category = Notification.Category.MeshBeacon,
                id = offer.fromNodeNum,
                deepLink = NotificationDeepLinks.MESH_BEACON,
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
                deepLink = NotificationDeepLinks.nodeDetail(node.num),
            ),
        )
    }

    override fun cancelNewNodeNotification(nodeNum: Int) {
        notificationManager.cancel(Notification.Category.NodeEvent, nodeNum)
    }

    // Not every platform can refresh a notification in place, and re-posting would alert again, so only an episode's
    // first reading shows.
    override suspend fun notifyLowBattery(node: Node, isRemote: Boolean) {
        val episode = Any()
        val started = synchronized(lock) { lowBatteryEpisodes.getOrPut(node.num) { episode } === episode }
        if (!started) return
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
                    deepLink = NotificationDeepLinks.nodeDetail(node.num),
                )
            // Recovery may have ended this episode while its text was resolving.
            if (synchronized(lock) { lowBatteryEpisodes[node.num] !== episode }) return
            shown = notificationManager.dispatch(notification)
        } finally {
            // A warning that never showed ends its own episode, so the next low reading tries again.
            if (!shown) {
                synchronized(lock) {
                    if (lowBatteryEpisodes[node.num] === episode) lowBatteryEpisodes.remove(node.num)
                }
            }
        }
    }

    override fun cancelLowBatteryNotification(nodeNum: Int) {
        synchronized(lock) { lowBatteryEpisodes.remove(nodeNum) }
        notificationManager.cancel(Notification.Category.Battery, nodeNum)
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
                // The firmware repeats this advisory on every position request; only the first should sound.
                onlyAlertOnce = clientNotification.isProtectedPositionAdvisory(),
            ),
        )
    }

    override fun clearClientNotification(clientNotification: ClientNotification) {
        notificationManager.cancel(Notification.Category.Client, clientNotification.notificationId())
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
                deepLink = NotificationDeepLinks.FIRMWARE_UPDATE,
            ),
        )

    // The reconnect-blocked notice is about an Android runtime permission; no other platform raises it.
    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean = false

    override suspend fun cancelMessageNotification(contactKey: String) {
        val id = contactKey.hashCode()
        notificationManager.cancel(Notification.Category.Message, id)
        notificationManager.cancel(Notification.Category.Reaction, id)
    }
}
