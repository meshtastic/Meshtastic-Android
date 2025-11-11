/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseManagerEvictionTest {
    private val a = "meshtastic_database_a111111111"
    private val b = "meshtastic_database_b222222222"
    private val c = "meshtastic_database_c333333333"
    private val d = "meshtastic_database_d444444444"
    private val legacy = DatabaseConstants.LEGACY_DB_NAME // "meshtastic_database"
    private val defaultDb = DatabaseConstants.DEFAULT_DB_NAME // "meshtastic_database_default"

    @Test
    fun `does not evict when count equals limit`() {
        val names = listOf(a, b, c)
        val victims =
            selectEvictionVictims(names, activeDbName = a, limit = 3, lastUsedMsByDb = names.associateWith { 100L })
        assertTrue(victims.isEmpty())
    }

    @Test
    fun `never evicts active even if oldest`() {
        val names = listOf(a, b, c, d)
        val lastUsed = mapOf(a to 1L, b to 2L, c to 3L, d to 4L)
        val victims = selectEvictionVictims(names, activeDbName = a, limit = 3, lastUsedMsByDb = lastUsed)
        // Oldest overall is a, but active must not be evicted -> next oldest is b
        assertEquals(listOf(b), victims)
    }

    @Test
    fun `evicts two oldest when over limit by two`() {
        val names = listOf(a, b, c, d)
        val lastUsed = mapOf(a to 10L, b to 20L, c to 30L, d to 40L)
        val victims = selectEvictionVictims(names, activeDbName = d, limit = 2, lastUsedMsByDb = lastUsed)
        // Need to evict 2; oldest are a, then b
        assertEquals(listOf(a, b), victims)
    }

    @Test
    fun `excludes legacy and default from accounting`() {
        val names = listOf(a, b, legacy, defaultDb)
        val lastUsed = mapOf(a to 10L, b to 5L)
        val victims = selectEvictionVictims(names, activeDbName = a, limit = 1, lastUsedMsByDb = lastUsed)
        // Only device DBs a & b are counted; with limit 1 and active=a, evict b
        assertEquals(listOf(b), victims)
    }
}
