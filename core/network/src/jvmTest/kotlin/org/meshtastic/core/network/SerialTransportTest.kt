package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import io.mockk.mockk
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SerialTransportTest {
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
}
