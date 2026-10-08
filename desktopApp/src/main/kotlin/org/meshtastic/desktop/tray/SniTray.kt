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
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package org.meshtastic.desktop.tray

import co.touchlab.kermit.Logger
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val ITEM_PATH = "/StatusNotifierItem"
private const val MENU_PATH = "/MenuBar"
private const val WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher"
private const val WATCHER_PATH = "/StatusNotifierWatcher"
private const val DBUS_BUS_NAME = "org.freedesktop.DBus"
private const val DBUS_PATH = "/org/freedesktop/DBus"

/** D-Bus signatures the host is strict about; an unannotated variant would marshal as the wrong type. */
private const val PIXMAP_ARRAY_SIGNATURE = "a(iiay)"
private const val TOOLTIP_SIGNATURE = "(sa(iiay)ss)"
private const val OBJECT_PATH_SIGNATURE = "o"

/**
 * The tray item a StatusNotifierItem host reads and clicks.
 *
 * Hosts fetch state through `org.freedesktop.DBus.Properties`, not through accessors, so this implements [Properties]
 * as well. Methods run on dbus-java reader threads; [onActivate] is responsible for reaching the UI thread.
 */
internal class SniTrayItem(
    private val appId: String,
    private val title: String,
    private val tooltip: String,
    private val iconRasters: List<TrayIconRaster>,
    private val onActivate: () -> Unit,
) : StatusNotifierItem,
    Properties {

    override fun getObjectPath(): String = ITEM_PATH

    override fun isRemote(): Boolean = false

    override fun Activate(x: Int, y: Int) = onActivate()

    override fun SecondaryActivate(x: Int, y: Int) = onActivate()

    override fun ContextMenu(x: Int, y: Int) {
        // Nothing to do: the host renders the menu at the `Menu` object path itself. Hosts differ in whether
        // they call this at all, which is why the menu must not depend on it.
    }

    override fun Scroll(delta: Int, orientation: String) {
        // The tray icon has no scroll behaviour.
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (itemProperties()[propertyName] ?: Variant("")) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        // Every property this item exposes is read-only.
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = itemProperties()

    private fun pixmaps(): List<SniPixmap> = iconRasters.map { raster ->
        SniPixmap(raster.width, raster.height, raster.argbBigEndian)
    }

    private fun itemProperties(): Map<String, Variant<*>> {
        val pixmaps = pixmaps()
        return mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant(appId),
            "Title" to Variant(title),
            "Status" to Variant("Active"),
            "IconName" to Variant(""),
            "IconPixmap" to Variant(pixmaps, PIXMAP_ARRAY_SIGNATURE),
            "ToolTip" to Variant(SniToolTip("", pixmaps, title, tooltip), TOOLTIP_SIGNATURE),
            // ItemIsMenu=false plus a real Menu path is what makes a host open our dbusmenu on right-click
            // while still delivering left-click to Activate.
            "ItemIsMenu" to Variant(false),
            "Menu" to Variant(DBusPath(MENU_PATH), OBJECT_PATH_SIGNATURE),
        )
    }
}

/**
 * A StatusNotifierItem exported on the session bus, owning the connection it lives on.
 *
 * This exists because AWT's tray is XEmbed, and both GNOME and KDE Plasma dropped XEmbed: they surface AWT's icon
 * through an XEmbed-to-SNI proxy (`xembedsniproxy`) that renders it but never delivers clicks back to `TrayIcon`.
 * Speaking SNI directly is what makes the icon clickable.
 *
 * Registration is *not* a one-time fact. A watcher (kded6 under Plasma, the AppIndicator extension under GNOME) can be
 * absent at startup and appear later, or restart and lose its registry. So the item tracks `NameOwnerChanged` for the
 * watcher name, re-registers whenever a new owner appears, and reports every transition through `onRegistrationChange`.
 * [canHideToTray] consumes that, which is what stops the close button from hiding the window into a tray that is no
 * longer there.
 *
 * Every method here blocks on D-Bus round trips. Callers must keep it off the composition thread — see [SniTrayEffect],
 * which confines all of it to a single dedicated thread.
 */
internal class SniTray
private constructor(
    private val connection: DBusConnection,
    private val ownedBusName: String?,
    private val watcherSubscription: AutoCloseable?,
    private val reregisterExecutor: ExecutorService,
) : AutoCloseable {

    override fun close() {
        // Each step gets its own guard. Sharing one runCatching meant a failing releaseBusName — a lost
        // connection, say — skipped close(), leaking the connection and its reader threads, and the leak
        // repeated on every re-registration.
        runCatching { watcherSubscription?.close() }
            .onFailure { Logger.w(it) { "Failed to drop the StatusNotifierWatcher subscription" } }
        runCatching { reregisterExecutor.shutdownNow() }
            .onFailure { Logger.w(it) { "Failed to stop the tray re-registration executor" } }
        ownedBusName?.let { name ->
            runCatching { connection.releaseBusName(name) }
                .onFailure { Logger.w(it) { "Failed to release the tray bus name $name" } }
        }
        runCatching { connection.close() }.onFailure { Logger.w(it) { "Failed to close the tray D-Bus connection" } }
    }

    companion object {
        /**
         * Exports a tray item, or returns `null` when the session has no D-Bus to export onto.
         *
         * Returning `null` rather than throwing is load-bearing: the caller treats it as "no tray", which keeps the
         * close button quitting instead of hiding into something unreachable. That is also what makes this safe in CI
         * and under a bare window manager.
         *
         * A successful return does **not** mean a host is showing the icon — that is reported through
         * [onRegistrationChange], which fires with the state at install time and again on every watcher change. Blocks
         * on several D-Bus round trips; call it off the UI thread.
         */
        @Suppress("LongParameterList")
        fun install(
            appId: String,
            title: String,
            tooltip: String,
            iconRasters: List<TrayIconRaster>,
            menuProvider: () -> List<TrayMenuItem>,
            onActivate: () -> Unit,
            onItemClicked: (TrayMenuItem) -> Unit,
            onRegistrationChange: (Boolean) -> Unit,
        ): SniTray? {
            if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()) {
                Logger.i { "No DBUS_SESSION_BUS_ADDRESS; skipping the StatusNotifierItem tray" }
                return null
            }
            var connection: DBusConnection? = null
            return runCatching {
                val opened = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                connection = opened
                val ownedBusName = requestItemBusName(opened)
                val busName = ownedBusName ?: opened.uniqueName
                opened.exportObject(ITEM_PATH, SniTrayItem(appId, title, tooltip, iconRasters, onActivate))
                opened.exportObject(MENU_PATH, DbusMenuExport(MENU_PATH, menuProvider, onItemClicked))

                val executor = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "sni-tray-reregister").apply { isDaemon = true }
                }
                // Subscribe before the first check, so a watcher that appears in between is not missed.
                val subscription = subscribeToWatcher(opened, busName, executor, onRegistrationChange)
                val registered = registerIfWatcherPresent(opened, busName)
                onRegistrationChange(registered)
                SniTray(opened, ownedBusName, subscription, executor)
            }
                .getOrElse { error ->
                    Logger.i(error) { "Could not export the StatusNotifierItem tray" }
                    runCatching { connection?.close() }
                    onRegistrationChange(false)
                    null
                }
        }

        /**
         * Claims the conventional `org.kde.StatusNotifierItem-<pid>-1` name, or returns `null` when the bus refuses it.
         *
         * A Flatpak sandbox refuses it twice over: the manifest grants `--talk-name` for the watcher but no
         * `--own-name`, and every sandboxed process sees itself as a low pid, so the name would collide across apps
         * anyway. The watcher also accepts the connection's unique name, with the item at the standard path, so the
         * caller registers under that instead — the same fallback Electron uses (electron/electron#53641).
         */
        private fun requestItemBusName(connection: DBusConnection): String? {
            val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            return runCatching { connection.requestBusName(name) }
                .onFailure { Logger.i(it) { "Could not own $name; registering the tray by unique name instead" } }
                .map { name }
                .getOrNull()
        }

        /**
         * Registers with the watcher if one currently owns the name, reporting whether it did.
         *
         * `NameHasOwner` first, and `getRemoteObject` with autostart disabled, so a desktop with no watcher at all
         * answers immediately instead of waiting out a bus activation attempt.
         */
        private fun registerIfWatcherPresent(connection: DBusConnection, busName: String): Boolean {
            val daemon = connection.getRemoteObject(DBUS_BUS_NAME, DBUS_PATH, DBus::class.java, false)
            if (!daemon.NameHasOwner(WATCHER_BUS_NAME)) {
                Logger.i { "No $WATCHER_BUS_NAME on the bus; the tray icon will appear if one starts later" }
                return false
            }
            return runCatching { registerWithWatcher(connection, busName) }
                .onFailure { Logger.i(it) { "A StatusNotifierWatcher is present but refused the item" } }
                .isSuccess
        }

        private fun registerWithWatcher(connection: DBusConnection, busName: String) {
            connection
                .getRemoteObject(WATCHER_BUS_NAME, WATCHER_PATH, StatusNotifierWatcher::class.java, false)
                .RegisterStatusNotifierItem(busName)
            Logger.i { "Registered StatusNotifierItem tray as $busName" }
        }

        /**
         * Watches the watcher: re-registers when a new one takes the name, and reports its disappearance.
         *
         * The re-registration is handed to [executor] rather than run inline, because the handler runs on a dbus-java
         * delivery thread and a blocking outbound call from there can wedge that thread.
         */
        private fun subscribeToWatcher(
            connection: DBusConnection,
            busName: String,
            executor: ExecutorService,
            onRegistrationChange: (Boolean) -> Unit,
        ): AutoCloseable = connection.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
            if (signal.name != WATCHER_BUS_NAME) return@addSigHandler
            if (signal.newOwner.isNullOrEmpty()) {
                Logger.i { "$WATCHER_BUS_NAME went away; the tray icon is gone until one returns" }
                onRegistrationChange(false)
                return@addSigHandler
            }
            executor.execute {
                val registered = runCatching {
                    registerWithWatcher(connection, busName)
                }
                    .onFailure { Logger.i(it) { "A new StatusNotifierWatcher refused the item" } }
                    .isSuccess
                onRegistrationChange(registered)
            }
        }
    }
}
