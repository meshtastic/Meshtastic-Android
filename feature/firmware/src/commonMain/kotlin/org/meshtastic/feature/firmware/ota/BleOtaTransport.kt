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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_NOTIFY_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_WRITE_CHARACTERISTIC
import org.meshtastic.core.common.util.safeCatching
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** BLE transport implementation for ESP32 Unified OTA protocol using Kable. */
class BleOtaTransport(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    private val address: String,
    dispatcher: CoroutineDispatcher,
) : UnifiedOtaProtocol {

    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(transportScope, "BLE OTA")

    private val otaChar = BleCharacteristic(OTA_WRITE_CHARACTERISTIC)
    private val txChar = BleCharacteristic(OTA_NOTIFY_CHARACTERISTIC)

    private val responseChannel = Channel<String>(Channel.UNLIMITED)

    private var isConnected = false

    /** Scan for the device by MAC address (or MAC+1 for OTA mode) with retries. */
    private suspend fun scanForOtaDevice(): BleDevice? {
        val otaAddress = calculateMacPlusOne(address)
        val targetAddresses = setOf(address, otaAddress)
        Logger.i { "BLE OTA: Will match addresses: $targetAddresses" }

        return scanForBleDevice(
            scanner = scanner,
            tag = "BLE OTA",
            serviceUuid = OTA_SERVICE_UUID,
            retryCount = SCAN_RETRY_COUNT,
            retryDelay = SCAN_RETRY_DELAY,
        ) {
            it.address in targetAddresses
        }
    }

    @Suppress("MagicNumber")
    override suspend fun connect(): Result<Unit> = safeCatching {
        Logger.i { "BLE OTA: Waiting $REBOOT_DELAY for device to reboot into OTA mode..." }
        delay(REBOOT_DELAY)

        Logger.i { "BLE OTA: Connecting to $address using Kable..." }

        val device =
            scanForOtaDevice()
                ?: throw OtaProtocolException.ConnectionFailed(
                    "Device not found at address $address. " +
                        "Ensure the device has rebooted into OTA mode and is advertising.",
                )

        bleConnection.connectionState
            .onEach { state ->
                Logger.d { "BLE OTA: Connection state changed to $state" }
                isConnected = state is BleConnectionState.Connected
            }
            .launchIn(transportScope)

        try {
            val finalState = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT)
            if (finalState is BleConnectionState.Disconnected) {
                Logger.w { "BLE OTA: Failed to connect to ${device.address} (state=$finalState)" }
                throw OtaProtocolException.ConnectionFailed("Failed to connect to device at address ${device.address}")
            }
        } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.w { "BLE OTA: Timed out waiting to connect to ${device.address}. Error: ${e.message}" }
            throw OtaProtocolException.Timeout("Timed out connecting to device at address ${device.address}")
        }

        Logger.i { "BLE OTA: Connected to ${device.address}, discovering services..." }

        bleConnection.profile(OTA_SERVICE_UUID) { service ->
            // Log negotiated MTU for diagnostics
            val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
            Logger.i { "BLE OTA: Service ready. Max write value length: $maxLen bytes" }

            // Enable notifications and collect responses
            val subscribed = CompletableDeferred<Unit>()
            service
                .observe(txChar)
                .onEach { notifyBytes ->
                    try {
                        if (!subscribed.isCompleted) {
                            Logger.d { "BLE OTA: TX characteristic subscribed" }
                            subscribed.complete(Unit)
                        }
                        val response = notifyBytes.decodeToString()
                        Logger.d { "BLE OTA: Received response: $response" }
                        responseChannel.trySend(response)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Logger.e(e) { "BLE OTA: Failed to decode response bytes" }
                    }
                }
                .catch { e ->
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    Logger.e(e) { "BLE OTA: Error in TX characteristic subscription" }
                }
                .launchIn(this)

            // Allow time for the BLE subscription to be established before proceeding.
            delay(SUBSCRIPTION_SETTLE)
            if (!subscribed.isCompleted) subscribed.complete(Unit)

            subscribed.await()
            Logger.i { "BLE OTA: Service discovered and ready" }
        }
    }

    override suspend fun startOta(
        sizeBytes: Long,
        sha256Hash: String,
        onHandshakeStatus: suspend (OtaHandshakeStatus) -> Unit,
    ): Result<Unit> = safeCatching {
        val command = OtaCommand.StartOta(sizeBytes, sha256Hash)
        val packetsSent = sendCommand(command)

        var handshakeComplete = false
        var responsesReceived = 0
        while (!handshakeComplete) {
            val response = waitForResponse(ERASING_TIMEOUT)
            responsesReceived++
            when (val parsed = OtaResponse.parse(response)) {
                is OtaResponse.Ok -> {
                    if (responsesReceived >= packetsSent) {
                        handshakeComplete = true
                    }
                }
                is OtaResponse.Erasing -> {
                    Logger.i { "BLE OTA: Device erasing flash..." }
                    onHandshakeStatus(OtaHandshakeStatus.Erasing)
                }
                is OtaResponse.Error -> {
                    if (parsed.message.contains("Hash Rejected", ignoreCase = true)) {
                        throw OtaProtocolException.HashRejected(sha256Hash)
                    }
                    throw OtaProtocolException.CommandFailed(command, parsed)
                }
                else -> {
                    Logger.w { "BLE OTA: Unexpected handshake response: $response" }
                }
            }
        }
    }

    @Suppress("MagicNumber")
    override suspend fun streamFirmware(
        data: ByteArray,
        chunkSize: Int,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = safeCatching {
        val totalBytes = data.size
        var sentBytes = 0

        while (sentBytes < totalBytes) {
            if (!isConnected) {
                throw OtaProtocolException.TransferFailed("Connection lost during transfer")
            }

            val remainingBytes = totalBytes - sentBytes
            val currentChunkSize = minOf(chunkSize, remainingBytes)
            val chunk = data.copyOfRange(sentBytes, sentBytes + currentChunkSize)

            val packetsSentForChunk = writeData(chunk, BleWriteType.WITHOUT_RESPONSE)

            val nextSentBytes = sentBytes + currentChunkSize
            repeat(packetsSentForChunk) { i ->
                val response = waitForResponse(ACK_TIMEOUT)
                val isLastPacketOfChunk = i == packetsSentForChunk - 1

                when (val parsed = OtaResponse.parse(response)) {
                    is OtaResponse.Ack -> {}
                    is OtaResponse.Ok -> {
                        if (nextSentBytes >= totalBytes && isLastPacketOfChunk) {
                            sentBytes = nextSentBytes
                            onProgress(1.0f)
                            return@safeCatching Unit
                        }
                    }
                    is OtaResponse.Error -> {
                        if (parsed.message.contains("Hash Mismatch", ignoreCase = true)) {
                            throw OtaProtocolException.VerificationFailed("Firmware hash mismatch after transfer")
                        }
                        throw OtaProtocolException.TransferFailed("Transfer failed: ${parsed.message}")
                    }
                    else -> throw OtaProtocolException.TransferFailed("Unexpected response: $response")
                }
            }

            sentBytes = nextSentBytes
            onProgress(sentBytes.toFloat() / totalBytes)
        }

        val finalResponse = waitForResponse(VERIFICATION_TIMEOUT)
        when (val parsed = OtaResponse.parse(finalResponse)) {
            is OtaResponse.Ok -> Unit
            is OtaResponse.Error -> {
                if (parsed.message.contains("Hash Mismatch", ignoreCase = true)) {
                    throw OtaProtocolException.VerificationFailed("Firmware hash mismatch after transfer")
                }
                throw OtaProtocolException.TransferFailed("Verification failed: ${parsed.message}")
            }
            else -> throw OtaProtocolException.TransferFailed("Expected OK after transfer, got: $parsed")
        }
    }

    override suspend fun close() {
        bleConnection.disconnect()
        isConnected = false
        transportScope.cancel()
    }

    private suspend fun sendCommand(command: OtaCommand): Int {
        val data = command.toString().encodeToByteArray()
        return writeData(data, BleWriteType.WITH_RESPONSE)
    }

    private suspend fun writeData(data: ByteArray, writeType: BleWriteType): Int {
        val maxLen = bleConnection.maximumWriteValueLength(writeType) ?: data.size
        var offset = 0
        var packetsSent = 0

        try {
            while (offset < data.size) {
                val chunkSize = minOf(data.size - offset, maxLen)
                val packet = data.copyOfRange(offset, offset + chunkSize)

                bleConnection.profile(OTA_SERVICE_UUID) { service -> service.write(otaChar, packet, writeType) }

                offset += chunkSize
                packetsSent++
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw OtaProtocolException.TransferFailed("Failed to write data at offset $offset", e)
        }
        return packetsSent
    }

    private suspend fun waitForResponse(timeout: Duration): String = try {
        withTimeout(timeout) { responseChannel.receive() }
    } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
        throw OtaProtocolException.Timeout("Timeout waiting for response after $timeout")
    }

    companion object {
        private val CONNECTION_TIMEOUT = 15.seconds
        private val SUBSCRIPTION_SETTLE = 500.milliseconds
        private val ERASING_TIMEOUT = 60.seconds
        private val ACK_TIMEOUT = 10.seconds
        private val VERIFICATION_TIMEOUT = 10.seconds
        private val REBOOT_DELAY = 5.seconds
        private const val SCAN_RETRY_COUNT = 3
        private val SCAN_RETRY_DELAY = 2.seconds
        const val RECOMMENDED_CHUNK_SIZE = 512
    }
}
