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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

/**
 * Returns the reconnect backoff delay in milliseconds for a given consecutive failure count.
 *
 * Backoff schedule: 1 failure → 5 s 2 failures → 10 s 3 failures → 20 s 4 failures → 40 s 5+ failures → 60 s (capped)
 */
internal fun computeReconnectBackoffMs(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 0) return RECONNECT_BASE_DELAY_MS
    return minOf(RECONNECT_BASE_DELAY_MS * (1L shl (consecutiveFailures - 1).coerceAtMost(4)), RECONNECT_MAX_DELAY_MS)
}

// Milliseconds to wait after launching characteristic observations before triggering the
// Meshtastic handshake. Both fromRadio and logRadio observation flows write the CCCD
// asynchronously via Kable's GATT queue. Without this settle window the want_config_id
// burst from the radio can arrive before notifications are enabled, causing the first
// handshake attempt to look like a stall.
private const val CCCD_SETTLE_MS = 50L

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

        Logger.i { "[$address] Device not found in bonded list, scanning..." }

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
                Logger.v(e) { "Scan attempt failed or timed out" }
            }

            if (attempt < SCAN_RETRY_COUNT - 1) {
                delay(SCAN_RETRY_DELAY_MS)
            }
        }

        throw RadioNotConnectedException("Device not found at address $address")
    }

    @Suppress("LongMethod")
    private fun connect() {
        connectionJob = connectionScope.launch {
            while (isActive) {
                try {
                    // Allow any pending background disconnects to complete and the Android BLE stack
                    // to settle before we attempt a new connection.
                    @Suppress("MagicNumber")
                    val connectDelayMs = 1000L
                    delay(connectDelayMs)

                    connectionStartTime = nowMillis
                    Logger.i { "[$address] BLE connection attempt started" }

                    val device = findDevice()

                    // Ensure the device is bonded before connecting. On Android, the
                    // firmware may require an encrypted link (pairing mode != NO_PIN).
                    // Without an explicit bond the GATT connection will fail with
                    // insufficient-authentication (status 5) or the dreaded status 133.
                    // On Desktop/JVM this is a no-op since the OS handles pairing during
                    // the GATT connection when the peripheral requires it.
                    if (!bluetoothRepository.isBonded(address)) {
                        Logger.i { "[$address] Device not bonded, initiating bonding..." }
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
                        // Kable on Android occasionally fails the first connection attempt with
                        // NotConnectedException if the previous peripheral wasn't fully cleaned
                        // up by the OS. A quick retry resolves it.
                        Logger.w { "[$address] First connection attempt failed, retrying in 1.5s..." }
                        @Suppress("MagicNumber")
                        delay(1500L)
                        state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT_MS)
                    }

                    if (state !is BleConnectionState.Connected) {
                        throw RadioNotConnectedException("Failed to connect to device at address $address")
                    }

                    // Connection succeeded — reset failure counter
                    consecutiveFailures = 0
                    isFullyConnected = true
                    onConnected()

                    // Use coroutineScope so that the connectionState listener is scoped to this
                    // iteration only. When the inner scope exits (on disconnect), the listener is
                    // cancelled automatically before the next reconnect cycle starts a fresh one.
                    coroutineScope {
                        bleConnection.connectionState
                            .onEach { s ->
                                if (s is BleConnectionState.Disconnected && isFullyConnected) {
                                    isFullyConnected = false
                                    onDisconnected()
                                }
                            }
                            .catch { e -> Logger.w(e) { "[$address] bleConnection.connectionState flow crashed!" } }
                            .launchIn(this)

                        discoverServicesAndSetupCharacteristics()

                        // Suspend here until Kable drops the connection
                        bleConnection.connectionState.first { it is BleConnectionState.Disconnected }
                    }

                    Logger.i { "[$address] BLE connection dropped, preparing to reconnect..." }
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

                    // At the failure threshold, signal DeviceSleep so MeshConnectionManagerImpl can
                    // start its sleep timeout. Use == (not >=) to fire exactly once; repeated
                    // onDisconnect signals would reset upstream state machines unnecessarily.
                    if (consecutiveFailures == RECONNECT_FAILURE_THRESHOLD) {
                        handleFailure(e)
                    }

                    // Exponential backoff: 5s → 10s → 20s → 40s → capped at 60s.
                    // Reduces BLE stack pressure and battery drain when the device is genuinely
                    // out of range, while still recovering quickly from transient drops.
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
        Logger.w {
            "[$address] BLE disconnected, " +
                "Uptime: ${uptime}ms, " +
                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                "Packets TX: $packetsSent ($bytesSent bytes)"
        }
        // Do NOT call service.onDisconnect() here. The reconnect while-loop handles retries
        // internally. Emitting DeviceSleep on every transient disconnect creates competing state
        // transitions with MeshConnectionManagerImpl's sleep timeout. Instead, handleFailure()
        // is called from the catch block after RECONNECT_FAILURE_THRESHOLD consecutive failures.
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

                // Give Kable's async CCCD writes time to complete before triggering the
                // Meshtastic handshake. The fromRadio/logRadio observation flows register
                // notifications through the GATT queue asynchronously. Without this settle
                // window, the want_config_id burst arrives before notifications are enabled.
                delay(CCCD_SETTLE_MS)

                // Log negotiated MTU for diagnostics
                val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
                Logger.i { "[$address] BLE Radio Session Ready. Max write length (WITHOUT_RESPONSE): $maxLen bytes" }

                this@BleRadioInterface.service.onConnect()
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Profile service discovery or operation failed" }
            // Ensure the peripheral is disconnected so the outer reconnect loop sees a clean
            // Disconnected state. Do NOT call handleFailure here — the reconnect loop tracks
            // consecutive failures and calls handleFailure after RECONNECT_FAILURE_THRESHOLD,
            // preventing premature onDisconnect signals to the service on transient errors.
            try {
                bleConnection.disconnect()
            } catch (ignored: Exception) {
                Logger.w(ignored) { "[$address] disconnect() failed after profile error" }
            }
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
        Logger.d { "[$address] BLE keepAlive — sending ToRadio heartbeat (nonce=$nonce)" }
        handleSendToRadio(ToRadio(heartbeat = Heartbeat(nonce = nonce)).encode())
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
        // Cancel the connection scope to break the while(isActive) reconnect loop.
        connectionScope.cancel("close() called")
        // GATT cleanup must run regardless of serviceScope lifecycle. SharedRadioInterfaceService
        // cancels serviceScope immediately after calling close(), so launching on serviceScope is
        // not reliable — the coroutine may never start. We use withContext(NonCancellable) inside
        // a serviceScope.launch to guarantee cleanup completes even if the scope is cancelled
        // mid-flight, preventing leaked BluetoothGatt objects (GATT 133 errors).
        // onDisconnect is handled by SharedRadioInterfaceService.stopInterfaceLocked() directly.
        serviceScope.launch {
            withContext(NonCancellable) {
                // Send ToRadio.disconnect before dropping the BLE link. The firmware calls its
                // own close() immediately on receipt, resetting the PhoneAPI state machine
                // (config nonce, packet queue, observers) without waiting for the 6-second BLE
                // supervision timeout. Best-effort: if the write fails we still disconnect below.
                val currentService = radioService
                if (currentService != null) {
                    try {
                        withTimeoutOrNull(2_000L) { currentService.sendToRadio(ToRadio(disconnect = true).encode()) }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Logger.w(e) { "[$address] Failed to send disconnect signal" }
                    }
                }
                try {
                    bleConnection.disconnect()
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "[$address] Failed to disconnect in close()" }
                }
            }
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
