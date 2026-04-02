/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.proto.HardwareModel

class NodeTest {

    @Test
    fun `createFallback produces expected node data`() {
        val nodeNum = 0x12345678
        val prefix = "Node"
        val node = Node.createFallback(nodeNum, prefix)

        assertEquals(nodeNum, node.num)
        assertEquals("!12345678", node.user.id)
        assertEquals("Node 5678", node.user.long_name)
        assertEquals("5678", node.user.short_name)
        assertEquals(HardwareModel.UNSET, node.user.hw_model)
    }

    @Test
    fun `createFallback pads short IDs with zeros`() {
        val nodeNum = 0x1
        val prefix = "Node"
        val node = Node.createFallback(nodeNum, prefix)

        assertEquals(nodeNum, node.num)
        assertEquals("!00000001", node.user.id)
        assertEquals("Node 0001", node.user.long_name)
        assertEquals("0001", node.user.short_name)
    }
}
