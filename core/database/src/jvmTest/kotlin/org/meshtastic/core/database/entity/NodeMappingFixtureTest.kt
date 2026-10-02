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
package org.meshtastic.core.database.entity

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeMappingFixtureTest {

    /**
     * The round-trip test only proves a field survives if the fixture sets it. A data class exposes one `componentN`
     * per constructor property, so this fails as soon as [Node] gains a property the fixture leaves at its default.
     */
    @Test
    fun `the round trip fixture sets every Node constructor property`() {
        val components =
            Node::class
                .java
                .methods
                .filter { it.name.matches(COMPONENT) && it.parameterCount == 0 }
                .sortedBy { it.name.removePrefix("component").toInt() }
        val fixture = fullyPopulatedNode()
        val defaults = Node(num = 0)

        assertTrue(components.isNotEmpty())
        val unset = components.filter { it.invoke(fixture) == it.invoke(defaults) }.map { it.name }
        assertEquals(emptyList(), unset, "fixture leaves these Node properties at their defaults")
    }

    private companion object {
        val COMPONENT = Regex("component\\d+")
    }
}
