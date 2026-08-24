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
 *
 * **The GLib entry points below MUST stay on this interface.** They are deliberately resolved through the *libnotify*
 * handle rather than a separate `Native.load("gobject-2.0", ...)`. `dlsym()` on a library handle searches that library
 * *and its dependency chain*, and libnotify has a `DT_NEEDED` on `libgobject-2.0.so.0`/`libglib-2.0.so.0` — so this
 * guarantees we get the exact same GLib instance that libnotify itself is bound to.
 *
 * Loading `gobject-2.0` separately is NOT safe: a JVM process can easily have two distinct GLib copies mapped at once
 * (e.g. the JDK/Compose Desktop AWT stack drags in a bundled GTK3 + GLib, while libnotify binds to the system GLib).
 * Each copy owns a private `GType` registry, so freeing a `NotifyNotification` created by one copy with the other
 * copy's `g_object_unref()` walks the wrong registry and jumps through a null vtable slot — an immediate SIGSEGV at
 * `pc=0x0` inside `g_object_unref`, not a catchable exception.
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

    // --- GLib, resolved via libnotify's own dependency chain. See the KDoc above before touching these. ---

    fun g_object_unref(obj: Pointer)

    fun g_error_free(error: Pointer)

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
) : NativeNotificationSender,
    AutoCloseable {

    /**
     * Cleared by [close]. Volatile so a [close] on the shutdown path is visible to any thread that is about to call
     * [send]; a send already past its null check will still complete against the old handle, which is why [close] is
     * documented as "once sends have stopped".
     */
    @Volatile private var lib: LibNotify?

    init {
        var loadedLib: LibNotify? = null
        try {
            loadedLib = Native.load("notify", LibNotify::class.java) as LibNotify
            if (loadedLib.notify_init(appName)) {
                Logger.i { "libnotify initialized for '$appName'" }
            } else {
                Logger.w { "notify_init('$appName') returned false" }
                loadedLib = null
            }
        } catch (e: UnsatisfiedLinkError) {
            Logger.w(e) { "libnotify not available — native Linux notifications disabled" }
            loadedLib = null
        }
        lib = loadedLib
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
            // On failure libnotify hands us ownership of a GError; nothing else frees it, so without this every
            // failed send leaks the struct and its message. Freed here rather than in the `if (!shown)` branch so
            // it is released even if `show()` reports success while still having set an error. The message is read
            // above, before the free — GErrorStruct dereferences the pointer.
            errorRef.value?.let { libnotify.g_error_free(it) }
            libnotify.g_object_unref(ptr)
        }
    }

    /**
     * Releases libnotify's process-wide state — the D-Bus proxy and cached app name allocated by `notify_init()`.
     *
     * Idempotent: the second and later calls are no-ops. Afterwards [isAvailable] is false and [send] returns false
     * rather than calling into a torn-down library.
     *
     * Call this once sends have stopped. `notify_uninit()` is resolved through the libnotify handle, so it runs against
     * the same GLib instance that allocated the state — see the [LibNotify] KDoc for why that matters.
     */
    @Synchronized
    override fun close() {
        val libnotify = lib ?: return
        lib = null
        runCatching { libnotify.notify_uninit() }
            .onFailure { Logger.w(it) { "notify_uninit() failed during shutdown" } }
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
                Notification.Category.MeshBeacon -> "network"
                Notification.Category.Service -> "device"
            }
        libnotify.notify_notification_set_category(ptr, category)

        // desktop-entry hint associates notifications with the app's .desktop file,
        // enabling proper icon resolution and notification grouping by the daemon.
        // The GVariants below are floating refs; notify_notification_set_hint() sinks them, so we must not unref.
        libnotify.notify_notification_set_hint(ptr, "desktop-entry", libnotify.g_variant_new_string(desktopEntry))

        if (notification.isSilent) {
            libnotify.notify_notification_set_hint(ptr, "suppress-sound", libnotify.g_variant_new_boolean(true))
        }
    }
}
