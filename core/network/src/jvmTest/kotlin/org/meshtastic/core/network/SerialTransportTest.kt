package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import kotlin.test.Test
import kotlin.test.assertNotNull

class SerialTransportTest {
    @Test
    fun testJSerialCommIsAvailable() {
        val ports = SerialPort.getCommPorts()
        assertNotNull(ports, "Serial ports array should not be null")
    }
}
