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
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.atomic.AtomicInteger

/** The `clicked` event is the only one that activates an item; hosts also send hover/opened/closed. */
private const val EVENT_CLICKED = "clicked"

/**
 * Serves the tray's context menu over `com.canonical.dbusmenu`.
 *
 * Every method name here is PascalCase because it is a D-Bus member name on the wire and dbus-java derives the wire
 * name from the Java method name — hence the file-level naming suppression above.
 *
 * The menu is rebuilt from [menuProvider] on every read rather than cached, which is what lets a row that appears later
 * — the "update available" item — show up without emitting a `LayoutUpdated` signal. Hosts call `AboutToShow` before
 * opening the menu, so the rebuild happens exactly when it is needed.
 *
 * Every method runs on a dbus-java reader thread. [onItemClicked] is responsible for getting back to the UI thread;
 * nothing in this class touches Compose state directly.
 */
internal class DbusMenuExport(
    private val objectPath: String,
    private val menuProvider: () -> List<TrayMenuItem>,
    private val onItemClicked: (TrayMenuItem) -> Unit,
) : DbusMenu,
    Properties {

    /** Bumped whenever a read observes a different menu, so hosts that cache by revision re-fetch. */
    private val revision = AtomicInteger(1)
    private var lastLabels: List<String> = emptyList()

    override fun getObjectPath(): String = objectPath

    override fun isRemote(): Boolean = false

    override fun GetLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): DbusMenuLayout<UInt32, DbusMenuNode> {
        val items = currentItems()
        val root =
            if (parentId == ROOT_ID) {
                val children = items.mapIndexed { index, item -> dbusMenuChildVariant(item, dbusMenuIdForIndex(index)) }
                DbusMenuNode(ROOT_ID, dbusMenuRootProperties(), children)
            } else {
                // A host asking for a single row: answer with that leaf and no children.
                val item = items.itemForDbusMenuId(parentId)
                DbusMenuNode(parentId, item?.let(::dbusMenuItemProperties).orEmpty(), emptyList())
            }
        return DbusMenuLayout(UInt32(revision.get().toLong()), root)
    }

    override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DbusMenuNodeProperties> {
        val items = currentItems()
        val requested = ids.ifEmpty { items.indices.map(::dbusMenuIdForIndex) }
        return requested.mapNotNull { id ->
            items.itemForDbusMenuId(id)?.let { item -> DbusMenuNodeProperties(id, dbusMenuItemProperties(item)) }
        }
    }

    override fun GetProperty(id: Int, name: String): Variant<*> {
        val item = currentItems().itemForDbusMenuId(id) ?: return Variant("")
        return dbusMenuItemProperties(item)[name] ?: Variant("")
    }

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId != EVENT_CLICKED) return
        val item = currentItems().itemForDbusMenuId(id)
        if (item == null) {
            // A stale id from a host whose cached layout predates the current menu; dropping it is correct.
            Logger.d { "Ignoring dbusmenu click for unknown item id $id" }
            return
        }
        onItemClicked(item)
    }

    override fun AboutToShow(id: Int): Boolean {
        // Reading the menu here refreshes the revision, and returning true invites a fresh GetLayout.
        currentItems()
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (dbusMenuProperties()[propertyName] ?: Variant("")) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        // Every dbusmenu property this object exposes is read-only; hosts never write them.
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = dbusMenuProperties()

    /** Reads the menu and bumps [revision] when it differs from the last observation. */
    private fun currentItems(): List<TrayMenuItem> {
        val items = menuProvider()
        val labels = items.map { it.label }
        if (labels != lastLabels) {
            lastLabels = labels
            revision.incrementAndGet()
        }
        return items
    }
}
