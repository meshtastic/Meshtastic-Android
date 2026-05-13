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
package org.meshtastic.feature.node.component

import org.meshtastic.core.model.NodeListDensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeListDensityTest {

    @Test
    fun valid_complete_string_maps_to_complete() {
        assertEquals(NodeListDensity.COMPLETE, NodeListDensity.fromName("COMPLETE"))
    }

    @Test
    fun valid_compact_string_maps_to_compact() {
        assertEquals(NodeListDensity.COMPACT, NodeListDensity.fromName("COMPACT"))
    }

    @Test
    fun invalid_string_falls_back_to_complete() {
        assertEquals(NodeListDensity.COMPLETE, NodeListDensity.fromName("GARBAGE"))
    }

    @Test
    fun empty_string_falls_back_to_complete() {
        assertEquals(NodeListDensity.COMPLETE, NodeListDensity.fromName(""))
    }

    @Test
    fun lowercase_does_not_match_and_falls_back() {
        assertEquals(NodeListDensity.COMPLETE, NodeListDensity.fromName("compact"))
    }

    @Test
    fun firstOrNull_returns_null_for_unknown() {
        assertNull(NodeListDensity.entries.firstOrNull { it.name == "UNKNOWN" })
    }
}
