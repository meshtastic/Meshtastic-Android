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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import org.meshtastic.core.ble.AndroidBleService
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_NOTIFY_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_WRITE_CHARACTERISTIC
import kotlin.time.Duration.Companion.seconds

/**
 * BLE transport implementation for ESP32 Unified OTA protocol.
 *
 * Service UUID: 4FAFC201-1FB5-459E-8FCC-C5C9C331914B
 * - OTA Characteristic (Write): 62ec0272-3ec5-11eb-b378-0242ac130005
 * - TX Characteristic (Notify): 62ec0272-3ec5-11eb-b378-0242ac130003
 */
class BleOtaTransport(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    private val address: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UnifiedOtaProtocol {

    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(transportScope, "BLE OTA")
    private var otaCharacteristic: RemoteCharacteristic? = null

    private val responseChannel = Channel<String>(Channel.UNLIMITED)

    private var isConnected = false

    /** Scan for the device by MAC address with retries. After reboot, the device needs time to come up in OTA mode. */
    private suspend fun scanForOtaDevice(): BleDevice? {
        // ESP32 OTA bootloader may use MAC address with last byte incremented by 1
        val otaAddress = calculateOtaAddress(macAddress = address)
        val targetAddresses = setOf(address, otaAddress)
        Logger.i { "BLE OTA: Will match addresses: $targetAddresses" }

        repeat(SCAN_RETRY_COUNT) { attempt ->
            Logger.i { "BLE OTA: Scanning for device (attempt ${attempt + 1}/$SCAN_RETRY_COUNT)..." }

            val foundDevices = mutableSetOf<String>()
            val device =
                scanner
                    .scan(SCAN_TIMEOUT)
                    .onEach { d ->
                        if (foundDevices.add(d.address)) {
                            Logger.d { "BLE OTA: Scan found device: ${d.address} (name=${d.name})" }
                        }
                    }
                    .firstOrNull { it.address in targetAddresses }

            if (device != null) {
                Logger.i { "BLE OTA: Found target device at ${device.address}" }
                return device
            }

            Logger.w { "BLE OTA: Target addresses $targetAddresses not in ${foundDevices.size} devices found" }

            if (attempt < SCAN_RETRY_COUNT - 1) {
                Logger.i { "BLE OTA: Device not found, waiting ${SCAN_RETRY_DELAY_MS}ms before retry..." }
                delay(SCAN_RETRY_DELAY_MS)
            }
        }
        return null
    }

    /**
     * Calculate the potential OTA MAC address by incrementing the last byte. Some ESP32 bootloaders use MAC+1 for OTA
     * mode to distinguish from normal operation.
     */
    @Suppress("MagicNumber", "ReturnCount")
    private fun calculateOtaAddress(macAddress: String): String {
        val parts = macAddress.split(":")
        if (parts.size != 6) return macAddress

        val lastByte = parts[5].toIntOrNull(16) ?: return macAddress
        val incrementedByte = ((lastByte + 1) and 0xFF).toString(16).uppercase().padStart(2, '0')
        return parts.take(5).joinToString(":") + ":" + incrementedByte
    }

    /** Connect to the device and discover OTA service. */
    @Suppress("LongMethod")
    override suspend fun connect(): Result<Unit> = runCatching {
        Logger.i { "BLE OTA: Waiting ${REBOOT_DELAY_MS}ms for device to reboot into OTA mode..." }
        delay(REBOOT_DELAY_MS)

        Logger.i { "BLE OTA: Connecting to $address using Nordic BLE Library..." }

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
            val finalState = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)
            if (finalState is BleConnectionState.Disconnected) {
                Logger.w { "BLE OTA: Failed to connect to ${device.address} (state=$finalState)" }
                throw OtaProtocolException.ConnectionFailed("Failed to connect to device at address ${device.address}")
            }
        } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.w { "BLE OTA: Timed out waiting to connect to ${device.address}. Error: ${e.message}" }
            throw OtaProtocolException.Timeout("Timed out connecting to device at address ${device.address}")
        }

        Logger.i { "BLE OTA: Connected to ${device.address}, discovering services..." }

        // Discover services using our unified profile helper
        bleConnection.profile(OTA_SERVICE_UUID) { service ->
            val androidService = (service as AndroidBleService).service
            val ota =
                requireNotNull(androidService.characteristics.firstOrNull { it.uuid == OTA_WRITE_CHARACTERISTIC }) {
                    "OTA characteristic not found"
                }
            val txChar =
                requireNotNull(androidService.characteristics.firstOrNull { it.uuid == OTA_NOTIFY_CHARACTERISTIC }) {
                    "TX characteristic not found"
                }

            otaCharacteristic = ota

            // Log negotiated MTU for diagnostics
            val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
            Logger.i { "BLE OTA: Service ready. Max write value length: $maxLen bytes" }

            // Enable notifications and collect responses
            val subscribed = CompletableDeferred<Unit>()
            txChar
                .subscribe {
                    Logger.d { "BLE OTA: TX characteristic subscribed" }
                    subscribed.complete(Unit)
                }
                .onEach { notifyBytes ->
                    try {
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

            subscribed.await()
            Logger.i { "BLE OTA: Service discovered and ready" }
        }
    }

    /** Initiates the OTA update by sending the size and hash. */
    override suspend fun startOta(
        sizeBytes: Long,
        sha256Hash: String,
        onHandshakeStatus: suspend (OtaHandshakeStatus) -> Unit,
    ): Result<Unit> = runCatching {
        val command = OtaCommand.StartOta(sizeBytes, sha256Hash)
        val packetsSent = sendCommand(command)

        var handshakeComplete = false
        var responsesReceived = 0
        while (!handshakeComplete) {
            val response = waitForResponse(ERASING_TIMEOUT_MS)
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

    /** Streams the firmware data in chunks. */
    override suspend fun streamFirmware(
        data: ByteArray,
        chunkSize: Int,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = runCatching {
        val totalBytes = data.size
        var sentBytes = 0

        while (sentBytes < totalBytes) {
            if (!isConnected) {
                throw OtaProtocolException.TransferFailed("Connection lost during transfer")
            }

            val remainingBytes = totalBytes - sentBytes
            val currentChunkSize = minOf(chunkSize, remainingBytes)
            val chunk = data.copyOfRange(sentBytes, sentBytes + currentChunkSize)

            // Write chunk
            val packetsSentForChunk = writeData(chunk, BleWriteType.WITHOUT_RESPONSE)

            // Wait for responses
            val nextSentBytes = sentBytes + currentChunkSize
            repeat(packetsSentForChunk) { i ->
                val response = waitForResponse(ACK_TIMEOUT_MS)
                val isLastPacketOfChunk = i == packetsSentForChunk - 1

                when (val parsed = OtaResponse.parse(response)) {
                    is OtaResponse.Ack -> {
                        // Normal packet success
                    }

                    is OtaResponse.Ok -> {
                        if (nextSentBytes >= totalBytes && isLastPacketOfChunk) {
                            sentBytes = nextSentBytes
                            onProgress(1.0f)
                            return@runCatching Unit
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

        val finalResponse = waitForResponse(VERIFICATION_TIMEOUT_MS)
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
        val data = command.toString().toByteArray()
        return writeData(data, BleWriteType.WITH_RESPONSE)
    }

    private suspend fun writeData(data: ByteArray, writeType: BleWriteType): Int {
        val characteristic =
            otaCharacteristic ?: throw OtaProtocolException.ConnectionFailed("OTA characteristic not available")

        val maxLen = bleConnection.maximumWriteValueLength(writeType) ?: data.size
        var offset = 0
        var packetsSent = 0

        try {
            while (offset < data.size) {
                val chunkSize = minOf(data.size - offset, maxLen)
                val packet = data.copyOfRange(offset, offset + chunkSize)

                val nordicWriteType =
                    when (writeType) {
                        BleWriteType.WITH_RESPONSE -> no.nordicsemi.kotlin.ble.core.WriteType.WITH_RESPONSE
                        BleWriteType.WITHOUT_RESPONSE -> no.nordicsemi.kotlin.ble.core.WriteType.WITHOUT_RESPONSE
                    }

                characteristic.write(packet, writeType = nordicWriteType)
                offset += chunkSize
                packetsSent++
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw OtaProtocolException.TransferFailed("Failed to write data at offset $offset", e)
        }
        return packetsSent
    }

    private suspend fun waitForResponse(timeoutMs: Long): String = try {
        withTimeout(timeoutMs) { responseChannel.receive() }
    } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
        throw OtaProtocolException.Timeout("Timeout waiting for response after ${timeoutMs}ms")
    }

    companion object {
        // Timeouts and retries
        private val SCAN_TIMEOUT = 10.seconds
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val ERASING_TIMEOUT_MS = 60_000L
        private const val ACK_TIMEOUT_MS = 10_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L

        private const val REBOOT_DELAY_MS = 5_000L
        private const val SCAN_RETRY_COUNT = 3
        private const val SCAN_RETRY_DELAY_MS = 2_000L

        const val RECOMMENDED_CHUNK_SIZE = 512
    }
}
