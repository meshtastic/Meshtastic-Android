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
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MagicNumber")

package org.meshtastic.desktop.tray

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * `(iiay)` — a single icon raster: width, height, and ARGB32 pixels in network byte order.
 *
 * `@field:Position` is deliberate: dbus-java reads struct members from annotated *fields*, and `@Position` is declared
 * `@Target(FIELD)`, so the use-site target has to name the backing field.
 */
internal class SniPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val pixels: ByteArray,
) : Struct()

/**
 * `(sa(iiay)ss)` — tooltip icon name, icon pixmaps, title and body text.
 *
 * The `@Position` indices are the struct's wire layout, not magic numbers, which is why this file suppresses
 * `MagicNumber`; every function name here is likewise a D-Bus member name, hence the naming suppressions.
 */
internal class SniToolTip(
    @field:Position(0) val iconName: String,
    @field:Position(1) val iconPixmaps: List<SniPixmap>,
    @field:Position(2) val title: String,
    @field:Position(3) val description: String,
) : Struct()

/**
 * `(ia{sv}av)` — one dbusmenu node: its id, its properties, and its children as a list of variants each wrapping a
 * further node.
 */
internal class DbusMenuNode(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>,
    @field:Position(2) val children: List<Variant<*>>,
) : Struct()

/** `a(ia{sv})` — one entry of a `GetGroupProperties` reply. */
internal class DbusMenuNodeProperties(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>,
) : Struct()

/**
 * The two out-arguments of `GetLayout`: a revision counter and the subtree.
 *
 * Generic on purpose, and not for type safety. dbus-java expands a [Tuple] into separate out-arguments only when it
 * meets it as a `ParameterizedType`, taking the argument types from `getActualTypeArguments()` — a raw `Tuple` subclass
 * is rejected outright as a non-exportable type, whatever its `@Position` fields say. So the type parameters are what
 * make the reply marshal as `u` plus `(ia{sv}av)` rather than fail.
 *
 * It must stay a [Tuple] rather than becoming a [Struct]: a struct would wrap both values in one out-argument, giving
 * `(u(ia{sv}av))`, and a host rejects that signature.
 */
internal class DbusMenuLayout<A, B>(@field:Position(0) val revision: A, @field:Position(1) val root: B) : Tuple()

/** The registry every StatusNotifierItem announces itself to; owned by the panel (kded6 under Plasma). */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

/**
 * The tray item itself.
 *
 * Hosts read the item's state through `org.freedesktop.DBus.Properties` rather than accessors here, so the exported
 * object implements [org.freedesktop.dbus.interfaces.Properties] alongside this.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)

    fun SecondaryActivate(x: Int, y: Int)

    fun ContextMenu(x: Int, y: Int)

    fun Scroll(delta: Int, orientation: String)
}

/**
 * The menu hosts render on right-click, addressed by integer node id with 0 as the root.
 *
 * `AboutToShow` is the hook that keeps the menu honest: hosts call it before opening, which is when the layout is
 * rebuilt, so an item that appears later (the update row) shows up without needing a `LayoutUpdated` signal.
 */
@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DbusMenu : DBusInterface {
    fun GetLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): DbusMenuLayout<UInt32, DbusMenuNode>

    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DbusMenuNodeProperties>

    fun GetProperty(id: Int, name: String): Variant<*>

    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)

    fun AboutToShow(id: Int): Boolean
}
