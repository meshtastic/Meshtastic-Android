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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import androidx.compose.ui.window.Notification as ComposeNotification

@Single
class DesktopNotificationManager(private val prefs: NotificationPrefs) : NotificationManager {
    private val _notifications = MutableSharedFlow<ComposeNotification>(extraBufferCapacity = 10)
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

        if (!enabled) return

        val composeType =
            when (notification.type) {
                Notification.Type.None -> ComposeNotification.Type.None
                Notification.Type.Info -> ComposeNotification.Type.Info
                Notification.Type.Warning -> ComposeNotification.Type.Warning
                Notification.Type.Error -> ComposeNotification.Type.Error
            }

        _notifications.tryEmit(ComposeNotification(notification.title, notification.message, composeType))
    }

    override fun cancel(id: Int) {
        // Desktop Tray notifications cannot be cancelled once sent via TrayState
    }

    override fun cancelAll() {
        // Desktop Tray notifications cannot be cleared once sent via TrayState
    }
}
