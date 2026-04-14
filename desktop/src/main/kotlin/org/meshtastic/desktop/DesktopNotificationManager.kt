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
package org.meshtastic.desktop

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import androidx.compose.ui.window.Notification as ComposeNotification

/**
 * Desktop notification manager that bridges domain [Notification] objects to Compose Desktop tray notifications.
 *
 * Notifications are emitted via [notifications] and collected by the tray composable in [Main.kt]. Respects user
 * preferences for message, node-event, and low-battery categories.
 *
 * Registered manually in `desktopPlatformStubsModule` -- do **not** add `@Single` to avoid double-registration with the
 * `@ComponentScan("org.meshtastic.desktop")` in [DesktopDiModule][org.meshtastic.desktop.di.DesktopDiModule].
 */
class DesktopNotificationManager(private val prefs: NotificationPrefs) : NotificationManager {
    init {
        Logger.i { "DesktopNotificationManager initialized" }
    }

    private val _notifications = MutableSharedFlow<ComposeNotification>(extraBufferCapacity = 10)

    /** Flow of Compose [ComposeNotification] objects to be forwarded to [TrayState.sendNotification]. */
    val notifications: SharedFlow<ComposeNotification> = _notifications.asSharedFlow()

    override fun dispatch(notification: Notification) {
        val enabled =
            when (notification.category) {
                Notification.Category.Message -> prefs.messagesEnabled.value
                Notification.Category.NodeEvent -> prefs.nodeEventsEnabled.value
                Notification.Category.Battery -> prefs.lowBatteryEnabled.value
                Notification.Category.Alert -> true
                Notification.Category.Service -> true
            }

        Logger.d { "DesktopNotificationManager dispatch: category=${notification.category}, enabled=$enabled" }

        if (!enabled) return

        val composeType =
            when (notification.type) {
                Notification.Type.None -> ComposeNotification.Type.None
                Notification.Type.Info -> ComposeNotification.Type.Info
                Notification.Type.Warning -> ComposeNotification.Type.Warning
                Notification.Type.Error -> ComposeNotification.Type.Error
            }

        val success = _notifications.tryEmit(ComposeNotification(notification.title, notification.message, composeType))
        Logger.d { "DesktopNotificationManager emit: success=$success, title=${notification.title}" }
    }

    override fun cancel(id: Int) {
        // Desktop tray notifications cannot be cancelled once sent via TrayState.
    }

    override fun cancelAll() {
        // Desktop tray notifications cannot be cleared once sent via TrayState.
    }
}
