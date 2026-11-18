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

    // --- isSessionFatalBleException tests ---

    @Test
    fun `isSessionFatalBleException returns true for NotConnectedException`() {
        assertTrue(NotConnectedException("test").isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException returns true for fatal GATT status codes`() {
        assertTrue(GattStatusException(status = 133, message = "test").isSessionFatalBleException())
        assertTrue(GattStatusException(status = 8, message = "test").isSessionFatalBleException())
        assertTrue(GattStatusException(status = 129, message = "test").isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException returns true for peer-disconnect and establishment-failure codes`() {
        // 19 = GATT_CONN_TERMINATE_PEER_USER — firmware reboot / peer-initiated disconnect
        assertTrue(GattStatusException(status = 19, message = "peer disconnect").isSessionFatalBleException())
        // 22 = GATT_CONN_LMP_TIMEOUT — link manager protocol timeout
        assertTrue(GattStatusException(status = 22, message = "lmp timeout").isSessionFatalBleException())
        // 62 = GATT_CONN_FAIL_ESTABLISH — connection establishment failed
        assertTrue(GattStatusException(status = 62, message = "establish failed").isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException returns false for transient GATT status`() {
        assertFalse(GattStatusException(status = 6, message = "busy").isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException returns false for unrelated exceptions`() {
        assertFalse(IllegalStateException("test").isSessionFatalBleException())
        assertFalse(RuntimeException("test").isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException traverses cause chain for wrapped exceptions`() {
        val fatal = GattStatusException(status = 133, message = "wrapped fatal")
        val wrapper = RuntimeException("wrapper", fatal)
        assertTrue(wrapper.isSessionFatalBleException())

        val notConnected = NotConnectedException("wrapped")
        val doubleWrapper = IllegalStateException("outer", RuntimeException("middle", notConnected))
        assertTrue(doubleWrapper.isSessionFatalBleException())
    }

    @Test
    fun `isSessionFatalBleException returns false for non-fatal cause chain`() {
        val transient = GattStatusException(status = 6, message = "busy")
        val wrapper = RuntimeException("wrapper", transient)
        assertFalse(wrapper.isSessionFatalBleException())
    }
}
