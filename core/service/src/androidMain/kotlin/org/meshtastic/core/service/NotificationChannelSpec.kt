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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.net.toUri
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.R.raw
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.device
import org.meshtastic.core.resources.meshtastic_alerts_notifications
import org.meshtastic.core.resources.meshtastic_alerts_notifications_description
import org.meshtastic.core.resources.meshtastic_broadcast_notifications
import org.meshtastic.core.resources.meshtastic_broadcast_notifications_description
import org.meshtastic.core.resources.meshtastic_client_notifications
import org.meshtastic.core.resources.meshtastic_client_notifications_description
import org.meshtastic.core.resources.meshtastic_device_status_notifications
import org.meshtastic.core.resources.meshtastic_device_status_notifications_description
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications_description
import org.meshtastic.core.resources.meshtastic_low_battery_temporary_remote_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_temporary_remote_notifications_description
import org.meshtastic.core.resources.meshtastic_mesh_beacon_notifications
import org.meshtastic.core.resources.meshtastic_mesh_beacon_notifications_description
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications_description
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications_description
import org.meshtastic.core.resources.meshtastic_service_notifications
import org.meshtastic.core.resources.meshtastic_service_notifications_description
import org.meshtastic.core.resources.meshtastic_waypoints_notifications
import org.meshtastic.core.resources.meshtastic_waypoints_notifications_description
import org.meshtastic.core.resources.messages
import org.meshtastic.core.resources.notification_group_mesh

/** Meshtastic brand accent (Green 500, see .skills/design-standards): the small-icon tint and the LED colour. */
internal val NOTIFICATION_COLOR = 0xFF67EA94.toInt()

/**
 * Every notification channel the app owns. The platform lets an app change a channel's name, description and (once)
 * group after creation; importance, sound, vibration and lights are fixed the first time the channel is created, so an
 * importance here must never drop below what an earlier release created for the same id.
 */
internal enum class NotificationChannelSpec(
    val id: String,
    val nameRes: StringResource,
    val descriptionRes: StringResource,
    val group: NotificationChannelGroupSpec,
    val importance: Int,
) {
    // A foreground-service channel below LOW is raised to LOW by the platform on first use anyway.
    Service(
        NotificationChannels.SERVICE,
        Res.string.meshtastic_service_notifications,
        Res.string.meshtastic_service_notifications_description,
        NotificationChannelGroupSpec.Device,
        NotificationManager.IMPORTANCE_LOW,
    ),
    DirectMessages(
        NotificationChannels.MESSAGES,
        Res.string.meshtastic_messages_notifications,
        Res.string.meshtastic_messages_notifications_description,
        NotificationChannelGroupSpec.Messages,
        NotificationManager.IMPORTANCE_HIGH,
    ),
    Broadcasts(
        NotificationChannels.BROADCASTS,
        Res.string.meshtastic_broadcast_notifications,
        Res.string.meshtastic_broadcast_notifications_description,
        NotificationChannelGroupSpec.Messages,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    Waypoints(
        NotificationChannels.WAYPOINTS,
        Res.string.meshtastic_waypoints_notifications,
        Res.string.meshtastic_waypoints_notifications_description,
        NotificationChannelGroupSpec.Messages,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    Alerts(
        NotificationChannels.ALERTS,
        Res.string.meshtastic_alerts_notifications,
        Res.string.meshtastic_alerts_notifications_description,
        NotificationChannelGroupSpec.Messages,
        NotificationManager.IMPORTANCE_HIGH,
    ),
    NewNodes(
        NotificationChannels.NEW_NODES,
        Res.string.meshtastic_new_nodes_notifications,
        Res.string.meshtastic_new_nodes_notifications_description,
        NotificationChannelGroupSpec.Mesh,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    MeshBeacon(
        NotificationChannels.MESH_BEACON,
        Res.string.meshtastic_mesh_beacon_notifications,
        Res.string.meshtastic_mesh_beacon_notifications_description,
        NotificationChannelGroupSpec.Mesh,
        NotificationManager.IMPORTANCE_LOW,
    ),
    LowBatteryRemote(
        NotificationChannels.LOW_BATTERY_REMOTE,
        Res.string.meshtastic_low_battery_temporary_remote_notifications,
        Res.string.meshtastic_low_battery_temporary_remote_notifications_description,
        NotificationChannelGroupSpec.Mesh,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    LowBattery(
        NotificationChannels.LOW_BATTERY,
        Res.string.meshtastic_low_battery_notifications,
        Res.string.meshtastic_low_battery_notifications_description,
        NotificationChannelGroupSpec.Device,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    Client(
        NotificationChannels.CLIENT,
        Res.string.meshtastic_client_notifications,
        Res.string.meshtastic_client_notifications_description,
        NotificationChannelGroupSpec.Device,
        NotificationManager.IMPORTANCE_HIGH,
    ),
    DeviceStatus(
        NotificationChannels.DEVICE_STATUS,
        Res.string.meshtastic_device_status_notifications,
        Res.string.meshtastic_device_status_notifications_description,
        NotificationChannelGroupSpec.Device,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    ;

    fun toChannel(context: Context, name: String, description: String): NotificationChannel =
        NotificationChannel(id, name, importance).also { channel ->
            channel.description = description
            channel.group = group.id
            channel.lightColor = NOTIFICATION_COLOR
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(true)
            when (this) {
                Service -> {
                    channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    channel.setShowBadge(false)
                }

                Alerts -> {
                    channel.enableLights(true)
                    channel.enableVibration(true)
                    val alertSound = "$SCHEME_ANDROID_RESOURCE://${context.packageName}/${raw.meshtastic_alert}".toUri()
                    channel.setSound(alertSound, soundAttributes(AudioAttributes.USAGE_ALARM))
                }

                LowBatteryRemote -> {
                    channel.enableVibration(true)
                    channel.setSound(defaultSound, soundAttributes(AudioAttributes.USAGE_NOTIFICATION))
                }

                DirectMessages,
                Broadcasts,
                Waypoints,
                NewNodes,
                LowBattery,
                DeviceStatus,
                -> channel.setSound(defaultSound, soundAttributes(AudioAttributes.USAGE_NOTIFICATION))

                MeshBeacon,
                Client,
                -> Unit
            }
        }

    private companion object {
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        fun soundAttributes(usage: Int): AudioAttributes =
            AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    }
}

internal enum class NotificationChannelGroupSpec(val id: String, val nameRes: StringResource) {
    Messages("group_messages", Res.string.messages),
    Mesh("group_mesh", Res.string.notification_group_mesh),
    Device("group_device", Res.string.device),
    ;

    fun toGroup(name: String): NotificationChannelGroup = NotificationChannelGroup(id, name)
}
