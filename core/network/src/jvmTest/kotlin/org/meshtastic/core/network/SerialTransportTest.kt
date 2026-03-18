package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import org.meshtastic.core.repository.RadioTransport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SerialTransportTest {
    @Test
    fun testJSerialCommIsAvailable() {
        val ports = SerialPort.getCommPorts()
        assertNotNull(ports, "Serial ports array should not be null")
    }

    @Test
    fun testSerialTransportImplementsRadioTransport() {
        // Just instantiate to verify it implements the interface and compiles
        val transport: RadioTransport = SerialTransport()
        assertTrue(transport is SerialTransport, "Transport should be a SerialTransport")
    }
}
