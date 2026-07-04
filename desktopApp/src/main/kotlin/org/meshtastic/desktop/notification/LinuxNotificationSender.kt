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
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
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

    fun notify_notification_set_hint(notification: Pointer, key: String, value: Pointer?)

    fun notify_notification_show(notification: Pointer, error: PointerByReference?): Boolean

    fun notify_uninit()
}

/** Minimal GLib bindings for GVariant creation and GObject ref-counting. */
@Suppress("FunctionNaming", "FunctionParameterNaming", "ktlint:standard:function-naming")
private interface GLib : Library {
    fun g_object_unref(obj: Pointer)

    fun g_variant_new_boolean(value: Boolean): Pointer

    fun g_variant_new_string(string: String): Pointer
}

/** JNA mapping of GLib's `GError` struct for extracting error diagnostics from libnotify. */
@Suppress("MagicNumber")
@Structure.FieldOrder("domain", "code", "message")
class GErrorStruct(p: Pointer?) : Structure(p) {
    @JvmField var domain: Int = 0

    @JvmField var code: Int = 0

    @JvmField var message: Pointer? = null

    init {
        if (p != null) read()
    }

    val errorMessage: String
        get() = message?.getString(0) ?: "unknown error"
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
class LinuxNotificationSender(
    private val appName: String = "Meshtastic",
    private val desktopEntry: String = appName.lowercase(),
) : NativeNotificationSender {

    private val lib: LibNotify?
    private val glib: GLib?

    init {
        var loadedLib: LibNotify? = null
        var loadedGLib: GLib? = null
        try {
            loadedLib = Native.load("notify", LibNotify::class.java) as LibNotify
            loadedGLib = Native.load("gobject-2.0", GLib::class.java) as GLib
            if (loadedLib.notify_init(appName)) {
                Logger.i { "libnotify initialized for '$appName'" }
            } else {
                Logger.w { "notify_init('$appName') returned false" }
                loadedLib = null
                loadedGLib = null
            }
        } catch (e: UnsatisfiedLinkError) {
            Logger.w(e) { "libnotify not available — native Linux notifications disabled" }
            loadedLib = null
            loadedGLib = null
        }
        lib = loadedLib
        glib = loadedGLib
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

        applyMetadata(libnotify, ptr, notification)

        val errorRef = PointerByReference()
        return try {
            val shown = libnotify.notify_notification_show(ptr, errorRef)
            if (!shown) {
                val errMsg = errorRef.value?.let { GErrorStruct(it).errorMessage } ?: "unknown"
                Logger.w { "notify_notification_show failed for '${notification.title}': $errMsg" }
            }
            shown
        } finally {
            glib?.g_object_unref(ptr)
        }
    }

    private fun applyMetadata(libnotify: LibNotify, ptr: Pointer, notification: Notification) {
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

        // desktop-entry hint associates notifications with the app's .desktop file,
        // enabling proper icon resolution and notification grouping by the daemon.
        glib?.let { g ->
            libnotify.notify_notification_set_hint(ptr, "desktop-entry", g.g_variant_new_string(desktopEntry))
        }

        if (notification.isSilent) {
            glib?.let { g ->
                libnotify.notify_notification_set_hint(ptr, "suppress-sound", g.g_variant_new_boolean(true))
            }
        }
    }
}
