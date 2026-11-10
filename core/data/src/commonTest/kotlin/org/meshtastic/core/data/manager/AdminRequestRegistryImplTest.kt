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
package org.meshtastic.core.data.manager

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminRequestRegistryImplTest {

    private val registry = AdminRequestRegistryImpl()

    @Test
    fun `a registered id is consumed once`() {
        registry.register(42)

        assertTrue(registry.consume(42))
        assertFalse(registry.consume(42))
    }

    @Test
    fun `an unknown id is not consumed`() {
        assertFalse(registry.consume(7))
    }

    @Test
    fun `id zero is never outstanding`() {
        registry.register(0)

        assertFalse(registry.consume(0))
    }

    @Test
    fun `clearAll forgets every outstanding id`() {
        registry.register(1)
        registry.register(2)

        registry.clearAll()

        assertFalse(registry.consume(1))
        assertFalse(registry.consume(2))
    }

    @Test
    fun `the oldest id is dropped past the bound`() {
        (1..129).forEach(registry::register)

        assertFalse(registry.consume(1))
        assertTrue(registry.consume(2))
        assertTrue(registry.consume(129))
    }
}
