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
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching

/**
 * WiFi/TCP transport implementation for ESP32 Unified OTA protocol.
 *
 * Uses Ktor raw sockets for KMP-compatible TCP communication. UDP discovery is not included in this common
 * implementation and should be handled by platform-specific code.
 *
 * Unlike BLE, WiFi transport:
 * - Uses synchronous TCP (no manual ACK waiting)
 * - Supports larger chunk sizes (up to 1024 bytes)
 * - Generally faster transfer speeds
 */
class WifiOtaTransport(private val deviceIpAddress: String, private val port: Int = DEFAULT_PORT) : UnifiedOtaProtocol {

    private var selectorManager: SelectorManager? = null
    private var socket: Socket? = null
    private var writeChannel: ByteWriteChannel? = null
    private var readChannel: ByteReadChannel? = null
    private var isConnected = false

    /** Connect to the device via TCP using Ktor raw sockets. */
    override suspend fun connect(): Result<Unit> = withContext(ioDispatcher) {
        safeCatching {
            Logger.i { "WiFi OTA: Connecting to $deviceIpAddress:$port" }

            val selector = SelectorManager(ioDispatcher)
            selectorManager = selector

            val tcpSocket =
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    aSocket(selector).tcp().connect(InetSocketAddress(deviceIpAddress, port))
                }
            socket = tcpSocket

            writeChannel = tcpSocket.openWriteChannel(autoFlush = false)
            readChannel = tcpSocket.openReadChannel()
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
    ): Result<Unit> = safeCatching {
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

    @Suppress("CyclomaticComplexity")
    override suspend fun streamFirmware(
        data: ByteArray,
        chunkSize: Int,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = withContext(ioDispatcher) {
        safeCatching {
            if (!isConnected) {
                throw OtaProtocolException.TransferFailed("Not connected")
            }

            val wc = writeChannel ?: throw OtaProtocolException.TransferFailed("Not connected")
            val totalBytes = data.size
            var sentBytes = 0

            while (sentBytes < totalBytes) {
                val remainingBytes = totalBytes - sentBytes
                val currentChunkSize = minOf(chunkSize, remainingBytes)

                // Write chunk directly to TCP stream — no per-chunk ACK needed over TCP.
                // Ktor writeFully uses (startIndex, endIndex), NOT (offset, length).
                wc.writeFully(data, sentBytes, sentBytes + currentChunkSize)
                wc.flush()

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
        withContext(ioDispatcher) {
            safeCatching {
                socket?.close()
                selectorManager?.close()
            }
            writeChannel = null
            readChannel = null
            socket = null
            selectorManager = null
            isConnected = false
        }
    }

    private suspend fun sendCommand(command: OtaCommand) = withContext(ioDispatcher) {
        val wc = writeChannel ?: throw OtaProtocolException.ConnectionFailed("Not connected")
        val commandStr = command.toString()
        Logger.d { "WiFi OTA: Sending command: ${commandStr.trim()}" }
        wc.writeStringUtf8(commandStr)
        wc.flush()
    }

    private suspend fun readResponse(timeoutMs: Long = COMMAND_TIMEOUT_MS): String = withTimeout(timeoutMs) {
        val rc = readChannel ?: throw OtaProtocolException.ConnectionFailed("Not connected")
        val response = rc.readLine() ?: throw OtaProtocolException.ConnectionFailed("Connection closed")
        Logger.d { "WiFi OTA: Received response: $response" }
        response
    }

    companion object {
        const val DEFAULT_PORT = 3232
        const val RECOMMENDED_CHUNK_SIZE = 1024 // Larger than BLE

        // Timeouts
        private const val CONNECTION_TIMEOUT_MS = 5_000L
        private const val COMMAND_TIMEOUT_MS = 10_000L
        private const val ERASING_TIMEOUT_MS = 60_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L
        private const val WRITE_DELAY_MS = 10L // Shorter than BLE
    }
}
