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

import co.touchlab.kermit.Logger
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.radio.StreamTransport
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.repository.RadioTransportCallback
import java.io.File

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm. Uses [StreamTransport] for START1/START2 packet
 * framing.
 *
 * Use the [open] factory method instead of the constructor directly to ensure the serial port is opened and the read
 * loop is started.
 */
class SerialTransport
private constructor(
    private val portName: String,
    private val baudRate: Int = DEFAULT_BAUD_RATE,
    callback: RadioTransportCallback,
    scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : StreamTransport(callback, scope) {
    private var serialPort: SerialPort? = null
    private var readJob: Job? = null

    private val heartbeatSender = HeartbeatSender(sendToRadio = ::handleSendToRadio, logTag = "Serial[$portName]")

    /** Attempts to open the serial port and starts the read loop. Returns true if successful, false otherwise. */
    private fun startConnection(): Boolean {
        return try {
            val port = SerialPort.getCommPort(portName) ?: return false
            port.setComPortParameters(baudRate, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)
            if (port.openPort()) {
                serialPort = port
                port.setDTR()
                port.setRTS()
                Logger.i { "[$portName] Serial port opened (baud=$baudRate)" }
                super.connect() // Sends WAKE_BYTES and signals callback.onConnect()
                startReadLoop(port)
                true
            } else {
                Logger.w { "[$portName] Serial port openPort() returned false" }
                false
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "[$portName] Serial connection failed" }
            false
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun startReadLoop(port: SerialPort) {
        Logger.d { "[$portName] Starting serial read loop" }
        readJob =
            scope.launch(dispatchers.io) {
                val input = port.inputStream
                val buffer = ByteArray(READ_BUFFER_SIZE)
                try {
                    var reading = true
                    while (isActive && port.isOpen && reading) {
                        try {
                            val numRead = input.read(buffer)
                            if (numRead == -1) {
                                reading = false
                            } else if (numRead > 0) {
                                for (i in 0 until numRead) {
                                    readChar(buffer[i])
                                }
                            }
                        } catch (_: SerialPortTimeoutException) {
                            // Expected timeout when no data is available
                        } catch (e: CancellationException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            if (isActive) {
                                Logger.w(e) { "[$portName] Serial read error" }
                            } else {
                                Logger.d { "[$portName] Serial read interrupted by cancellation" }
                            }
                            reading = false
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    if (isActive) {
                        Logger.w(e) { "[$portName] Serial read loop outer error" }
                    } else {
                        Logger.d { "[$portName] Serial read loop interrupted by cancellation" }
                    }
                } finally {
                    Logger.d { "[$portName] Serial read loop exiting" }
                    try {
                        input.close()
                    } catch (_: Exception) {
                        // Ignore errors during input stream close
                    }
                    try {
                        if (port.isOpen) {
                            port.closePort()
                        }
                    } catch (_: Exception) {
                        // Ignore errors during port close
                    }
                    if (isActive) {
                        onDeviceDisconnect(true)
                    }
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
        // Delegate to HeartbeatSender which sends a ToRadio heartbeat to prove the
        // serial link is alive.
        scope.launch { heartbeatSender.sendHeartbeat() }
    }

    private fun closePortResources() {
        serialPort?.takeIf { it.isOpen }?.closePort()
        serialPort = null
    }

    override suspend fun close() {
        Logger.d { "[$portName] Closing serial transport" }
        readJob?.cancel()
        readJob = null
        closePortResources()
        super.close()
    }

    companion object {
        private const val DEFAULT_BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val READ_BUFFER_SIZE = 1024
        private const val READ_TIMEOUT_MS = 100

        /**
         * Creates and opens a [SerialTransport]. If the port cannot be opened, the transport signals a permanent
         * disconnect to the [callback] and returns the (non-connected) instance.
         */
        fun open(
            portName: String,
            baudRate: Int = DEFAULT_BAUD_RATE,
            callback: RadioTransportCallback,
            scope: CoroutineScope,
            dispatchers: CoroutineDispatchers,
        ): SerialTransport {
            val transport = SerialTransport(portName, baudRate, callback, scope, dispatchers)
            if (!transport.startConnection()) {
                val errorMessage = diagnoseOpenFailure(portName)
                Logger.w { "[$portName] Serial port could not be opened; signalling disconnect. $errorMessage" }
                callback.onDisconnect(isPermanent = true, errorMessage = errorMessage)
            }
            return transport
        }

        /**
         * Discovers and returns a list of available serial ports. Returns a list of the system port names (e.g.,
         * "COM3", "/dev/ttyUSB0").
         */
        fun getAvailablePorts(): List<String> = SerialPort.getCommPorts().map { it.systemPortName }

        /**
         * Diagnoses why a serial port could not be opened and returns a user-facing error message. On Linux, checks
         * file permissions and suggests the appropriate group fix.
         */
        @Suppress("ReturnCount")
        private fun diagnoseOpenFailure(portName: String): String {
            val osName = System.getProperty("os.name", "").lowercase()
            if (!osName.contains("linux")) {
                return "Could not open serial port: $portName"
            }

            // jSerialComm resolves bare names like "ttyUSB0" to "/dev/ttyUSB0"
            val devPath = if (portName.startsWith("/")) portName else "/dev/$portName"
            val portFile = File(devPath)
            if (!portFile.exists()) {
                return "Serial port $portName not found. Is the device still connected?"
            }
            if (!portFile.canRead() || !portFile.canWrite()) {
                val group = detectSerialGroup(devPath)
                val user = System.getProperty("user.name", "your_user")
                return "Permission denied for $devPath. " +
                    "Run: sudo usermod -aG $group $user  — then log out and back in."
            }
            return "Could not open serial port: $portName"
        }

        /**
         * Attempts to detect the group that owns the serial device file. Falls back to "dialout" (Debian/Ubuntu
         * default) if detection fails.
         */
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        private fun detectSerialGroup(devPath: String): String = try {
            val process = ProcessBuilder("stat", "-c", "%G", devPath).redirectErrorStream(true).start()
            val group = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            group.ifEmpty { "dialout" }
        } catch (e: Exception) {
            "dialout"
        }
    }
}
