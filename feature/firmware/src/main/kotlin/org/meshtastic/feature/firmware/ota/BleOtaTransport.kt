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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import java.util.UUID
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
class BleOtaTransport(private val centralManager: CentralManager, private val address: String) : UnifiedOtaProtocol {

    private val transportScope = CoroutineScope(SupervisorJob())
    private var peripheral: Peripheral? = null
    private var otaCharacteristic: RemoteCharacteristic? = null

    private val responseFlow =
        MutableSharedFlow<String>(
            extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var isConnected = false

    /** Connect to the device and discover OTA service. */
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun connect(): Result<Unit> = runCatching {
        Logger.i { "BLE OTA: Connecting to $address using Nordic BLE Library..." }

        val p =
            centralManager.getBondedPeripherals().firstOrNull { it.address == address }
                ?: throw OtaProtocolException.ConnectionFailed("Device not found at address $address")

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

        // Wait for connection
        p.state.first { it is ConnectionState.Connected }

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
                val response = notifyBytes.decodeToString()
                Logger.d { "BLE OTA: Received response: $response" }
                responseFlow.emit(response)
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
        withTimeout(timeoutMs) { responseFlow.first() }
    } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
        throw OtaProtocolException.Timeout("Timeout waiting for response after ${timeoutMs}ms")
    }

    companion object {
        // Service and Characteristic UUIDs from ESP32 Unified OTA spec
        private val SERVICE_UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
        private val OTA_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
        private val TX_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")

        // Timeouts
        private const val ERASING_TIMEOUT_MS = 30_000L // Flash erase can take a while
        private const val ACK_TIMEOUT_MS = 5_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L

        // Recommended chunk size for BLE
        const val RECOMMENDED_CHUNK_SIZE = 512
        private const val EXTRA_BUFFER_CAPACITY = 10
    }
}
