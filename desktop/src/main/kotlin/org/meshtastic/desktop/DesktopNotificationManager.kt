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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.desktop.notification.NativeNotificationSender
import androidx.compose.ui.window.Notification as ComposeNotification

/**
 * Desktop notification manager that dispatches domain [Notification] objects to native OS notifications.
 *
 * Uses platform-specific [NativeNotificationSender] implementations (notify-send on Linux, osascript on macOS,
 * PowerShell toast on Windows) for proper native look-and-feel. Falls back to Compose Desktop tray notifications (via
 * [fallbackNotifications]) when the native sender is unavailable or fails.
 *
 * All native sends are dispatched on a background scope to avoid blocking callers.
 *
 * Registered manually in `desktopPlatformStubsModule` -- do **not** add `@Single` to avoid double-registration with the
 * `@ComponentScan("org.meshtastic.desktop")` in [DesktopDiModule][org.meshtastic.desktop.di.DesktopDiModule].
 */
class DesktopNotificationManager(
    private val prefs: NotificationPrefs,
    private val nativeSender: NativeNotificationSender,
) : NotificationManager {

    @Suppress("InjectDispatcher")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Logger.i { "DesktopNotificationManager initialized (native sender: ${nativeSender::class.simpleName})" }
    }

    private val _fallbackNotifications = MutableSharedFlow<ComposeNotification>(extraBufferCapacity = 10)

    /**
     * Fallback flow of Compose [ComposeNotification] objects, emitted only when the native sender fails. Collected by
     * the tray composable in Main.kt as a last resort.
     */
    val fallbackNotifications: SharedFlow<ComposeNotification> = _fallbackNotifications.asSharedFlow()

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

        scope.launch {
            val success = nativeSender.send(notification)
            if (!success) {
                Logger.w { "Native notification failed, falling back to tray: ${notification.title}" }
                emitFallback(notification)
            }
        }
    }

    private fun emitFallback(notification: Notification) {
        val composeType =
            when (notification.type) {
                Notification.Type.None -> ComposeNotification.Type.None
                Notification.Type.Info -> ComposeNotification.Type.Info
                Notification.Type.Warning -> ComposeNotification.Type.Warning
                Notification.Type.Error -> ComposeNotification.Type.Error
            }
        _fallbackNotifications.tryEmit(ComposeNotification(notification.title, notification.message, composeType))
    }

    override fun cancel(id: Int) {
        // Native OS notifications are fire-and-forget; cancel is best-effort.
        Logger.d { "cancel($id) — not supported by current native senders" }
    }

    override fun cancelAll() {
        // Native OS notifications are fire-and-forget; cancelAll is best-effort.
        Logger.d { "cancelAll() — not supported by current native senders" }
    }
}
