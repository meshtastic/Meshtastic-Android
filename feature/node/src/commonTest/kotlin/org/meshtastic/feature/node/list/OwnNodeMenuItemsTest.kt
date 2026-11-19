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
package org.meshtastic.feature.node.list

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnNodeMenuItemsTest {

    private fun node(num: Int, firmware: String?) = Node(
        num = num,
        metadata = firmware?.let { DeviceMetadata.Builder().also { wb -> wb.firmware_version = it }.build() },
    )

    @Test
    fun `a connected local node on 2 8 firmware offers every item`() {
        val ours = node(1, "2.8.0")

        val items = ownNodeMenuItems(ours, ours, ConnectionState.Connected)

        assertTrue(items.updateStatus && items.editName && items.shareContact)
        assertTrue(items.any)
    }

    @Test
    fun `old firmware without status messages still offers edit name and share contact`() {
        val ours = node(1, "2.7.0")

        val items = ownNodeMenuItems(ours, ours, ConnectionState.Connected)

        assertFalse(items.updateStatus)
        assertTrue(items.editName)
        assertTrue(items.shareContact)
    }

    @Test
    fun `firmware before contact sharing offers edit name only`() {
        val ours = node(1, "2.6.0")

        val items = ownNodeMenuItems(ours, ours, ConnectionState.Connected)

        assertFalse(items.shareContact)
        assertTrue(items.editName)
    }

    @Test
    fun `a disconnected local node offers nothing`() {
        val ours = node(1, "2.8.0")

        assertFalse(ownNodeMenuItems(ours, ours, ConnectionState.Disconnected).any)
    }

    @Test
    fun `another node offers nothing`() {
        val ours = node(1, "2.8.0")

        assertFalse(ownNodeMenuItems(node(2, "2.8.0"), ours, ConnectionState.Connected).any)
    }
}
