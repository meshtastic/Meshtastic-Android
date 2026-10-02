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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DbusMenuExportTest {

    private val clicked = mutableListOf<String>()
    private var menu = listOf<TrayMenuItem>()

    private fun export() = DbusMenuExport(
        objectPath = "/MenuBar",
        menuProvider = { menu },
        onItemClicked = { item -> item.onClick() },
    )

    private fun item(label: String) = TrayMenuItem(label) { clicked += label }

    private fun labelOf(variant: Variant<*>): String {
        val node = variant.value as DbusMenuNode
        return node.properties.getValue("label").value as String
    }

    @Test
    fun `root layout lists the menu in order with ids starting after the root`() {
        menu = listOf(item("Show"), item("Quit"))

        val layout = export().GetLayout(ROOT_ID, 1, emptyList())

        assertEquals(ROOT_ID, layout.root.id)
        assertEquals(listOf("Show", "Quit"), layout.root.children.map(::labelOf))
        assertEquals(listOf(ROOT_ID + 1, ROOT_ID + 2), layout.root.children.map { (it.value as DbusMenuNode).id })
    }

    @Test
    fun `root advertises itself as a submenu so hosts render its children`() {
        menu = listOf(item("Quit"))

        val layout = export().GetLayout(ROOT_ID, 1, emptyList())

        assertEquals("submenu", layout.root.properties.getValue("children-display").value)
    }

    @Test
    fun `a click routes to the item holding that id`() {
        menu = listOf(item("Update"), item("Show"), item("Quit"))

        export().Event(ROOT_ID + 3, "clicked", Variant(""), UInt32(0L))

        assertEquals(listOf("Quit"), clicked)
    }

    @Test
    fun `a non-click event does not activate anything`() {
        menu = listOf(item("Quit"))

        export().Event(ROOT_ID + 1, "hovered", Variant(""), UInt32(0L))

        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `a click on an id the menu no longer has is dropped`() {
        // A host can hold a cached layout from before the update row disappeared.
        menu = listOf(item("Quit"))

        export().Event(ROOT_ID + 99, "clicked", Variant(""), UInt32(0L))

        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `the revision advances when the menu changes and holds when it does not`() {
        val export = export()
        menu = listOf(item("Show"), item("Quit"))

        val first = export.GetLayout(ROOT_ID, 1, emptyList()).revision.toLong()
        val unchanged = export.GetLayout(ROOT_ID, 1, emptyList()).revision.toLong()
        menu = listOf(item("Update"), item("Show"), item("Quit"))
        val afterChange = export.GetLayout(ROOT_ID, 1, emptyList()).revision.toLong()

        assertEquals(first, unchanged, "an unchanged menu must not invalidate the host's cache")
        assertTrue(afterChange > first, "a changed menu must bump the revision")
    }

    @Test
    fun `group properties cover every row when the host asks for all of them`() {
        menu = listOf(item("Show"), item("Quit"))

        val properties = export().GetGroupProperties(emptyList(), emptyList())

        assertEquals(listOf(ROOT_ID + 1, ROOT_ID + 2), properties.map { it.id })
        assertEquals(listOf("Show", "Quit"), properties.map { it.properties.getValue("label").value })
    }

    @Test
    fun `group properties skip ids the menu does not have`() {
        menu = listOf(item("Quit"))

        val properties = export().GetGroupProperties(listOf(ROOT_ID + 1, ROOT_ID + 42), emptyList())

        assertEquals(listOf(ROOT_ID + 1), properties.map { it.id })
    }

    @Test
    fun `rows report themselves enabled and visible`() {
        menu = listOf(item("Quit"))

        val export = export()

        assertEquals(true, export.GetProperty(ROOT_ID + 1, "enabled").value)
        assertEquals(true, export.GetProperty(ROOT_ID + 1, "visible").value)
        assertEquals("Quit", export.GetProperty(ROOT_ID + 1, "label").value)
    }

    @Test
    fun `AboutToShow invites the host to re-read the layout`() {
        menu = listOf(item("Quit"))

        assertTrue(export().AboutToShow(ROOT_ID))
    }

    @Test
    fun `declares dbusmenu version three`() {
        val version = export().GetAll("com.canonical.dbusmenu").getValue("Version").value as UInt32
        assertEquals(3L, version.toLong())
    }

    @Test
    fun `child nodes carry the explicit dbusmenu node signature`() {
        // Children travel as `av`. Without the explicit signature the variant would advertise the struct's
        // inferred type and the host would reject the layout — invisible to every other assertion here.
        val variant = dbusMenuChildVariant(item("Quit"), dbusMenuIdForIndex(0))

        assertEquals("(ia{sv}av)", variant.sig)
    }

    @Test
    fun `row ids start after the reserved root id`() {
        assertEquals(ROOT_ID + 1, dbusMenuIdForIndex(0))
        assertEquals(ROOT_ID + 3, dbusMenuIdForIndex(2))
    }

    @Test
    fun `is a local object so dbus-java exports rather than proxies it`() {
        val export = export()

        assertEquals("/MenuBar", export.getObjectPath())
        assertTrue(!export.isRemote())
        assertNotNull(export.GetAll("com.canonical.dbusmenu")["Status"])
    }
}
