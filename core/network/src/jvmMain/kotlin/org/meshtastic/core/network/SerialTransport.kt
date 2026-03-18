package org.meshtastic.core.network

import com.fazecast.jSerialComm.SerialPort
import org.meshtastic.core.repository.RadioTransport

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm.
 */
class SerialTransport(
    private val portName: String,
    private val baudRate: Int = 115200
) : RadioTransport {
    private var serialPort: SerialPort? = null

    /**
     * Attempts to open the serial port.
     * Returns true if successful, false otherwise.
     */
    fun connect(): Boolean {
        return try {
            val port = SerialPort.getCommPort(portName) ?: return false
            port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0)
            if (port.openPort()) {
                serialPort = port
                true
            } else {
                false
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            false
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
        serialPort?.takeIf { it.isOpen }?.outputStream?.write(p)
    }

    override fun keepAlive() {
        // Stub implementation
    }

    override fun close() {
        serialPort?.takeIf { it.isOpen }?.closePort()
        serialPort = null
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
