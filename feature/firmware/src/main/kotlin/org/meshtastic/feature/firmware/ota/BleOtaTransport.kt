/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BLE transport implementation for ESP32 Unified OTA protocol.
 *
 * Service UUID: 4FAFC201-1FB5-459E-8FCC-C5C9C331914B
 * - OTA Characteristic (Write): 62ec0272-3ec5-11eb-b378-0242ac130005
 * - TX Characteristic (Notify): 62ec0272-3ec5-11eb-b378-0242ac130003
 */
class BleOtaTransport(private val context: Context, private val device: BluetoothDevice) : UnifiedOtaProtocol {

    private var gatt: BluetoothGatt? = null
    private var otaCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private var isConnected = false

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Logger.i { "BLE OTA: Connected to ${device.address}" }
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Logger.i { "BLE OTA: Disconnected from ${device.address}" }
                        isConnected = false
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        otaCharacteristic = service.getCharacteristic(OTA_CHARACTERISTIC_UUID)
                        txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID)

                        if (otaCharacteristic != null && txCharacteristic != null) {
                            // Enable notifications on TX characteristic
                            txCharacteristic?.let { enableNotifications(gatt, it) }
                            isConnected = true
                            Logger.i { "BLE OTA: Service discovered and ready" }
                        } else {
                            Logger.e { "BLE OTA: Required characteristics not found" }
                        }
                    } else {
                        Logger.e { "BLE OTA: Service not found" }
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == TX_CHARACTERISTIC_UUID) {
                    val response = value.decodeToString()
                    Logger.d { "BLE OTA: Received response: $response" }
                    responseChannel.trySend(response)
                }
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                onCharacteristicChanged(gatt, characteristic, characteristic.value)
            }
        }

    /** Connect to the device and discover OTA service. */
    suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            gatt =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(context, false, gattCallback)
                }

            continuation.invokeOnCancellation {
                gatt?.disconnect()
                gatt?.close()
            }

            // Wait for connection and service discovery
            continuation.resume(Result.success(Unit))
        } catch (e: Exception) {
            continuation.resumeWithException(
                OtaProtocolException.ConnectionFailed("Failed to connect to BLE device", e),
            )
        }
    }

    override suspend fun sendVersion(): Result<OtaResponse.Ok> = runCatching {
        sendCommand(OtaCommand.Version)
        val response = waitForResponse(COMMAND_TIMEOUT_MS)
        when (val parsed = OtaResponse.parse(response)) {
            is OtaResponse.Ok -> parsed
            is OtaResponse.Error -> throw OtaProtocolException.CommandFailed(OtaCommand.Version, parsed)
            else ->
                throw OtaProtocolException.CommandFailed(
                    OtaCommand.Version,
                    OtaResponse.Error("Unexpected response: $parsed"),
                )
        }
    }

    override suspend fun startOta(sizeBytes: Long, sha256Hash: String): Result<Unit> = runCatching {
        val command = OtaCommand.StartOta(sizeBytes, sha256Hash)
        sendCommand(command)

        // Wait for ERASING response
        val erasingResponse = waitForResponse(ERASING_TIMEOUT_MS)
        when (OtaResponse.parse(erasingResponse)) {
            is OtaResponse.Erasing -> Logger.i { "BLE OTA: Device erasing flash..." }
            is OtaResponse.Error -> {
                val error = OtaResponse.parse(erasingResponse) as OtaResponse.Error
                if (error.message.contains("Hash Rejected", ignoreCase = true)) {
                    throw OtaProtocolException.HashRejected(sha256Hash)
                }
                throw OtaProtocolException.CommandFailed(command, error)
            }
            else -> {} // OK or other response, continue
        }

        // Wait for OK response after erasing
        val okResponse = waitForResponse(ERASING_TIMEOUT_MS)
        when (val parsed = OtaResponse.parse(okResponse)) {
            is OtaResponse.Ok -> Unit
            is OtaResponse.Error -> throw OtaProtocolException.CommandFailed(command, parsed)
            else -> throw OtaProtocolException.CommandFailed(command, OtaResponse.Error("Expected OK, got: $parsed"))
        }
    }

    override suspend fun streamFirmware(data: ByteArray, chunkSize: Int, onProgress: (Float) -> Unit): Result<Unit> =
        runCatching {
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
                writeData(chunk)

                // Wait for ACK (BLE only)
                val ackResponse = waitForResponse(ACK_TIMEOUT_MS)
                when (OtaResponse.parse(ackResponse)) {
                    is OtaResponse.Ack -> {} // Continue
                    is OtaResponse.Error -> {
                        val error = OtaResponse.parse(ackResponse) as OtaResponse.Error
                        throw OtaProtocolException.TransferFailed("Transfer failed: ${error.message}")
                    }
                    else -> throw OtaProtocolException.TransferFailed("Expected ACK, got: $ackResponse")
                }

                sentBytes += currentChunkSize
                onProgress(sentBytes.toFloat() / totalBytes)
            }

            // Wait for final verification
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

    override suspend fun reboot(): Result<Unit> = runCatching {
        sendCommand(OtaCommand.Reboot)
        val response = waitForResponse(COMMAND_TIMEOUT_MS)
        when (val parsed = OtaResponse.parse(response)) {
            is OtaResponse.Ok -> Unit
            is OtaResponse.Error -> throw OtaProtocolException.CommandFailed(OtaCommand.Reboot, parsed)
            else ->
                throw OtaProtocolException.CommandFailed(
                    OtaCommand.Reboot,
                    OtaResponse.Error("Unexpected response: $parsed"),
                )
        }
    }

    override suspend fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        responseChannel.close()
    }

    private suspend fun sendCommand(command: OtaCommand) {
        val data = command.toString().toByteArray()
        writeData(data)
    }

    private suspend fun writeData(data: ByteArray) {
        val characteristic =
            otaCharacteristic ?: throw OtaProtocolException.ConnectionFailed("OTA characteristic not available")

        suspendCancellableCoroutine { continuation ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    @Suppress("DEPRECATION")
                    gatt?.writeCharacteristic(characteristic)
                }
                continuation.resume(Unit)
            } catch (e: Exception) {
                continuation.resumeWithException(OtaProtocolException.TransferFailed("Failed to write data", e))
            }
        }

        // Small delay to avoid overwhelming the device
        delay(WRITE_DELAY_MS)
    }

    private suspend fun waitForResponse(timeoutMs: Long): String = try {
        withTimeout(timeoutMs) { responseChannel.receive() }
    } catch (e: CancellationException) {
        throw OtaProtocolException.Timeout("Timeout waiting for response after ${timeoutMs}ms")
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    companion object {
        // Service and Characteristic UUIDs from ESP32 Unified OTA spec
        private val SERVICE_UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
        private val OTA_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
        private val TX_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Timeouts
        private const val COMMAND_TIMEOUT_MS = 5_000L
        private const val ERASING_TIMEOUT_MS = 30_000L // Flash erase can take a while
        private const val ACK_TIMEOUT_MS = 2_000L
        private const val VERIFICATION_TIMEOUT_MS = 10_000L
        private const val WRITE_DELAY_MS = 50L

        // Recommended chunk size for BLE
        const val RECOMMENDED_CHUNK_SIZE = 512
    }
}
