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
package org.meshtastic.desktop.tray

import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// The property dictionaries and node structs `com.canonical.dbusmenu` is built out of.
//
// These live apart from DbusMenuExport so that class holds only the protocol methods: everything here is a
// pure value builder with no connection or menu state behind it, which is also what makes it directly
// testable.

/** dbusmenu's own signature for a menu node, needed when nesting nodes inside the children variant list. */
private const val NODE_SIGNATURE = "(ia{sv}av)"

/** Protocol revision this implementation speaks (`com.canonical.dbusmenu` version 3). */
private const val DBUSMENU_VERSION = 3L

/** Properties of one visible row. dbusmenu infers a plain command row from `type` = `standard`. */
internal fun dbusMenuItemProperties(item: TrayMenuItem): Map<String, Variant<*>> = mapOf(
    "label" to Variant(item.label),
    "enabled" to Variant(true),
    "visible" to Variant(true),
    "type" to Variant("standard"),
)

/** Properties of the root node; `children-display` = `submenu` is what makes a host render its children. */
internal fun dbusMenuRootProperties(): Map<String, Variant<*>> = mapOf("children-display" to Variant("submenu"))

/** The interface-level properties a host reads off the menu object itself. */
internal fun dbusMenuProperties(): Map<String, Variant<*>> = mapOf(
    "Version" to Variant(UInt32(DBUSMENU_VERSION)),
    "Status" to Variant("normal"),
    "TextDirection" to Variant("ltr"),
    "IconThemePath" to Variant(emptyList<String>(), "as"),
)

/**
 * Wraps one row as a child of the root.
 *
 * The explicit [NODE_SIGNATURE] is load-bearing: children travel as `av`, and without the signature the variant would
 * carry the struct's inferred type rather than the `(ia{sv}av)` the host demands.
 */
internal fun dbusMenuChildVariant(item: TrayMenuItem, id: Int): Variant<DbusMenuNode> =
    Variant(DbusMenuNode(id, dbusMenuItemProperties(item), emptyList()), NODE_SIGNATURE)

/** The dbusmenu id for the row at [index], given that 0 is reserved for the root. */
internal fun dbusMenuIdForIndex(index: Int): Int = index + ROOT_ID + 1
