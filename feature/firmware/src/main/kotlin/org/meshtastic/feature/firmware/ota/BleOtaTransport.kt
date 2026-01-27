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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.distinctByPeripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/**
 * BLE transport implementation for ESP32 Unified OTA protocol. Uses Nordic Kotlin-BLE-Library for modern coroutine
 * support.
 *
 * Service UUID: 4FAFC201-1FB5-459E-8FCC-C5C9C331914B
 * - OTA Characteristic (Write): 62ec0272-3ec5-11eb-b378-0242ac130005
 * - TX Characteristic (Notify): 62ec0272-3ec5-11eb-b378-0242ac130003
 */
class BleOtaTransport(
    private val centralManager: CentralManager,
    private val address: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UnifiedOtaProtocol {

    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var peripheral: Peripheral? = null
    private var otaCharacteristic: RemoteCharacteristic? = null

    private val responseChannel =
        kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    private var isConnected = false

    /**
     * Scan for the device by MAC address with retries. After reboot, the device needs time to come up in OTA mode.
     *
     * Note: We scan by address rather than service UUID because some ESP32 OTA bootloaders don't include the service
     * UUID in their advertisement data - the service is only discoverable after connecting. We verify the OTA service
     * exists after connection.
     *
     * ESP32 bootloaders may use the original MAC address OR increment the last byte by 1 for OTA mode, so we check both
     * addresses.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun scanForOtaDevice(): Peripheral? {
        // ESP32 OTA bootloader may use MAC address with last byte incremented by 1
        val otaAddress = calculateOtaAddress(address)
        val targetAddresses = setOf(address, otaAddress)
        Logger.i { "BLE OTA: Will match addresses: $targetAddresses" }

        repeat(SCAN_RETRY_COUNT) { attempt ->
            Logger.i { "BLE OTA: Scanning for device (attempt ${attempt + 1}/$SCAN_RETRY_COUNT)..." }

            // Scan without service UUID filter - ESP32 OTA bootloader may not advertise the UUID
            // Log all devices found during scan for debugging
            val foundDevices = mutableSetOf<String>()
            val peripheral =
                centralManager
                    .scan(SCAN_TIMEOUT)
                    .distinctByPeripheral()
                    .map { it.peripheral }
                    .onEach { p ->
                        if (foundDevices.add(p.address)) {
                            Logger.d { "BLE OTA: Scan found device: ${p.address} (name=${p.name})" }
                        }
                    }
                    .firstOrNull { it.address in targetAddresses }

            if (peripheral != null) {
                Logger.i { "BLE OTA: Found target device at ${peripheral.address}" }
                return peripheral
            }

            Logger.w { "BLE OTA: Target addresses $targetAddresses not in ${foundDevices.size} devices found" }

            if (attempt < SCAN_RETRY_COUNT - 1) {
                Logger.i { "BLE OTA: Device not found, waiting ${SCAN_RETRY_DELAY_MS}ms before retry..." }
                kotlinx.coroutines.delay(SCAN_RETRY_DELAY_MS)
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
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("LongMethod")
    override suspend fun connect(): Result<Unit> = runCatching {
        Logger.i { "BLE OTA: Waiting ${REBOOT_DELAY_MS}ms for device to reboot into OTA mode..." }
        kotlinx.coroutines.delay(REBOOT_DELAY_MS)

        Logger.i { "BLE OTA: Connecting to $address using Nordic BLE Library..." }

        // Scan for device by address - device must have rebooted into OTA mode
        val p =
            scanForOtaDevice()
                ?: throw OtaProtocolException.ConnectionFailed(
                    "Device not found at address $address. " +
                        "Ensure the device has rebooted into OTA mode and is advertising.",
                )

        peripheral = p

        centralManager.connect(
            peripheral = p,
            options = CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
        )
        p.requestConnectionPriority(ConnectionPriority.HIGH)

        // Monitor connection state
        p.state
            .onEach { state ->
                Logger.d { "BLE OTA: Connection state changed to $state" }
                if (state is ConnectionState.Disconnected) {
                    isConnected = false
                }
            }
            .launchIn(transportScope)

        // Wait for connection or failure with timeout
        // Don't use drop(1) - we might already be connected by the time we start collecting
        val connectionState =
            try {
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    p.state.first { it is ConnectionState.Connected || it is ConnectionState.Disconnected }
                }
            } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
                Logger.w { "BLE OTA: Timed out waiting to connect to ${p.address}. Error: ${e.message}" }
                throw OtaProtocolException.Timeout("Timed out connecting to device at address ${p.address}")
            }

        if (connectionState is ConnectionState.Disconnected) {
            Logger.w { "BLE OTA: Failed to connect to ${p.address} (state=$connectionState)" }
            throw OtaProtocolException.ConnectionFailed("Failed to connect to device at address ${p.address}")
        }

        Logger.i { "BLE OTA: Connected to ${p.address}, discovering services..." }

        // Discover services
        val services = p.services(listOf(SERVICE_UUID.toKotlinUuid())).filterNotNull().first()
        val meshtasticOtaService =
            services.find { it.uuid == SERVICE_UUID.toKotlinUuid() }
                ?: throw OtaProtocolException.ConnectionFailed("ESP32 OTA service not found")

        otaCharacteristic =
            meshtasticOtaService.characteristics.find { it.uuid == OTA_CHARACTERISTIC_UUID.toKotlinUuid() }
        val txChar = meshtasticOtaService.characteristics.find { it.uuid == TX_CHARACTERISTIC_UUID.toKotlinUuid() }

        if (otaCharacteristic == null || txChar == null) {
            throw OtaProtocolException.ConnectionFailed("Required characteristics not found")
        }

        // Enable notifications and collect responses
        txChar
            .subscribe()
            .onEach { notifyBytes ->
                try {
                    val response = notifyBytes.decodeToString()
                    Logger.d { "BLE OTA: Received response: $response" }
                    responseChannel.trySend(response)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "BLE OTA: Failed to decode response bytes" }
                }
            }
            .launchIn(transportScope)

        isConnected = true
        Logger.i { "BLE OTA: Service discovered and ready" }
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
            val response = waitForResponse(ERASING_TIMEOUT_MS)
            when (val parsed = OtaResponse.parse(response)) {
                is OtaResponse.Ok -> handshakeComplete = true
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
            writeData(chunk, WriteType.WITHOUT_RESPONSE)

            // Wait for response (ACK or OK for last chunk)
            val response = waitForResponse(ACK_TIMEOUT_MS)
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
                    } else {
                        throw OtaProtocolException.TransferFailed("Premature OK received at offset $nextSentBytes")
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

            sentBytes = nextSentBytes
            onProgress(sentBytes.toFloat() / totalBytes)
        }

        // If we finished the loop without receiving OK, wait for it now
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
        peripheral?.disconnect()
        peripheral = null
        isConnected = false
        transportScope.cancel()
    }

    private suspend fun sendCommand(command: OtaCommand) {
        val data = command.toString().toByteArray()
        writeData(data, WriteType.WITH_RESPONSE)
    }

    private suspend fun writeData(data: ByteArray, writeType: WriteType) {
        val characteristic =
            otaCharacteristic ?: throw OtaProtocolException.ConnectionFailed("OTA characteristic not available")

        try {
            characteristic.write(data, writeType = writeType)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw OtaProtocolException.TransferFailed("Failed to write data", e)
        }
    }

    private suspend fun waitForResponse(timeoutMs: Long): String = try {
        withTimeout(timeoutMs) { responseChannel.receive() }
    } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
        throw OtaProtocolException.Timeout("Timeout waiting for response after ${timeoutMs}ms")
    }

    companion object {
        // Service and Characteristic UUIDs from ESP32 Unified OTA spec
        private val SERVICE_UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
        private val OTA_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
        private val TX_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")

        // Timeouts and retries
        private val SCAN_TIMEOUT = 10.seconds
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val ERASING_TIMEOUT_MS = 60_000L // Flash erase can take a while
        private const val ACK_TIMEOUT_MS = 10_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L

        // Reboot and scan retry configuration
        // Device needs time to reboot into OTA mode after receiving the reboot command
        private const val REBOOT_DELAY_MS = 5_000L
        private const val SCAN_RETRY_COUNT = 3
        private const val SCAN_RETRY_DELAY_MS = 2_000L

        // Recommended chunk size for BLE
        const val RECOMMENDED_CHUNK_SIZE = 512
    }
}
