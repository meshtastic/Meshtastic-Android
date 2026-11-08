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
    fun `saving with no admin key turns managed mode off`() {
        val saved = managed().forSave()

        assertFalse(saved.is_managed)
        assertTrue(saved.admin_key.isEmpty())
    }

    @Test
    fun `saving with only blank or short slots turns managed mode off`() {
        val short = ByteArray(1) { 1 }.toByteString()
        val saved = managed(ByteString.EMPTY, short).forSave()

        assertFalse(saved.hasAdminKey())
        assertFalse(saved.is_managed)
    }

    @Test
    fun `a blank slot before a valid key keeps managed mode on`() {
        val config = managed(ByteString.EMPTY, key)

        assertTrue(config.hasAdminKey())
        assertEquals(config, config.forSave())
    }

    @Test
    fun `clearing the sole key mid edit keeps the managed choice until save`() {
        val editing = managed(key).newBuilder().also { wb -> wb.admin_key = listOf(ByteString.EMPTY) }.build()
        assertTrue(editing.is_managed)

        val replaced = editing.newBuilder().also { wb -> wb.admin_key = listOf(key) }.build()
        assertTrue(replaced.forSave().is_managed)
    }
}
