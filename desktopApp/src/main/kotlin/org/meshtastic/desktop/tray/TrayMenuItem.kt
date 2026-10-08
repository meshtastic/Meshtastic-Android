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

/**
 * One row of the tray's context menu.
 *
 * The same model drives Compose's AWT-backed `Tray` on macOS and Windows and the StatusNotifierItem menu on Linux, so
 * the labels and their order cannot drift apart between platforms.
 */
internal data class TrayMenuItem(val label: String, val onClick: () -> Unit)

/**
 * The menu as the tray backends consume it: an ordered list whose position is the item's identity.
 *
 * dbusmenu addresses items by integer id, with 0 reserved for the root, so an item's id is its index + 1. Rebuilding
 * the list is therefore enough to change the menu — see [DbusMenuExport].
 */
internal fun List<TrayMenuItem>.itemForDbusMenuId(id: Int): TrayMenuItem? = getOrNull(id - ROOT_ID - 1)

/** dbusmenu's reserved id for the root node, whose children are the visible rows. */
internal const val ROOT_ID: Int = 0
