package org.meshtastic.core.network

import co.touchlab.kermit.Logger
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.meshtastic.core.network.radio.StreamInterface
import org.meshtastic.core.repository.RadioInterfaceService

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm.
 * Uses [StreamInterface] for START1/START2 packet framing.
 */
class SerialTransport(
    private val portName: String,
    private val baudRate: Int = 115200,
    service: RadioInterfaceService
) : StreamInterface(service) {
    private var serialPort: SerialPort? = null
    private var readJob: Job? = null

    /**
     * Attempts to open the serial port and starts the read loop.
     * Returns true if successful, false otherwise.
     */
    fun startConnection(): Boolean {
        return try {
            val port = SerialPort.getCommPort(portName) ?: return false
            port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0)
            if (port.openPort()) {
                serialPort = port
                super.connect() // Sends WAKE_BYTES and signals service.onConnect()
                startReadLoop(port)
                true
            } else {
                false
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "Serial connection failed" }
            false
        }
    }

    private fun startReadLoop(port: SerialPort) {
        readJob = service.serviceScope.launch(Dispatchers.IO) {
            val input = port.inputStream
            try {
                while (isActive && port.isOpen) {
                    try {
                        val c = input.read()
                        if (c == -1) break
                        readChar(c.toByte())
                    } catch (_: SerialPortTimeoutException) {
                        // Expected timeout when no data is available
                        continue
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Serial read loop error" }
            } finally {
                close()
            }
        }
    }

    override fun sendBytes(p: ByteArray) {
        serialPort?.takeIf { it.isOpen }?.outputStream?.write(p)
    }

    override fun flushBytes() {
        serialPort?.takeIf { it.isOpen }?.outputStream?.flush()
    }

    override fun keepAlive() {
        // Not specifically needed for raw serial unless implemented
    }

    override fun close() {
        readJob?.cancel()
        readJob = null
        serialPort?.takeIf { it.isOpen }?.closePort()
        serialPort = null
        super.close()
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
