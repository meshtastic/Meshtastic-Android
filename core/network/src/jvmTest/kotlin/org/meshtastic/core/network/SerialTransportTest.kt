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
package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SerialTransportTest {
/*

    private val mockService: RadioInterfaceService = mockk(relaxed = true)

    @Test
    fun testJSerialCommIsAvailable() {
        val ports = SerialPort.getCommPorts()
        assertNotNull(ports, "Serial ports array should not be null")
    }

    @Test
    fun testSerialTransportImplementsRadioTransport() {
        val transport: RadioTransport = SerialTransport("dummyPort", service = mockService)
        assertTrue(transport is SerialTransport, "Transport should be a SerialTransport")
    }

    @Test
    fun testGetAvailablePorts() {
        val ports = SerialTransport.getAvailablePorts()
        assertNotNull(ports, "Available ports should not be null")
    }

    @Test
    fun testConnectToInvalidPortFailsGracefully() {
        val transport = SerialTransport("invalid_port_name", 115200, mockService)
        val connected = transport.startConnection()
        assertFalse(connected, "Connecting to an invalid port should return false")
        transport.close()
    }

*/
}
