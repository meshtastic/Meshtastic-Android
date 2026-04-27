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

import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.meshtastic.core.repository.Notification

/**
 * JNA bindings for libnotify (libnotify.so / libnotify-4.so).
 *
 * Only the minimal API surface needed for fire-and-forget desktop notifications is exposed. See:
 * https://developer-old.gnome.org/libnotify/stable/
 */
@Suppress("FunctionNaming", "FunctionParameterNaming", "ktlint:standard:function-naming")
private interface LibNotify : Library {
    fun notify_init(app_name: String): Boolean

    fun notify_notification_new(summary: String, body: String?, icon: String?): Pointer?

    fun notify_notification_set_urgency(notification: Pointer, urgency: Int)

    fun notify_notification_set_category(notification: Pointer, category: String)

    fun notify_notification_set_hint_string(notification: Pointer, key: String, value: String)

    fun notify_notification_show(notification: Pointer, error: Pointer?): Boolean

    fun notify_uninit()
}

/** Minimal GLib GObject binding for releasing native objects allocated by libnotify. */
@Suppress("FunctionNaming", "FunctionParameterNaming", "ktlint:standard:function-naming")
private interface GObject : Library {
    fun g_object_unref(obj: Pointer)
}

/** libnotify urgency levels matching `NotifyUrgency` enum. */
private object NotifyUrgency {
    const val LOW = 0
    const val NORMAL = 1
    const val CRITICAL = 2
}

/**
 * Sends notifications via libnotify on Linux, called directly through JNA.
 *
 * This avoids shelling out to `notify-send` and gives direct access to the notification daemon via D-Bus, providing
 * proper urgency, category, and sound suppression support.
 *
 * Requires `libnotify` (typically `libnotify4` or `libnotify.so.4`) to be installed on the system. Falls back
 * gracefully if the library cannot be loaded.
 */
class LinuxNotificationSender(private val appName: String = "Meshtastic") : NativeNotificationSender {

    private val lib: LibNotify?
    private val gobject: GObject?

    init {
        var loadedLib: LibNotify? = null
        var loadedGObject: GObject? = null
        try {
            loadedLib = Native.load("notify", LibNotify::class.java) as LibNotify
            loadedGObject = Native.load("gobject-2.0", GObject::class.java) as GObject
            if (loadedLib.notify_init(appName)) {
                Logger.i { "libnotify initialized for '$appName'" }
            } else {
                Logger.w { "notify_init('$appName') returned false" }
                loadedLib = null
                loadedGObject = null
            }
        } catch (e: UnsatisfiedLinkError) {
            Logger.w(e) { "libnotify not available — native Linux notifications disabled" }
            loadedLib = null
            loadedGObject = null
        }
        lib = loadedLib
        gobject = loadedGObject
    }

    /** Whether libnotify was successfully loaded and initialized. */
    val isAvailable: Boolean
        get() = lib != null

    @Suppress("ReturnCount")
    override fun send(notification: Notification): Boolean {
        val libnotify = lib ?: return false

        val ptr =
            libnotify.notify_notification_new(
                notification.title,
                notification.message,
                null, // icon — could be set to an app icon path in the future
            )
                ?: run {
                    Logger.w { "notify_notification_new returned null" }
                    return false
                }

        val urgency =
            when (notification.type) {
                Notification.Type.Error -> NotifyUrgency.CRITICAL
                Notification.Type.Warning -> NotifyUrgency.NORMAL
                else -> NotifyUrgency.LOW
            }
        libnotify.notify_notification_set_urgency(ptr, urgency)

        val category =
            when (notification.category) {
                Notification.Category.Message -> "im.received"
                Notification.Category.Battery -> "device.warning"
                Notification.Category.Alert -> "device.error"
                Notification.Category.NodeEvent -> "network"
                Notification.Category.Service -> "device"
            }
        libnotify.notify_notification_set_category(ptr, category)

        if (notification.isSilent) {
            libnotify.notify_notification_set_hint_string(ptr, "suppress-sound", "true")
        }

        return try {
            val shown = libnotify.notify_notification_show(ptr, null)
            if (!shown) {
                Logger.w { "notify_notification_show returned false for: ${notification.title}" }
            }
            shown
        } finally {
            gobject?.g_object_unref(ptr)
        }
    }
}
