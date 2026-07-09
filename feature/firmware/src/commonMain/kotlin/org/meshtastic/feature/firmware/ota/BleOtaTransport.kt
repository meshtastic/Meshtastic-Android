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
package org.meshtastic.feature.firmware.ota

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_NOTIFY_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_WRITE_CHARACTERISTIC
import org.meshtastic.core.common.util.safeCatching
import kotlin.concurrent.Volatile
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

    @Volatile private var otaService: BleService? = null

    @Volatile private var responseChannel = Channel<String>(Channel.UNLIMITED)

    // Written from the connectionState collector (Dispatchers.Default) and read by the streaming loop's
    // connection-loss guard (Dispatchers.IO); @Volatile ensures the guard sees a mid-transfer disconnect.
    @Volatile private var isConnected = false

    @Volatile private var notificationJob: Job? = null

    @Volatile private var connectionStateJob: Job? = null

    /** Scan for the device by MAC address (or MAC+1 for OTA mode) with retries. */
    private suspend fun scanForOtaDevice(): BleDevice? {
        val otaAddress = calculateMacPlusOne(address)
        val targetAddresses = setOf(address, otaAddress)
        Logger.i { "BLE OTA: Will match target OTA device addresses" }

        return scanForBleDevice(scanner = scanner, tag = "BLE OTA", serviceUuid = OTA_SERVICE_UUID) {
            it.address in targetAddresses
        }
    }

    @Suppress("MagicNumber", "LongMethod")
    override suspend fun connect(): Result<Unit> = safeCatching {
        otaService = null
        notificationJob?.cancel()
        notificationJob = null
        connectionStateJob?.cancel()
        connectionStateJob = null
        isConnected = false
        responseChannel.close()
        val connectResponseChannel = Channel<String>(Channel.UNLIMITED)
        responseChannel = connectResponseChannel

        Logger.i { "BLE OTA: Waiting $REBOOT_DELAY for device to reboot into OTA mode..." }
        delay(REBOOT_DELAY)

        Logger.i { "BLE OTA: Connecting to OTA device using Kable..." }

        val device =
            scanForOtaDevice()
                ?: throw OtaProtocolException.ConnectionFailed(
                    "Device not found. Ensure the device has rebooted into OTA mode and is advertising.",
                )

        connectionStateJob =
            bleConnection.connectionState
                .onEach { state ->
                    Logger.d { "BLE OTA: Connection state changed to $state" }
                    isConnected = state is BleConnectionState.Connected
                }
                .launchIn(transportScope)

        try {
            val finalState = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT)
            if (finalState is BleConnectionState.Disconnected) {
                Logger.w { "BLE OTA: Failed to connect to OTA device (state=$finalState)" }
                throw OtaProtocolException.ConnectionFailed("Failed to connect to OTA device")
            }
        } catch (@Suppress("SwallowedException") e: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Logger.w { "BLE OTA: Timed out waiting to connect to OTA device. Error: ${e.message}" }
            throw OtaProtocolException.Timeout("Timed out connecting to OTA device")
        }

        val cacheInvalidated = bleConnection.invalidateServiceCache()
        Logger.d { "BLE OTA: GATT cache invalidation requested: $cacheInvalidated" }
        if (cacheInvalidated) {
            Logger.i { "BLE OTA: Invalidated stale GATT service cache before OTA discovery" }
        } else {
            Logger.d { "BLE OTA: GATT cache invalidation not available; proceeding with existing service cache" }
        }

        Logger.i { "BLE OTA: Connected to OTA device, discovering services..." }

        try {
            discoverAndPrepareOtaService(device, connectResponseChannel, cacheInvalidated)
        } catch (e: TimeoutCancellationException) {
            otaService = null
            currentCoroutineContext().ensureActive()
            Logger.w { "BLE OTA: Timed out waiting for OTA service discovery. Error: ${e.message}" }
            throw OtaProtocolException.Timeout("Timed out waiting for BLE OTA service discovery")
        } catch (e: OtaProtocolException) {
            otaService = null
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            otaService = null
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            otaService = null
            Logger.w(e) { "BLE OTA: Failed to prepare OTA service" }
            throw OtaProtocolException.ConnectionFailed("Failed to prepare BLE OTA service", e)
        }
    }

    @Suppress("LongMethod")
    private suspend fun discoverAndPrepareOtaService(
        device: BleDevice,
        connectResponseChannel: Channel<String>,
        cacheInvalidated: Boolean,
    ) {
        suspend fun prepareProfile() {
            bleConnection.profile(OTA_SERVICE_UUID) { service ->
                service.requireOtaCharacteristics()

                val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
                Logger.i { "BLE OTA: Service ready. Max write value length: $maxLen bytes" }

                val notificationsReady = CompletableDeferred<Unit>()
                notificationJob =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        service
                            .observe(txChar) {
                                Logger.d { "BLE OTA: TX characteristic subscribed" }
                                notificationsReady.complete(Unit)
                            }
                            .onEach { notifyBytes ->
                                try {
                                    val response = notifyBytes.decodeToString()
                                    Logger.d { "BLE OTA: Received response: $response" }
                                    connectResponseChannel.trySend(response)
                                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                                    Logger.e(e) { "BLE OTA: Failed to decode response bytes" }
                                }
                            }
                            .catch { e ->
                                Logger.e(e) { "BLE OTA: Error in TX characteristic notification flow" }
                                if (!notificationsReady.isCompleted) {
                                    notificationsReady.completeExceptionally(e)
                                }
                                connectResponseChannel.close(e)
                            }
                            .collect()
                    }

                val confirmed = withTimeoutOrNull(SUBSCRIPTION_SETTLE) { notificationsReady.await() } != null
                if (confirmed) {
                    Logger.i { "BLE OTA: TX notifications subscribed" }
                } else {
                    Logger.w {
                        "BLE OTA: TX notification subscription not confirmed after $SUBSCRIPTION_SETTLE; " +
                            "continuing with bounded settle fallback"
                    }
                }
                otaService = service
                Logger.i { "BLE OTA: Service discovered and ready" }
            }
        }

        try {
            prepareProfile()
        } catch (e: OtaProtocolException.ConnectionFailed) {
            if (!cacheInvalidated) throw e
            Logger.i {
                "BLE OTA: OTA characteristics missing after cache refresh; reconnecting to force service rediscovery"
            }
            notificationJob?.cancel()
            notificationJob = null
            otaService = null
            bleConnection.disconnect()
            delay(CACHE_REFRESH_RECONNECT_DELAY)
            val reconnectState = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT)
            if (reconnectState is BleConnectionState.Disconnected) {
                throw OtaProtocolException.ConnectionFailed("Failed to reconnect to OTA device after cache refresh")
            }
            Logger.i { "BLE OTA: Reconnected after cache refresh for OTA service rediscovery" }
            prepareProfile()
        }
    }

    override suspend fun startOta(
        sizeBytes: Long,
        sha256Hash: String,
        onHandshakeStatus: suspend (OtaHandshakeStatus) -> Unit,
    ): Result<Unit> = safeCatching {
        val command = OtaCommand.StartOta(sizeBytes, sha256Hash)
        sendCommand(command)

        // Drive on response *type*, never a fragment/response count: the handshake completes only on an explicit OK.
        // ERASING is an interim progress notification the device may emit before OK, so it just continues the wait. At
        // a low MTU the command splits into multiple writes; gating completion on a write count would desync against
        // the device's single OK — the same fragment-count bug PR #5915 removed from streamFirmware.
        var handshakeComplete = false
        while (!handshakeComplete) {
            when (val parsed = OtaResponse.parse(waitForResponse(ERASING_TIMEOUT))) {
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

                else -> Logger.w { "BLE OTA: Unexpected handshake response: $parsed" }
            }
        }
    }

    /**
     * Streams firmware to the device. The ESP32 OTA loader is an ACK-paced byte stream: it drains its receive buffer
     * and emits exactly ONE response per drain — an `ACK` while more data is expected, or the terminal `OK` once the
     * final byte is received and the image verified. To keep a deterministic 1-write → 1-response cadence at any MTU,
     * each chunk is sent as a single GATT write no larger than the negotiated write payload. A chunk that exceeded the
     * payload would fragment into multiple writes that the device coalesces into one response, desyncing our response
     * accounting (the cause of the prior ACK-timeout hang at MTU 512, where a 512-byte chunk split into [509, 3]).
     *
     * Because we write one chunk then await its single response before writing the next, only one chunk is ever
     * outstanding, so the device's byte-stream receive buffer holds exactly one chunk per drain — keeping the cadence a
     * deterministic 1 write → 1 response. Branch on response *type* (never on a fragment count): success requires an
     * explicit terminal `OK`, and any `ERR` fails the transfer, so a late device error can never be reported as
     * success.
     */
    @Suppress("MagicNumber")
    override suspend fun streamFirmware(
        data: ByteArray,
        chunkSize: Int,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = safeCatching {
        val totalBytes = data.size
        if (totalBytes == 0) {
            // Fail now: an empty image would otherwise skip the loop and then wait out the full
            // VERIFICATION_TIMEOUT before failing.
            throw OtaProtocolException.TransferFailed("Firmware is empty")
        }
        var sentBytes = 0

        val writePayload = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: SAFE_WRITE_PAYLOAD
        val effectiveChunkSize = minOf(chunkSize, writePayload).coerceAtLeast(1)

        while (sentBytes < totalBytes) {
            if (!isConnected) {
                throw OtaProtocolException.TransferFailed("Connection lost during transfer")
            }

            val currentChunkSize = minOf(effectiveChunkSize, totalBytes - sentBytes)
            val chunk = data.copyOfRange(sentBytes, sentBytes + currentChunkSize)
            val isLastChunk = sentBytes + currentChunkSize >= totalBytes

            val packetsSent = writeData(chunk, BleWriteType.WITHOUT_RESPONSE)
            if (packetsSent != 1) {
                throw OtaProtocolException.TransferFailed("Chunk produced $packetsSent writes, expected exactly one")
            }

            when (val parsed = OtaResponse.parse(waitForResponse(ACK_TIMEOUT))) {
                is OtaResponse.Ack -> Unit

                is OtaResponse.Ok ->
                    if (isLastChunk) {
                        sentBytes += currentChunkSize
                        onProgress(1.0f)
                        return@safeCatching Unit
                    } else {
                        // The device sends OK only once the full image is received; an OK before the final chunk
                        // means its size accounting disagrees with ours — fail closed rather than treat it as an ACK.
                        throw OtaProtocolException.TransferFailed("Received OK before the final chunk")
                    }

                is OtaResponse.Error -> throw transferException(parsed)

                else -> throw OtaProtocolException.TransferFailed("Unexpected response during transfer: $parsed")
            }

            sentBytes += currentChunkSize
            onProgress(sentBytes.toFloat() / totalBytes)
        }

        // Every chunk was acknowledged with ACK but the terminal OK has not arrived (e.g. the device expected more
        // bytes than we sent) — wait for the device's final verification result rather than reporting success.
        when (val parsed = OtaResponse.parse(waitForResponse(VERIFICATION_TIMEOUT))) {
            is OtaResponse.Ok -> Unit
            is OtaResponse.Error -> throw transferException(parsed)
            else -> throw OtaProtocolException.TransferFailed("Expected OK after transfer, got: $parsed")
        }
    }

    /**
     * Maps a device `ERR` response to the appropriate transfer exception (hash mismatch is verification, else generic).
     */
    private fun transferException(error: OtaResponse.Error): OtaProtocolException =
        if (error.message.contains("Hash Mismatch", ignoreCase = true)) {
            OtaProtocolException.VerificationFailed("Firmware hash mismatch after transfer")
        } else {
            OtaProtocolException.TransferFailed("Transfer failed: ${error.message}")
        }

    override suspend fun close() {
        notificationJob?.cancel()
        notificationJob = null
        connectionStateJob?.cancel()
        connectionStateJob = null
        otaService = null
        responseChannel.close()
        bleConnection.disconnect()
        isConnected = false
        transportScope.cancel()
    }

    private suspend fun sendCommand(command: OtaCommand): Int {
        val data = command.toString().encodeToByteArray()
        return writeData(data, BleWriteType.WITH_RESPONSE)
    }

    private suspend fun writeData(data: ByteArray, writeType: BleWriteType): Int {
        val service = otaService ?: throw OtaProtocolException.TransferFailed("BLE OTA service is not ready")
        // takeIf { it > 0 }: a non-positive negotiated length would stall the loop (offset never advances); fall
        // back to a single whole-buffer write instead of looping forever.
        val maxLen = bleConnection.maximumWriteValueLength(writeType)?.takeIf { it > 0 } ?: data.size
        var offset = 0
        var packetsSent = 0

        try {
            while (offset < data.size) {
                val chunkSize = minOf(data.size - offset, maxLen)
                val packet = data.copyOfRange(offset, offset + chunkSize)

                service.write(otaChar, packet, writeType)

                offset += chunkSize
                packetsSent++
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw OtaProtocolException.TransferFailed("Failed to write data at offset $offset", e)
        }
        return packetsSent
    }

    private suspend fun waitForResponse(timeout: Duration): String = responseChannel.receiveWithin(timeout) {
        OtaProtocolException.Timeout("Timeout waiting for response after $timeout")
    }

    private fun BleService.requireOtaCharacteristics() {
        val missing = mutableListOf<String>()
        if (!hasCharacteristic(txChar)) {
            missing.add("TX notify characteristic $txChar")
        }
        if (!hasCharacteristic(otaChar)) {
            missing.add("OTA write characteristic $otaChar")
        }

        if (missing.isNotEmpty()) {
            val discovered = discoveredCharacteristicUuids()
            val diagnostic =
                if (discovered.isNotEmpty()) {
                    " (discovered characteristics: $discovered)"
                } else {
                    " (no characteristics discovered for OTA service)"
                }
            throw OtaProtocolException.ConnectionFailed(
                "ESP32 OTA service was missing required characteristics after BLE service discovery: " +
                    missing.joinToString(separator = "; ") +
                    diagnostic,
            )
        }
    }

    companion object {
        private val CONNECTION_TIMEOUT = 15.seconds
        private val SUBSCRIPTION_SETTLE = 500.milliseconds
        private val ERASING_TIMEOUT = 60.seconds
        private val ACK_TIMEOUT = 10.seconds
        private val VERIFICATION_TIMEOUT = 10.seconds
        private val REBOOT_DELAY = 5.seconds
        private val CACHE_REFRESH_RECONNECT_DELAY = 1.seconds
        const val RECOMMENDED_CHUNK_SIZE = 512

        /** Fallback write payload when the MTU has not been negotiated (23-byte ATT MTU minus the 3-byte header). */
        private const val SAFE_WRITE_PAYLOAD = 20
    }
}
