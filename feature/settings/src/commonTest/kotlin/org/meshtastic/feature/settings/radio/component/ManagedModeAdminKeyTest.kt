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
package org.meshtastic.feature.settings.radio.component

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagedModeAdminKeyTest {
    private val key = ByteArray(32) { (it + 1).toByte() }.toByteString()

    private fun managed(vararg keys: ByteString) = Config.SecurityConfig.Builder()
        .also { wb ->
            wb.admin_key = keys.toList()
            wb.is_managed = true
        }
        .build()

    @Test
    fun `removing the last admin key turns managed mode off`() {
        val updated = managed(key).withAdminKeys(emptyList())

        assertFalse(updated.is_managed)
        assertTrue(updated.admin_key.isEmpty())
    }

    @Test
    fun `leaving only blank slots turns managed mode off`() {
        val updated = managed(key).withAdminKeys(listOf(ByteString.EMPTY))

        assertFalse(updated.is_managed)
        assertFalse(updated.hasAdminKey())
    }

    @Test
    fun `removing one of several admin keys keeps managed mode on`() {
        val other = ByteArray(32) { (it + 2).toByte() }.toByteString()
        val updated = managed(key, other).withAdminKeys(listOf(other))

        assertTrue(updated.is_managed)
        assertEquals(listOf(other), updated.admin_key)
    }
}
