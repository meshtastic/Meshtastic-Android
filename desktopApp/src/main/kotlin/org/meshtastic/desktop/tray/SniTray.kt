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
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

private const val ITEM_PATH = "/StatusNotifierItem"
private const val MENU_PATH = "/MenuBar"
private const val WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher"
private const val WATCHER_PATH = "/StatusNotifierWatcher"

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
 * A registered StatusNotifierItem, owning the D-Bus connection it lives on.
 *
 * This exists because AWT's tray is XEmbed, and both GNOME and KDE Plasma dropped XEmbed: they surface AWT's icon
 * through an XEmbed-to-SNI proxy (`xembedsniproxy`) that renders it but never delivers clicks back to `TrayIcon`.
 * Speaking SNI directly is what makes the icon clickable — see [canHideToTray], which only permits hide-to-tray on
 * Linux once an item here has actually registered.
 */
internal class SniTray private constructor(private val connection: DBusConnection, private val busName: String) :
    AutoCloseable {

    override fun close() {
        // Closing the connection unexports both objects and drops the bus name, which is the whole teardown;
        // dbus-java has no per-object unexport.
        runCatching {
            connection.releaseBusName(busName)
            connection.close()
        }
            .onFailure { Logger.w(it) { "Error while tearing down the StatusNotifierItem tray" } }
    }

    companion object {
        /**
         * Registers a tray item, or returns `null` when the desktop cannot host one.
         *
         * Returning `null` rather than throwing is load-bearing: the caller treats it as "no tray", which keeps the
         * close button quitting instead of hiding into something unreachable. That is also what makes this safe in CI
         * and under a bare window manager, where there is no session bus or no watcher at all.
         */
        fun install(
            appId: String,
            title: String,
            tooltip: String,
            iconRasters: List<TrayIconRaster>,
            menuProvider: () -> List<TrayMenuItem>,
            onActivate: () -> Unit,
            onItemClicked: (TrayMenuItem) -> Unit,
        ): SniTray? {
            if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()) {
                Logger.i { "No DBUS_SESSION_BUS_ADDRESS; skipping the StatusNotifierItem tray" }
                return null
            }
            var connection: DBusConnection? = null
            return runCatching {
                val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
                val opened = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                connection = opened
                opened.requestBusName(busName)
                opened.exportObject(ITEM_PATH, SniTrayItem(appId, title, tooltip, iconRasters, onActivate))
                opened.exportObject(MENU_PATH, DbusMenuExport(MENU_PATH, menuProvider, onItemClicked))
                opened
                    .getRemoteObject(WATCHER_BUS_NAME, WATCHER_PATH, StatusNotifierWatcher::class.java, true)
                    .RegisterStatusNotifierItem(busName)
                Logger.i { "Registered StatusNotifierItem tray as $busName" }
                SniTray(opened, busName)
            }
                .getOrElse { error ->
                    Logger.i(error) { "No StatusNotifierItem host available; the tray will be unavailable" }
                    runCatching { connection?.close() }
                    null
                }
        }
    }
}
