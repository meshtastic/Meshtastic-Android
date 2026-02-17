/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.firmware.ota

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.model.util.nowMillis
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * WiFi/TCP transport implementation for ESP32 Unified OTA protocol.
 *
 * Uses UDP for device discovery on port 3232, then establishes TCP connection for OTA commands and firmware streaming.
 *
 * Unlike BLE, WiFi transport:
 * - Uses synchronous TCP (no manual ACK waiting)
 * - Supports larger chunk sizes (up to 1024 bytes)
 * - Generally faster transfer speeds
 */
class WifiOtaTransport(private val deviceIpAddress: String, private val port: Int = DEFAULT_PORT) : UnifiedOtaProtocol {

    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var isConnected = false

    /** Connect to the device via TCP. */
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Logger.i { "WiFi OTA: Connecting to $deviceIpAddress:$port" }

            socket =
                Socket().apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                    connect(
                        InetSocketAddress(deviceIpAddress, this@WifiOtaTransport.port),
                        CONNECTION_TIMEOUT_MS,
                    )
                }

            writer = OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))
            isConnected = true

            Logger.i { "WiFi OTA: Connected successfully" }
        }
            .onFailure { e ->
                Logger.e(e) { "WiFi OTA: Connection failed" }
                close()
            }
    }

    override suspend fun startOta(
        sizeBytes: Long,
        sha256Hash: String,
        onHandshakeStatus: suspend (OtaHandshakeStatus) -> Unit,
    ): Result<Unit> = runCatching {
        val command = OtaCommand.StartOta(sizeBytes, sha256Hash)
        sendCommand(command)

        var handshakeComplete = false
        while (!handshakeComplete) {
            val response = readResponse(ERASING_TIMEOUT_MS)
            when (val parsed = OtaResponse.parse(response)) {
                is OtaResponse.Ok -> handshakeComplete = true
                is OtaResponse.Erasing -> {
                    Logger.i { "WiFi OTA: Device erasing flash..." }
                    onHandshakeStatus(OtaHandshakeStatus.Erasing)
                }

                is OtaResponse.Error -> {
                    if (parsed.message.contains("Hash Rejected", ignoreCase = true)) {
                        throw OtaProtocolException.HashRejected(sha256Hash)
                    }
                    throw OtaProtocolException.CommandFailed(command, parsed)
                }

                else -> {
                    Logger.w { "WiFi OTA: Unexpected handshake response: $response" }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun streamFirmware(
        data: ByteArray,
        chunkSize: Int,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConnected) {
                throw OtaProtocolException.TransferFailed("Not connected")
            }

            val totalBytes = data.size
            var sentBytes = 0
            val outputStream = socket!!.getOutputStream()

            while (sentBytes < totalBytes) {
                val remainingBytes = totalBytes - sentBytes
                val currentChunkSize = minOf(chunkSize, remainingBytes)
                val chunk = data.copyOfRange(sentBytes, sentBytes + currentChunkSize)

                // Write chunk directly to TCP stream
                outputStream.write(chunk)
                outputStream.flush()

                // In the updated protocol, the device may send ACKs over WiFi too.
                // We check for any available responses without blocking too long.
                if (reader?.ready() == true) {
                    val response = readResponse(ACK_TIMEOUT_MS)
                    val nextSentBytes = sentBytes + currentChunkSize
                    when (val parsed = OtaResponse.parse(response)) {
                        is OtaResponse.Ack -> {
                            // Normal chunk success
                        }

                        is OtaResponse.Ok -> {
                            // OK indicates completion (usually on last chunk)
                            if (nextSentBytes >= totalBytes) {
                                sentBytes = nextSentBytes
                                onProgress(1.0f)
                                return@runCatching Unit
                            }
                        }

                        is OtaResponse.Error -> {
                            throw OtaProtocolException.TransferFailed("Transfer failed: ${parsed.message}")
                        }

                        else -> {} // Ignore other responses during stream
                    }
                }

                sentBytes += currentChunkSize
                onProgress(sentBytes.toFloat() / totalBytes)

                // Small delay to avoid overwhelming the device
                delay(WRITE_DELAY_MS)
            }

            Logger.i { "WiFi OTA: Firmware streaming complete ($sentBytes bytes)" }

            // Wait for final verification response (loop until OK or Error)
            var finalHandshakeComplete = false
            while (!finalHandshakeComplete) {
                val finalResponse = readResponse(VERIFICATION_TIMEOUT_MS)
                when (val parsed = OtaResponse.parse(finalResponse)) {
                    is OtaResponse.Ok -> finalHandshakeComplete = true
                    is OtaResponse.Ack -> {} // Ignore late ACKs
                    is OtaResponse.Error -> {
                        if (parsed.message.contains("Hash Mismatch", ignoreCase = true)) {
                            throw OtaProtocolException.VerificationFailed("Firmware hash mismatch after transfer")
                        }
                        throw OtaProtocolException.TransferFailed("Verification failed: ${parsed.message}")
                    }

                    else ->
                        throw OtaProtocolException.TransferFailed("Expected OK after transfer, got: $finalResponse")
                }
            }
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            runCatching {
                writer?.close()
                reader?.close()
                socket?.close()
            }
            writer = null
            reader = null
            socket = null
            isConnected = false
        }
    }

    private suspend fun sendCommand(command: OtaCommand) = withContext(Dispatchers.IO) {
        val w = writer ?: throw OtaProtocolException.ConnectionFailed("Not connected")
        val commandStr = command.toString()
        Logger.d { "WiFi OTA: Sending command: ${commandStr.trim()}" }
        w.write(commandStr)
        w.flush()
    }

    private suspend fun readResponse(timeoutMs: Long = COMMAND_TIMEOUT_MS): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val r = reader ?: throw OtaProtocolException.ConnectionFailed("Not connected")
                val response = r.readLine() ?: throw OtaProtocolException.ConnectionFailed("Connection closed")
                Logger.d { "WiFi OTA: Received response: $response" }
                response
            }
        } catch (@Suppress("SwallowedException") e: SocketTimeoutException) {
            throw OtaProtocolException.Timeout("Timeout waiting for response after ${timeoutMs}ms")
        }
    }

    companion object {
        const val DEFAULT_PORT = 3232
        const val RECOMMENDED_CHUNK_SIZE = 1024 // Larger than BLE
        private const val RECEIVE_BUFFER_SIZE = 1024
        private const val DISCOVERY_TIMEOUT_DEFAULT = 3000L
        private const val BROADCAST_ADDRESS = "255.255.255.255"

        // Timeouts
        private const val CONNECTION_TIMEOUT_MS = 5_000
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val COMMAND_TIMEOUT_MS = 10_000L
        private const val ERASING_TIMEOUT_MS = 60_000L
        private const val ACK_TIMEOUT_MS = 10_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L
        private const val WRITE_DELAY_MS = 10L // Shorter than BLE

        /**
         * Discover ESP32 devices on the local network via UDP broadcast.
         *
         * @return List of discovered device IP addresses
         */
        suspend fun discoverDevices(timeoutMs: Long = DISCOVERY_TIMEOUT_DEFAULT): List<String> =
            withContext(Dispatchers.IO) {
                val devices = mutableListOf<String>()

                runCatching {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        socket.soTimeout = timeoutMs.toInt()

                        // Send discovery broadcast
                        val discoveryMessage = "MESHTASTIC_OTA_DISCOVERY\n".toByteArray()
                        val broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS)
                        val packet =
                            DatagramPacket(discoveryMessage, discoveryMessage.size, broadcastAddress, DEFAULT_PORT)
                        socket.send(packet)
                        Logger.d { "WiFi OTA: Sent discovery broadcast" }

                        // Listen for responses
                        val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
                        val startTime = nowMillis

                        while (nowMillis - startTime < timeoutMs) {
                            try {
                                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                                socket.receive(receivePacket)

                                val response = String(receivePacket.data, 0, receivePacket.length).trim()
                                if (response.startsWith("MESHTASTIC_OTA")) {
                                    val deviceIp = receivePacket.address.hostAddress
                                    if (deviceIp != null && !devices.contains(deviceIp)) {
                                        devices.add(deviceIp)
                                        Logger.i { "WiFi OTA: Discovered device at $deviceIp" }
                                    }
                                }
                            } catch (@Suppress("SwallowedException") e: SocketTimeoutException) {
                                break
                            }
                        }
                    }
                }
                    .onFailure { e -> Logger.e(e) { "WiFi OTA: Discovery failed" } }

                devices
            }
    }
}
