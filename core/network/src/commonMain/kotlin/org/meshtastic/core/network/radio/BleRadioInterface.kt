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
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught")

package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.retryBleOperation
import org.meshtastic.core.ble.toMeshtasticRadioProfile
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private const val SCAN_RETRY_DELAY_MS = 1000L
private const val CONNECTION_TIMEOUT_MS = 15_000L
private val SCAN_TIMEOUT = 5.seconds

/**
 * A [RadioTransport] implementation for BLE devices using the common BLE abstractions (which are powered by Kable).
 *
 * This class handles the high-level connection lifecycle for Meshtastic radios over BLE, including:
 * - Bonding and discovery.
 * - Automatic reconnection logic.
 * - MTU and connection parameter monitoring.
 * - Routing raw byte packets between the radio and [RadioInterfaceService].
 *
 * @param serviceScope The coroutine scope to use for launching coroutines.
 * @param scanner The BLE scanner.
 * @param bluetoothRepository The Bluetooth repository.
 * @param connectionFactory The BLE connection factory.
 * @param service The [RadioInterfaceService] to use for handling radio events.
 * @param address The BLE address of the device to connect to.
 */
class BleRadioInterface(
    private val serviceScope: CoroutineScope,
    private val scanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val connectionFactory: BleConnectionFactory,
    private val service: RadioInterfaceService,
    val address: String,
) : RadioTransport {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.w(throwable) { "[$address] Uncaught exception in connectionScope" }
        serviceScope.launch {
            try {
                bleConnection.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in exception handler" }
            }
        }
        val (isPermanent, msg) = throwable.toDisconnectReason()
        service.onDisconnect(isPermanent, errorMessage = msg)
    }

    private val connectionScope: CoroutineScope =
        CoroutineScope(
            serviceScope.coroutineContext + SupervisorJob(serviceScope.coroutineContext.job) + exceptionHandler,
        )
    private val bleConnection: BleConnection = connectionFactory.create(connectionScope, address)
    private val writeMutex: Mutex = Mutex()

    private var connectionStartTime: Long = 0
    private var packetsReceived: Int = 0
    private var packetsSent: Int = 0
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0

    @Suppress("VolatileModifier")
    @Volatile
    private var isFullyConnected = false

    init {
        connect()
    }

    // --- Connection & Discovery Logic ---

    /** Robustly finds the device. First checks bonded devices, then performs a short scan if not found. */
    private suspend fun findDevice(): BleDevice {
        bluetoothRepository.state.value.bondedDevices
            .firstOrNull { it.address == address }
            ?.let {
                return it
            }

        Logger.i { "[$address] Device not found in bonded list, scanning..." }

        repeat(SCAN_RETRY_COUNT) { attempt ->
            try {
                val d =
                    kotlinx.coroutines.withTimeoutOrNull(SCAN_TIMEOUT) {
                        scanner.scan(timeout = SCAN_TIMEOUT, serviceUuid = SERVICE_UUID, address = address).first {
                            it.address == address
                        }
                    }
                if (d != null) return d
            } catch (e: Exception) {
                Logger.v(e) { "Scan attempt failed or timed out" }
            }

            if (attempt < SCAN_RETRY_COUNT - 1) {
                delay(SCAN_RETRY_DELAY_MS)
            }
        }

        throw RadioNotConnectedException("Device not found at address $address")
    }

    private fun connect() {
        connectionScope.launch {
            bleConnection.connectionState
                .onEach { state ->
                    if (state is BleConnectionState.Disconnected && isFullyConnected) {
                        isFullyConnected = false
                        onDisconnected(state)
                    }
                }
                .catch { e ->
                    Logger.w(e) { "[$address] bleConnection.connectionState flow crashed!" }
                    handleFailure(e)
                }
                .launchIn(connectionScope)

            while (isActive) {
                try {
                    // Add a delay to allow any pending background disconnects (from a previous close() call)
                    // to complete and the Android BLE stack to settle before we attempt a new connection.
                    @Suppress("MagicNumber")
                    val connectDelayMs = 1000L
                    kotlinx.coroutines.delay(connectDelayMs)

                    connectionStartTime = nowMillis
                    Logger.i { "[$address] BLE connection attempt started" }

                    val device = findDevice()

                    var state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)

                    if (state !is BleConnectionState.Connected) {
                        // Kable on Android occasionally fails the first connection attempt with NotConnectedException
                        // if the previous peripheral wasn't fully cleaned up by the OS. A quick retry resolves it.
                        Logger.w { "[$address] First connection attempt failed, retrying in 1.5s..." }
                        @Suppress("MagicNumber")
                        val retryDelayMs = 1500L
                        kotlinx.coroutines.delay(retryDelayMs)
                        state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)
                    }

                    if (state !is BleConnectionState.Connected) {
                        throw RadioNotConnectedException("Failed to connect to device at address $address")
                    }

                    isFullyConnected = true
                    onConnected()
                    discoverServicesAndSetupCharacteristics()

                    // Suspend here until Kable drops the connection
                    bleConnection.connectionState.first { it is BleConnectionState.Disconnected }

                    Logger.i { "[$address] BLE connection dropped, preparing to reconnect..." }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Logger.d { "[$address] BLE connection coroutine cancelled" }
                    throw e
                } catch (e: Exception) {
                    val failureTime = nowMillis - connectionStartTime
                    Logger.w(e) { "[$address] Failed to connect to device after ${failureTime}ms" }
                    handleFailure(e)

                    // Wait before retrying to prevent hot loops
                    @Suppress("MagicNumber")
                    kotlinx.coroutines.delay(5000L)
                }
            }
        }
    }

    private suspend fun onConnected() {
        try {
            bleConnection.deviceFlow.first()?.let { device ->
                val rssi = retryBleOperation(tag = address) { device.readRssi() }
                Logger.d { "[$address] Connection confirmed. Initial RSSI: $rssi dBm" }
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Failed to read initial connection RSSI" }
        }
    }

    private fun onDisconnected(@Suppress("UNUSED_PARAMETER") state: BleConnectionState.Disconnected) {
        radioService = null

        val uptime =
            if (connectionStartTime > 0) {
                nowMillis - connectionStartTime
            } else {
                0
            }
        Logger.w {
            "[$address] BLE disconnected, " +
                "Uptime: ${uptime}ms, " +
                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                "Packets TX: $packetsSent ($bytesSent bytes)"
        }

        // Note: Disconnected state in commonMain doesn't currently carry a reason.
        // We might want to add that later if needed.
        service.onDisconnect(false, errorMessage = "Disconnected")
    }

    private suspend fun discoverServicesAndSetupCharacteristics() {
        try {
            bleConnection.profile(serviceUuid = SERVICE_UUID) { service ->
                val radioService = service.toMeshtasticRadioProfile()

                // Wire up notifications
                radioService.fromRadio
                    .onEach { packet ->
                        Logger.d { "[$address] Received packet fromRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in fromRadio flow" }
                        handleFailure(e)
                    }
                    .launchIn(this)

                radioService.logRadio
                    .onEach { packet ->
                        Logger.d { "[$address] Received packet logRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in logRadio flow" }
                        handleFailure(e)
                    }
                    .launchIn(this)

                // Store reference for handleSendToRadio
                this@BleRadioInterface.radioService = radioService

                Logger.i { "[$address] Profile service active and characteristics subscribed" }

                // Log negotiated MTU for diagnostics
                val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
                Logger.i { "[$address] BLE Radio Session Ready. Max write length (WITHOUT_RESPONSE): $maxLen bytes" }

                this@BleRadioInterface.service.onConnect()
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Profile service discovery or operation failed" }
            bleConnection.disconnect()
            handleFailure(e)
        }
    }

    private var radioService: org.meshtastic.core.ble.MeshtasticRadioProfile? = null

    // --- RadioTransport Implementation ---

    /**
     * Sends a packet to the radio with retry support.
     *
     * @param p The packet to send.
     */
    override fun handleSendToRadio(p: ByteArray) {
        val currentService = radioService
        if (currentService != null) {
            connectionScope.launch {
                writeMutex.withLock {
                    try {
                        retryBleOperation(tag = address) { currentService.sendToRadio(p) }
                        packetsSent++
                        bytesSent += p.size
                        Logger.d {
                            "[$address] Successfully wrote packet #$packetsSent " +
                                "to toRadioCharacteristic - " +
                                "${p.size} bytes (Total TX: $bytesSent bytes)"
                        }
                    } catch (e: Exception) {
                        Logger.w(e) {
                            "[$address] Failed to write packet to toRadioCharacteristic after " +
                                "$packetsSent successful writes"
                        }
                        handleFailure(e)
                    }
                }
            }
        } else {
            Logger.w { "[$address] toRadio characteristic unavailable, can't send data" }
        }
    }

    override fun keepAlive() {
        Logger.d { "[$address] BLE keepAlive" }
    }

    /** Closes the connection to the device. */
    override fun close() {
        val uptime =
            if (connectionStartTime > 0) {
                nowMillis - connectionStartTime
            } else {
                0
            }
        Logger.i {
            "[$address] Disconnecting. " +
                "Uptime: ${uptime}ms, " +
                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                "Packets TX: $packetsSent ($bytesSent bytes)"
        }
        serviceScope.launch {
            connectionScope.cancel()
            bleConnection.disconnect()
            service.onDisconnect(true)
        }
    }

    private fun dispatchPacket(packet: ByteArray) {
        packetsReceived++
        bytesReceived += packet.size
        Logger.d {
            "[$address] Dispatching packet to service.handleFromRadio() - " +
                "Packet #$packetsReceived, ${packet.size} bytes (Total: $bytesReceived bytes)"
        }
        service.handleFromRadio(packet)
    }

    private fun handleFailure(throwable: Throwable) {
        val (isPermanent, msg) = throwable.toDisconnectReason()
        service.onDisconnect(isPermanent, errorMessage = msg)
    }

    private fun Throwable.toDisconnectReason(): Pair<Boolean, String> {
        val isPermanent =
            this::class.simpleName == "BluetoothUnavailableException" ||
                this::class.simpleName == "ManagerClosedException"
        val msg =
            when {
                this is RadioNotConnectedException -> this.message ?: "Device not found"
                this is NoSuchElementException || this is IllegalArgumentException -> "Required characteristic missing"
                this::class.simpleName == "GattException" -> "GATT Error: ${this.message}"
                else -> this.message ?: this::class.simpleName ?: "Unknown"
            }
        return Pair(isPermanent, msg)
    }
}
