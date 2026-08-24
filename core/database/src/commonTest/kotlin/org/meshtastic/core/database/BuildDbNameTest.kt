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
package org.meshtastic.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BuildDbNameTest {
    @Test
    fun `no-device sentinels all resolve to DEFAULT_DB_NAME`() {
        val expected = DatabaseConstants.DEFAULT_DB_NAME
        val sentinels =
            listOf(null, "", "   ", "\t", "n", "N", "null", "NULL", "Null", ".n", ".N", "default", "DEFAULT", "Default")
        sentinels.forEach { sentinel ->
            assertEquals(
                expected,
                buildDbName(sentinel),
                "Sentinel ${sentinel?.let { "'$it'" } ?: "null"} must collapse to DEFAULT_DB_NAME",
            )
        }
    }

    @Test
    fun `BLE MAC address hashes consistently regardless of punctuation or case`() {
        val colonUpper = buildDbName("AA:BB:CC:DD:EE:FF")
        val noColonLower = buildDbName("aabbccddeeff")
        val colonMixed = buildDbName("aA:Bb:cC:dD:eE:fF")

        assertEquals(colonUpper, noColonLower)
        assertEquals(colonUpper, colonMixed)
    }

    @Test
    fun `non-default real device address does not equal DEFAULT_DB_NAME`() {
        val realDb = buildDbName("AA:BB:CC:DD:EE:FF")
        assertNotEquals(DatabaseConstants.DEFAULT_DB_NAME, realDb)
        // Real device DB names are still scoped under the standard prefix.
        assertTrue(realDb.startsWith("${DatabaseConstants.DB_PREFIX}_"))
    }
}
