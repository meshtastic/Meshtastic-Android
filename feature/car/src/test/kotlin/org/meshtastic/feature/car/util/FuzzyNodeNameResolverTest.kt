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
package org.meshtastic.feature.car.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyNodeNameResolverTest {

    private val resolver = FuzzyNodeNameResolver()

    private val testNodes =
        listOf(1 to "Alice Base Station", 2 to "Bob Mobile", 3 to "Charlie Repeater", 4 to "Delta Gateway")

    @Test
    fun `resolve returns exact match with high confidence`() {
        val result = resolver.resolve("Alice Base Station", testNodes)
        assertNotNull(result)
        assertEquals(1, result.nodeNum)
        assertEquals(1f, result.confidence)
    }

    @Test
    fun `resolve handles case-insensitive matching`() {
        val result = resolver.resolve("alice base station", testNodes)
        assertNotNull(result)
        assertEquals(1, result.nodeNum)
    }

    @Test
    fun `resolve returns partial match with sufficient confidence`() {
        val result = resolver.resolve("Alice Base Staton", testNodes)
        assertNotNull(result)
        assertEquals(1, result.nodeNum)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun `resolve returns null for blank input`() {
        assertNull(resolver.resolve("", testNodes))
        assertNull(resolver.resolve("   ", testNodes))
    }

    @Test
    fun `resolve returns null for empty node list`() {
        assertNull(resolver.resolve("Alice", emptyList()))
    }

    @Test
    fun `resolve returns null for low-confidence match`() {
        assertNull(resolver.resolve("zzz", testNodes))
    }

    @Test
    fun `resolve picks best match among similar names`() {
        val nodes = listOf(1 to "Charlie Alpha", 2 to "Charlie Bravo")
        val result = resolver.resolve("Charlie Bravo", nodes)
        assertNotNull(result)
        assertEquals(2, result.nodeNum)
    }
}
