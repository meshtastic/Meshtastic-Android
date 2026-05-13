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
package org.meshtastic.core.ble

import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [classifyBleException] — the boundary between Kable types and the transport layer.
 *
 * [GattRequestRejectedException] and [UnmetRequirementException] have `internal` constructors in Kable, so they cannot
 * be instantiated from outside the library. The `else -> null` branch covers the fallback for any unrecognised
 * throwable.
 */
class BleExceptionClassifierTest {

    @Test
    fun `GattStatusException maps to non-permanent with status code`() {
        val ex = GattStatusException(message = "GATT failure", status = 133)
        val info = ex.classifyBleException()
        assertNotNull(info)
        assertFalse(info.isPermanent)
        assertEquals(133, info.gattStatus)
        assertTrue(info.message.contains("133"))
    }

    @Test
    fun `NotConnectedException maps to non-permanent without status code`() {
        val ex = NotConnectedException("disconnected")
        val info = ex.classifyBleException()
        assertNotNull(info)
        assertFalse(info.isPermanent)
        assertNull(info.gattStatus)
        assertEquals("Not connected", info.message)
    }

    @Test
    fun `unrelated exception returns null`() {
        val ex = IllegalStateException("something else")
        assertNull(ex.classifyBleException())
    }

    @Test
    fun `RuntimeException returns null`() {
        assertNull(RuntimeException("boom").classifyBleException())
    }
}
