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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.DisconnectReason
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.classifyBleException
import org.meshtastic.core.ble.retryBleOperation
import org.meshtastic.core.ble.toMeshtasticRadioProfile
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private const val SCAN_RETRY_DELAY_MS = 1000L
private const val CONNECTION_TIMEOUT_MS = 15_000L
private const val RECONNECT_FAILURE_THRESHOLD = 3
private const val RECONNECT_BASE_DELAY_MS = 5_000L
private const val RECONNECT_MAX_DELAY_MS = 60_000L
private const val RECONNECT_MAX_FAILURES = 10

/**
 * Minimum milliseconds a BLE connection must stay up before we consider it "stable" and reset
 * [BleRadioInterface.consecutiveFailures]. Without this, a device at the edge of BLE range can repeatedly connect for a
 * fraction of a second and drop — each brief connection resets the failure counter so [RECONNECT_FAILURE_THRESHOLD] is
 * never reached, and the app never signals [ConnectionState.DeviceSleep].
 *
 * The value (5 s) is long enough that only connections that survive past the initial GATT setup are treated as genuine,
 * but short enough that normal reconnects after light-sleep still reset the counter promptly.
 */
private const val MIN_STABLE_CONNECTION_MS = 5_000L

/**
 * Returns the reconnect backoff delay in milliseconds for a given consecutive failure count.
 *
 * Backoff schedule: 1 failure → 5 s 2 failures → 10 s 3 failures → 20 s 4 failures → 40 s 5+ failures → 60 s (capped)
 */
internal fun computeReconnectBackoffMs(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 0) return RECONNECT_BASE_DELAY_MS
    return minOf(RECONNECT_BASE_DELAY_MS * (1L shl (consecutiveFailures - 1).coerceAtMost(4)), RECONNECT_MAX_DELAY_MS)
}

/**
 * Milliseconds to wait after writing a heartbeat before re-polling FROMRADIO.
 *
 * The ESP32 firmware processes TORADIO writes asynchronously (NimBLE callback → FreeRTOS main task queue →
 * `handleToRadio()` → `heartbeatReceived = true`). The immediate drain trigger in
 * [KableMeshtasticRadioProfile.sendToRadio] fires before this completes, so the `queueStatus` response is not yet
 * available. 200 ms is well above observed ESP32 task scheduling latency (~10–50 ms) while remaining imperceptible to
 * the user.
 */
private const val HEARTBEAT_DRAIN_DELAY_MS = 200L

private val SCAN_TIMEOUT = 5.seconds
private val GATT_CLEANUP_TIMEOUT = 5.seconds

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

    @Volatile private var isFullyConnected = false
    private var connectionJob: Job? = null
    private var consecutiveFailures = 0

    @OptIn(ExperimentalAtomicApi::class)
    private val heartbeatNonce = AtomicInt(0)

    init {
        connect()
    }

    // --- Connection & Discovery Logic ---

    /** Robustly finds the device. First checks bonded devices, then performs a short scan if not found. */
    private suspend fun findDevice(): BleDevice {
        bluetoothRepository.state.value.bondedDevices
            .firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?.let {
                return it
            }

        Logger.i { "[$address] Device not found in bonded list, scanning" }

        repeat(SCAN_RETRY_COUNT) { attempt ->
            try {
                val d =
                    withTimeoutOrNull(SCAN_TIMEOUT) {
                        scanner.scan(timeout = SCAN_TIMEOUT, serviceUuid = SERVICE_UUID, address = address).first {
                            it.address.equals(address, ignoreCase = true)
                        }
                    }
                if (d != null) return d
            } catch (e: Exception) {
                Logger.v(e) { "[$address] Scan attempt failed or timed out" }
            }

            if (attempt < SCAN_RETRY_COUNT - 1) {
                delay(SCAN_RETRY_DELAY_MS)
            }
        }

        throw RadioNotConnectedException("Device not found at address $address")
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun connect() {
        connectionJob =
            connectionScope.launch {
                while (isActive) {
                    try {
                        // Settle delay: let the Android BLE stack finish any pending
                        // disconnect cleanup before starting a new connection attempt.
                        @Suppress("MagicNumber")
                        val connectDelayMs = 1000L
                        delay(connectDelayMs)

                        connectionStartTime = nowMillis
                        Logger.i { "[$address] BLE connection attempt started" }

                        val device = findDevice()

                        // Bond before connecting: firmware may require an encrypted link,
                        // and without a bond Android fails with status 5 or 133.
                        // No-op on Desktop/JVM where the OS handles pairing automatically.
                        if (!bluetoothRepository.isBonded(address)) {
                            Logger.i { "[$address] Device not bonded, initiating bonding" }
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                bluetoothRepository.bond(device)
                                Logger.i { "[$address] Bonding successful" }
                            } catch (e: Exception) {
                                Logger.w(e) { "[$address] Bonding failed, attempting connection anyway" }
                            }
                        }

                        var state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)

                        if (state !is BleConnectionState.Connected) {
                            // Kable sometimes fails the first attempt if the previous GATT
                            // session wasn't fully cleaned up by the OS. One retry resolves it.
                            Logger.d { "[$address] First connection attempt failed, retrying in 1.5s" }
                            @Suppress("MagicNumber")
                            delay(1500L)
                            state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)
                        }

                        if (state !is BleConnectionState.Connected) {
                            throw RadioNotConnectedException("Failed to connect to device at address $address")
                        }

                        // Only reset failures if connection was stable (see MIN_STABLE_CONNECTION_MS).
                        val gattConnectedAt = nowMillis
                        isFullyConnected = true
                        onConnected()

                        // Scope the connectionState listener to this iteration so it's
                        // cancelled automatically before the next reconnect cycle.
                        var disconnectReason: DisconnectReason = DisconnectReason.Unknown
                        coroutineScope {
                            bleConnection.connectionState
                                .onEach { s ->
                                    if (s is BleConnectionState.Disconnected && isFullyConnected) {
                                        isFullyConnected = false
                                        disconnectReason = s.reason
                                        onDisconnected()
                                    }
                                }
                                .catch { e -> Logger.w(e) { "[$address] bleConnection.connectionState flow crashed" } }
                                .launchIn(this)

                            discoverServicesAndSetupCharacteristics()

                            bleConnection.connectionState.first { it is BleConnectionState.Disconnected }
                        }

                        Logger.i {
                            "[$address] BLE connection dropped (reason: $disconnectReason), preparing to reconnect"
                        }

                        // Skip failure counting for intentional disconnects.
                        if (disconnectReason is DisconnectReason.LocalDisconnect) {
                            consecutiveFailures = 0
                            continue
                        }

                        // A connection that drops almost immediately (< MIN_STABLE_CONNECTION_MS)
                        // is treated as a failure — the BLE stack may have "connected" to a
                        // cached GATT profile before realising the device is gone.
                        val connectionUptime = nowMillis - gattConnectedAt
                        if (connectionUptime >= MIN_STABLE_CONNECTION_MS) {
                            consecutiveFailures = 0
                        } else {
                            consecutiveFailures++
                            Logger.w {
                                "[$address] Connection lasted only ${connectionUptime}ms " +
                                    "(< ${MIN_STABLE_CONNECTION_MS}ms) — treating as failure " +
                                    "(consecutive failures: $consecutiveFailures)"
                            }
                            if (consecutiveFailures >= RECONNECT_MAX_FAILURES) {
                                Logger.e { "[$address] Giving up after $consecutiveFailures unstable connections" }
                                service.onDisconnect(
                                    isPermanent = true,
                                    errorMessage = "Device unreachable (unstable connection)",
                                )
                                return@launch
                            }
                            if (consecutiveFailures >= RECONNECT_FAILURE_THRESHOLD) {
                                service.onDisconnect(
                                    isPermanent = false,
                                    errorMessage = "Device unreachable (unstable connection)",
                                )
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Logger.d { "[$address] BLE connection coroutine cancelled" }
                        throw e
                    } catch (e: Exception) {
                        val failureTime = nowMillis - connectionStartTime
                        consecutiveFailures++
                        Logger.w(e) {
                            "[$address] Failed to connect to device after ${failureTime}ms " +
                                "(consecutive failures: $consecutiveFailures)"
                        }

                        // Give up permanently to stop draining battery.
                        if (consecutiveFailures >= RECONNECT_MAX_FAILURES) {
                            Logger.e { "[$address] Giving up after $consecutiveFailures consecutive failures" }
                            val (_, msg) = e.toDisconnectReason()
                            service.onDisconnect(isPermanent = true, errorMessage = msg)
                            return@launch
                        }

                        // Signal DeviceSleep so MeshConnectionManagerImpl starts its sleep timeout.
                        if (consecutiveFailures >= RECONNECT_FAILURE_THRESHOLD) {
                            handleFailure(e)
                        }

                        val backoffMs = computeReconnectBackoffMs(consecutiveFailures)
                        Logger.d { "[$address] Retrying in ${backoffMs}ms (failure #$consecutiveFailures)" }
                        delay(backoffMs)
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

    private fun onDisconnected() {
        radioService = null

        val uptime =
            if (connectionStartTime > 0) {
                nowMillis - connectionStartTime
            } else {
                0
            }
        Logger.i {
            "[$address] BLE disconnected - " +
                "Uptime: ${uptime}ms, " +
                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                "Packets TX: $packetsSent ($bytesSent bytes)"
        }
        // Signal immediately so the UI reflects the disconnect while reconnect continues.
        service.onDisconnect(isPermanent = false)
    }

    private suspend fun discoverServicesAndSetupCharacteristics() {
        try {
            bleConnection.profile(serviceUuid = SERVICE_UUID) { service ->
                val radioService = service.toMeshtasticRadioProfile()

                radioService.fromRadio
                    .onEach { packet ->
                        Logger.v { "[$address] Received packet fromRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in fromRadio flow" }
                        handleFailure(e)
                    }
                    .launchIn(this)

                radioService.logRadio
                    .onEach { packet ->
                        Logger.v { "[$address] Received packet logRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in logRadio flow" }
                        handleFailure(e)
                    }
                    .launchIn(this)

                this@BleRadioInterface.radioService = radioService

                Logger.i { "[$address] Profile service active and characteristics subscribed" }

                // Wait for FROMNUM CCCD write before triggering the Meshtastic handshake.
                radioService.awaitSubscriptionReady()

                // Log negotiated MTU for diagnostics
                val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
                Logger.i { "[$address] BLE Radio Session Ready. Max write length (WITHOUT_RESPONSE): $maxLen bytes" }

                this@BleRadioInterface.service.onConnect()
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Profile service discovery or operation failed" }
            // Disconnect to let the outer reconnect loop see a clean Disconnected state.
            // Do NOT call handleFailure here — the reconnect loop owns failure counting.
            try {
                bleConnection.disconnect()
            } catch (ignored: Exception) {
                Logger.w(ignored) { "[$address] disconnect() failed after profile error" }
            }
        }
    }

    @Volatile private var radioService: org.meshtastic.core.ble.MeshtasticRadioProfile? = null

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
                        Logger.v {
                            "[$address] Wrote packet #$packetsSent " +
                                "to toRadio (${p.size} bytes, total TX: $bytesSent bytes)"
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

    @OptIn(ExperimentalAtomicApi::class)
    override fun keepAlive() {
        // Send a ToRadio heartbeat so the firmware resets its power-saving idle timer.
        // The firmware only resets the timer on writes to the TORADIO characteristic; a
        // BLE-level GATT keepalive is invisible to it. Without this the device may enter
        // light-sleep and drop the BLE connection after ~60 s of application inactivity.
        //
        // Each heartbeat uses a distinct nonce to vary the wire bytes, preventing the
        // firmware's per-connection duplicate-write filter from silently dropping it.
        val nonce = heartbeatNonce.fetchAndAdd(1)
        Logger.v { "[$address] BLE keepAlive — sending ToRadio heartbeat (nonce=$nonce)" }
        handleSendToRadio(ToRadio(heartbeat = Heartbeat(nonce = nonce)).encode())

        // The firmware responds to heartbeats by queuing a `queueStatus` FromRadio packet
        // on the next getFromRadio() call, but it does NOT send a FROMNUM notification for
        // it. The immediate drain trigger in sendToRadio() fires before the ESP32's async
        // task queue has processed the heartbeat, so the response sits unread. Schedule a
        // delayed re-drain to pick it up.
        connectionScope.launch {
            @Suppress("MagicNumber")
            delay(HEARTBEAT_DRAIN_DELAY_MS)
            radioService?.requestDrain()
        }
    }

    /** Closes the connection to the device. */
    override fun close() {
        val uptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
        Logger.i {
            "[$address] Disconnecting. " +
                "Uptime: ${uptime}ms, " +
                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                "Packets TX: $packetsSent ($bytesSent bytes)"
        }
        connectionScope.cancel("close() called")
        // GATT cleanup must outlive serviceScope cancellation — GlobalScope is intentional.
        // SharedRadioInterfaceService cancels serviceScope immediately after close(), so a
        // coroutine launched there may never run, leaking BluetoothGatt (causes GATT 133).
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                withTimeoutOrNull(GATT_CLEANUP_TIMEOUT) { bleConnection.disconnect() }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in close()" }
            }
        }
    }

    private fun dispatchPacket(packet: ByteArray) {
        packetsReceived++
        bytesReceived += packet.size
        Logger.v {
            "[$address] Dispatching packet #$packetsReceived " +
                "(${packet.size} bytes, total RX: $bytesReceived bytes)"
        }
        service.handleFromRadio(packet)
    }

    private fun handleFailure(throwable: Throwable) {
        val (isPermanent, msg) = throwable.toDisconnectReason()
        service.onDisconnect(isPermanent, errorMessage = msg)
    }

    private fun Throwable.toDisconnectReason(): Pair<Boolean, String> {
        classifyBleException()?.let {
            return Pair(it.isPermanent, it.message)
        }

        val msg =
            when (this) {
                is RadioNotConnectedException -> this.message ?: "Device not found"
                is NoSuchElementException,
                is IllegalArgumentException,
                -> "Required characteristic missing"
                else -> this.message ?: this::class.simpleName ?: "Unknown"
            }
        return Pair(false, msg)
    }
}
