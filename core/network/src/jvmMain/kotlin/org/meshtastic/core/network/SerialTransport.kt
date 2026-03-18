package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import org.meshtastic.core.repository.RadioTransport

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm.
 * Currently a stub for testing architecture.
 */
class SerialTransport : RadioTransport {
    
    override fun handleSendToRadio(p: ByteArray) {
        // Stub implementation
    }

    override fun keepAlive() {
        // Stub implementation
    }

    override fun close() {
        // Stub implementation
    }

    companion object {
        /**
         * Discovers and returns a list of available serial ports.
         * Returns a list of the system port names (e.g., "COM3", "/dev/ttyUSB0").
         */
        fun getAvailablePorts(): List<String> {
            return SerialPort.getCommPorts().map { it.systemPortName }
        }
    }
}
