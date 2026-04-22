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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import org.meshtastic.core.ble.DisconnectReason
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticRadioProfile
import org.meshtastic.core.ble.classifyBleException
import org.meshtastic.core.ble.retryBleOperation
import org.meshtastic.core.ble.toMeshtasticRadioProfile
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private val SCAN_RETRY_DELAY = 1.seconds
private val CONNECTION_TIMEOUT = 15.seconds

/**
 * Delay after writing a heartbeat before re-polling FROMRADIO.
 *
 * The ESP32 firmware processes TORADIO writes asynchronously (NimBLE callback → FreeRTOS main task queue →
 * `handleToRadio()` → `heartbeatReceived = true`). The immediate drain trigger in
 * [KableMeshtasticRadioProfile.sendToRadio] fires before this completes, so the `queueStatus` response is not yet
 * available. 200 ms is well above observed ESP32 task scheduling latency (~10–50 ms) while remaining imperceptible to
 * the user.
 */
private val HEARTBEAT_DRAIN_DELAY = 200.milliseconds

private val SCAN_TIMEOUT = 5.seconds
private val GATT_CLEANUP_TIMEOUT = 5.seconds

/**
 * A [RadioTransport] implementation for BLE devices using the common BLE abstractions (which are powered by Kable).
 *
 * This class handles the high-level connection lifecycle for Meshtastic radios over BLE, including:
 * - Bonding and discovery.
 * - Automatic reconnection logic.
 * - MTU and connection parameter monitoring.
 * - Routing raw byte packets between the radio and [RadioTransportCallback].
 *
 * @param scope The coroutine scope to use for launching coroutines.
 * @param scanner The BLE scanner.
 * @param bluetoothRepository The Bluetooth repository.
 * @param connectionFactory The BLE connection factory.
 * @param callback The [RadioTransportCallback] to use for handling radio events.
 * @param address The BLE address of the device to connect to.
 */
class BleRadioTransport(
    private val scope: CoroutineScope,
    private val scanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val connectionFactory: BleConnectionFactory,
    private val callback: RadioTransportCallback,
    internal val address: String,
) : RadioTransport {

    // Detached cleanup scope for last-ditch GATT teardown from the exception handler.
    // Must NOT be a child of `scope`: when an uncaught exception fires in connectionScope,
    // upper layers often tear down `scope` immediately. Launching cleanup on `scope` then
    // races the cancellation and may never start, leaking BluetoothGatt (status 133 on
    // the next reconnect). This scope is cancelled in close() once our own disconnect
    // has completed and the safety net is no longer needed.
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + scope.coroutineContext.minusKey(Job))

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.w(throwable) { "[$address] Uncaught exception in connectionScope" }
        cleanupScope.launch {
            try {
                bleConnection.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in exception handler" }
            }
        }
        val (isPermanent, msg) = throwable.toDisconnectReason()
        callback.onDisconnect(isPermanent, errorMessage = msg)
    }

    private val connectionScope: CoroutineScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job) + exceptionHandler)
    private val bleConnection: BleConnection = connectionFactory.create(connectionScope, address)
    private val writeMutex: Mutex = Mutex()

    @Volatile private var connectionStartTime: Long = 0

    @Volatile private var packetsReceived: Int = 0

    @Volatile private var packetsSent: Int = 0

    @Volatile private var bytesReceived: Long = 0

    @Volatile private var bytesSent: Long = 0

    @Volatile private var isFullyConnected = false
    private var connectionJob: Job? = null

    // Never give up while the user has this device selected. Higher layers (SharedRadioInterfaceService)
    // own the explicit-disconnect lifecycle and will close() us when the user picks a different device or
    // toggles the connection off; until then, retry forever with the policy's exponential-backoff cap (60 s).
    private val reconnectPolicy = BleReconnectPolicy(maxFailures = Int.MAX_VALUE)

    private val heartbeatSender =
        HeartbeatSender(
            sendToRadio = ::handleSendToRadio,
            afterHeartbeat = {
                delay(HEARTBEAT_DRAIN_DELAY)
                radioService?.requestDrain()
            },
            logTag = address,
        )

    override fun start() {
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
                delay(SCAN_RETRY_DELAY)
            }
        }

        throw RadioNotConnectedException("Device not found at address $address")
    }

    private fun connect() {
        connectionJob =
            connectionScope.launch {
                reconnectPolicy.execute(
                    attempt = {
                        try {
                            attemptConnection()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val failureTime = (nowMillis - connectionStartTime).milliseconds
                            Logger.w(e) { "[$address] Failed to connect after $failureTime" }
                            BleReconnectPolicy.Outcome.Failed(e)
                        }
                    },
                    onTransientDisconnect = { error ->
                        val msg = error?.toDisconnectReason()?.second ?: "Device unreachable"
                        callback.onDisconnect(isPermanent = false, errorMessage = msg)
                    },
                    onPermanentDisconnect = { error ->
                        val msg = error?.toDisconnectReason()?.second ?: "Device unreachable"
                        callback.onDisconnect(isPermanent = true, errorMessage = msg)
                    },
                )
            }
    }

    /**
     * Performs a single BLE connect-and-wait cycle.
     *
     * Finds the device, bonds if needed, connects, discovers services, and waits for disconnect. Returns a
     * [BleReconnectPolicy.Outcome] describing how the connection ended.
     */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun attemptConnection(): BleReconnectPolicy.Outcome {
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

        val state = bleConnection.connectAndAwait(device, CONNECTION_TIMEOUT)

        if (state !is BleConnectionState.Connected) {
            throw RadioNotConnectedException("Failed to connect to device at address $address")
        }

        val gattConnectedAt = nowMillis
        isFullyConnected = true
        onConnected()

        discoverServicesAndSetupCharacteristics()

        // Wait for the StateFlow to actually reflect Connected before watching for the next
        // Disconnected. connectAndAwait returns synchronously based on the underlying Kable
        // peripheral state, but our _connectionState observer runs on a separate coroutine and
        // may lag. Without this gate the next .first { Disconnected } below could match the
        // *previous* cycle's stale Disconnected value and fire immediately, breaking reconnect.
        bleConnection.connectionState.first { it is BleConnectionState.Connected }

        // Suspend until the next Disconnected emission. We deliberately do NOT wrap this in a
        // coroutineScope { launchIn(...); first(...) } pattern: launching a hot StateFlow
        // collector inside coroutineScope hangs the scope after .first returns (the launched
        // collector never completes naturally, and coroutineScope waits for all children).
        val disconnectedState =
            bleConnection.connectionState.filterIsInstance<BleConnectionState.Disconnected>().first()
        val disconnectReason = disconnectedState.reason
        if (isFullyConnected) {
            isFullyConnected = false
            onDisconnected()
        }

        Logger.i { "[$address] BLE connection dropped (reason: $disconnectReason), preparing to reconnect" }

        val wasIntentional = disconnectReason is DisconnectReason.LocalDisconnect
        val connectionUptime = (nowMillis - gattConnectedAt).milliseconds
        val wasStable = connectionUptime >= reconnectPolicy.minStableConnection

        if (!wasStable && !wasIntentional) {
            Logger.w {
                "[$address] Connection lasted only $connectionUptime " +
                    "(< ${reconnectPolicy.minStableConnection}) — treating as unstable"
            }
        }

        return BleReconnectPolicy.Outcome.Disconnected(wasStable = wasStable, wasIntentional = wasIntentional)
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
        Logger.i { "[$address] BLE disconnected - ${formatSessionStats()}" }
        // Signal immediately so the UI reflects the disconnect while reconnect continues.
        callback.onDisconnect(isPermanent = false)
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

                this@BleRadioTransport.radioService = radioService

                Logger.i { "[$address] Profile service active and characteristics subscribed" }

                // Wait for FROMNUM CCCD write before triggering the Meshtastic handshake.
                radioService.awaitSubscriptionReady()

                // Log negotiated MTU for diagnostics
                val maxLen = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE)
                Logger.i { "[$address] BLE Radio Session Ready. Max write length (WITHOUT_RESPONSE): $maxLen bytes" }

                // Ask the platform for a low-latency / high-throughput connection interval
                // (~7.5 ms on Android). The Meshtastic firmware happily accepts this and it
                // materially speeds up the initial config drain and any bulk fromRadio reads.
                if (bleConnection.requestHighConnectionPriority()) {
                    Logger.d { "[$address] Requested high BLE connection priority" }
                }

                this@BleRadioTransport.callback.onConnect()
            }
        } catch (e: CancellationException) {
            // Scope was cancelled externally — still ensure GATT cleanup runs so we don't
            // leak a BluetoothGatt handle and trigger GATT status 133 on the next attempt.
            withContext(NonCancellable) {
                try {
                    bleConnection.disconnect()
                } catch (ignored: Exception) {
                    Logger.w(ignored) { "[$address] disconnect() failed during cancellation cleanup" }
                }
            }
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Profile service discovery or operation failed" }
            // Disconnect to let the outer reconnect loop see a clean Disconnected state.
            // Do NOT call handleFailure here — the reconnect loop owns failure counting.
            withContext(NonCancellable) {
                try {
                    bleConnection.disconnect()
                } catch (ignored: Exception) {
                    Logger.w(ignored) { "[$address] disconnect() failed after profile error" }
                }
            }
        }
    }

    @Volatile private var radioService: MeshtasticRadioProfile? = null

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

    override fun keepAlive() {
        // Delegate to HeartbeatSender which sends a ToRadio heartbeat with a unique nonce
        // so the firmware resets its power-saving idle timer. After sending, it schedules
        // a delayed re-drain to pick up the queueStatus response.
        connectionScope.launch { heartbeatSender.sendHeartbeat() }
    }

    /** Closes the connection to the device. */
    override suspend fun close() {
        Logger.i { "[$address] Disconnecting. ${formatSessionStats()}" }
        connectionScope.cancel("close() called")
        // GATT cleanup must run under NonCancellable so a cancelled caller cannot skip it,
        // which would leak BluetoothGatt and trigger status 133 on the next reconnect.
        // Using withContext (not runBlocking) keeps the caller's thread free — this is
        // critical when close() is invoked from the main thread during process shutdown.
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(GATT_CLEANUP_TIMEOUT) { bleConnection.disconnect() }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in close()" }
            }
        }
        // Our own disconnect succeeded — the exception-handler safety net is no longer
        // needed. Cancel the detached cleanup scope so it doesn't outlive us in tests
        // or process lifetime.
        cleanupScope.cancel("close() called")
    }

    private fun dispatchPacket(packet: ByteArray) {
        packetsReceived++
        bytesReceived += packet.size
        Logger.v {
            "[$address] Dispatching packet #$packetsReceived " +
                "(${packet.size} bytes, total RX: $bytesReceived bytes)"
        }
        callback.handleFromRadio(packet)
    }

    private fun handleFailure(throwable: Throwable) {
        val (isPermanent, msg) = throwable.toDisconnectReason()
        callback.onDisconnect(isPermanent, errorMessage = msg)
    }

    /** Formats a one-line session statistics summary for logging. */
    private fun formatSessionStats(): String {
        val uptime = if (connectionStartTime > 0) nowMillis - connectionStartTime else 0
        return "Uptime: ${uptime}ms, " +
            "Packets RX: $packetsReceived ($bytesReceived bytes), " +
            "Packets TX: $packetsSent ($bytesSent bytes)"
    }

    private fun Throwable.toDisconnectReason(): Pair<Boolean, String> {
        classifyBleException()?.let {
            return it.isPermanent to it.message
        }

        val msg =
            when (this) {
                is RadioNotConnectedException -> this.message ?: "Device not found"
                is NoSuchElementException,
                is IllegalArgumentException,
                -> "Required characteristic missing"
                else -> this.message ?: this::class.simpleName ?: "Unknown"
            }
        return false to msg
    }
}
