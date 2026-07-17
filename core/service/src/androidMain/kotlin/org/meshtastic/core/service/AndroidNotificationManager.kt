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

import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.R.drawable
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_alerts_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_mesh_beacon_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.resources.meshtastic_service_notifications
import android.app.NotificationManager as SystemNotificationManager

@Single
class AndroidNotificationManager(private val context: Context) : NotificationManager {

    private val notificationManager =
        checkNotNull(context.getSystemService<SystemNotificationManager>()) { "NotificationManager not found" }

    private data class ChannelConfig(val id: String, val importance: Int)

    /**
     * Tracks whether notification channels have been created.
     *
     * Channels are **not** created in the constructor because this singleton is instantiated by Koin during
     * [org.meshtastic.core.service.MeshService.onCreate] on the main thread. The CMP [getString] helper uses
     * [kotlinx.coroutines.runBlocking] which can fail in that context, crashing the entire service startup chain.
     * Instead, channels are lazily ensured before the first [dispatch] call. Note that
     * [MeshNotificationManagerImpl.initChannels] already creates a superset of these channels when the orchestrator
     * starts, so this lazy path is only a safety net for notifications dispatched before orchestrator initialization.
     */
    private var channelsInitialized = false

    private fun ensureChannelsInitialized() {
        if (channelsInitialized) return
        channelsInitialized = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels =
                listOf(
                    createChannel(Notification.Category.Message, Res.string.meshtastic_messages_notifications),
                    createChannel(Notification.Category.NodeEvent, Res.string.meshtastic_new_nodes_notifications),
                    createChannel(Notification.Category.MeshBeacon, Res.string.meshtastic_mesh_beacon_notifications),
                    createChannel(Notification.Category.Battery, Res.string.meshtastic_low_battery_notifications),
                    createChannel(Notification.Category.Alert, Res.string.meshtastic_alerts_notifications),
                    createChannel(Notification.Category.Service, Res.string.meshtastic_service_notifications),
                )
            notificationManager.createNotificationChannels(channels)
            notificationManager.removeLegacyCategoryChannels()
        }
    }

    private fun createChannel(
        category: Notification.Category,
        nameRes: org.jetbrains.compose.resources.StringResource,
    ): NotificationChannel {
        val channelConfig = category.channelConfig()
        return NotificationChannel(channelConfig.id, getString(nameRes), channelConfig.importance)
    }

    // Keep category-to-channel mapping aligned with MeshNotificationManagerImpl.NotificationType IDs.
    private fun Notification.Category.channelConfig(): ChannelConfig = when (this) {
        Notification.Category.Message ->
            ChannelConfig(
                id = NotificationChannels.MESSAGES,
                importance = SystemNotificationManager.IMPORTANCE_HIGH,
            )

        Notification.Category.NodeEvent ->
            ChannelConfig(
                id = NotificationChannels.NEW_NODES,
                importance = SystemNotificationManager.IMPORTANCE_DEFAULT,
            )

        Notification.Category.MeshBeacon ->
            ChannelConfig(
                id = NotificationChannels.MESH_BEACON,
                importance = SystemNotificationManager.IMPORTANCE_LOW,
            )

        Notification.Category.Battery ->
            ChannelConfig(
                id = NotificationChannels.LOW_BATTERY,
                importance = SystemNotificationManager.IMPORTANCE_DEFAULT,
            )

        Notification.Category.Alert ->
            ChannelConfig(id = NotificationChannels.ALERTS, importance = SystemNotificationManager.IMPORTANCE_HIGH)

        Notification.Category.Service ->
            ChannelConfig(id = NotificationChannels.SERVICE, importance = SystemNotificationManager.IMPORTANCE_MIN)
    }

    override fun dispatch(notification: Notification): Boolean {
        ensureChannelsInitialized()
        val channelId = notification.category.channelConfig().id
        if (!canPostNotifications(channelId)) return false
        val id = notification.id ?: notification.hashCode()
        val builder =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setSmallIcon(drawable.meshtastic_ic_notification)
                .setAutoCancel(true)
                .setSilent(notification.isSilent)

        notification.group?.let { builder.setGroup(it) }

        if (notification.type == Notification.Type.Error) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        notification.deepLinkUri?.let { uri -> builder.setContentIntent(createDeepLinkPendingIntent(uri, id)) }

        return try {
            notificationManager.notify(id, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun canPostNotifications(channelId: String): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    notificationManager.getNotificationChannel(channelId)?.importance !=
                        SystemNotificationManager.IMPORTANCE_NONE
            )

    /**
     * Builds a [PendingIntent] that launches [MainActivity] with the given deep-link URI as [Intent.ACTION_VIEW], so
     * the existing deep-link plumbing (`UIViewModel.handleDeepLink` → `DeepLinkRouter` → `MultiBackstack`) can
     * synthesize the proper backstack and surface the target screen.
     *
     * Uses [Class.forName] to avoid pulling the `:androidApp` module into `:core:service` as a Gradle dep.
     */
    private fun createDeepLinkPendingIntent(uri: String, requestCode: Int): PendingIntent {
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, uri.toUri(), context, Class.forName(MAIN_ACTIVITY_CLASS)).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(requestCode, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!
        }
    }

    override fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
    }

    private companion object {
        /**
         * Fully-qualified name of the host activity that handles `meshtastic://` deep-link intents. Kept as a string to
         * avoid creating a module dependency from `:core:service` back onto `:androidApp`.
         */
        const val MAIN_ACTIVITY_CLASS = "org.meshtastic.app.MainActivity"
    }
}
