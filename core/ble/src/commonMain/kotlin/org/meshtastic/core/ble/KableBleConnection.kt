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
package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [BleService] implementation backed by a Kable [Peripheral] for a specific GATT service. */
class KableBleService(private val peripheral: Peripheral, private val serviceUuid: Uuid) : BleService {
    override fun hasCharacteristic(characteristic: BleCharacteristic): Boolean = peripheral.services.value?.any { svc ->
        svc.serviceUuid == serviceUuid && svc.characteristics.any { it.characteristicUuid == characteristic.uuid }
    } == true

    override fun observe(characteristic: BleCharacteristic) =
        peripheral.observe(characteristicOf(serviceUuid, characteristic.uuid))

    override fun observe(characteristic: BleCharacteristic, onSubscription: suspend () -> Unit) =
        peripheral.observe(characteristicOf(serviceUuid, characteristic.uuid), onSubscription)

    override suspend fun read(characteristic: BleCharacteristic): ByteArray =
        peripheral.read(characteristicOf(serviceUuid, characteristic.uuid))

    override fun preferredWriteType(characteristic: BleCharacteristic): BleWriteType {
        val service = peripheral.services.value?.find { it.serviceUuid == serviceUuid }
        val char = service?.characteristics?.find { it.characteristicUuid == characteristic.uuid }
        return if (char?.properties?.writeWithoutResponse == true) {
            BleWriteType.WITHOUT_RESPONSE
        } else {
            BleWriteType.WITH_RESPONSE
        }
    }

    override suspend fun write(characteristic: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
        peripheral.write(
            characteristicOf(serviceUuid, characteristic.uuid),
            data,
            when (writeType) {
                BleWriteType.WITH_RESPONSE -> WriteType.WithResponse
                BleWriteType.WITHOUT_RESPONSE -> WriteType.WithoutResponse
            },
        )
    }
}

/**
 * [BleConnection] implementation using Kable for cross-platform BLE communication.
 *
 * Manages peripheral lifecycle, connection state tracking, and GATT service profile access.
 *
 * Connection attempts follow Kable's recommended pattern from the SensorTag sample: try a direct connect first, then
 * fall back to `autoConnect = true` on failure. Only two attempts are made per [connect] call — the caller
 * ([BleRadioTransport]) owns the macro-level retry/backoff loop.
 */
class KableBleConnection(private val scope: CoroutineScope, private val loggingConfig: BleLoggingConfig) :
    BleConnection {

    @Volatile private var peripheral: Peripheral? = null

    @Volatile private var stateJob: Job? = null

    @Volatile private var connectionScope: CoroutineScope? = null

    companion object {
        /** Settle delay between a direct connect failure and the autoConnect fallback attempt. */
        private val AUTOCONNECT_FALLBACK_DELAY = 1.seconds
    }

    private val _deviceFlow = MutableStateFlow<BleDevice?>(null)
    override val deviceFlow: StateFlow<BleDevice?> = _deviceFlow.asStateFlow()

    override val device: BleDevice?
        get() = _deviceFlow.value

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected(DisconnectReason.Unknown))
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun connect(device: BleDevice) {
        val meshtasticDevice = device as? MeshtasticBleDevice ?: error("Unsupported BleDevice type: ${device::class}")
        var autoConnect = meshtasticDevice.advertisement == null

        /** Applies logging, observation exception handling, and platform config shared by both peripheral types. */
        fun PeripheralBuilder.commonConfig() {
            logging { applyConfig(loggingConfig, identifier = device.address) }
            observationExceptionHandler { cause ->
                Logger.w(cause) { "[${device.address}] Observation failure suppressed" }
            }
            platformConfig(device) { autoConnect }
        }

        val p =
            meshtasticDevice.advertisement?.let { adv -> Peripheral(adv) { commonConfig() } }
                ?: createPeripheral(device.address) { commonConfig() }

        // Install ownership of the new peripheral atomically. Cancellation between
        // peripheral construction and field assignment would strand `p` (Kable allocates
        // a per-peripheral scope + Bluetooth-state observer eagerly), so the cleanup,
        // assignment, and ActiveBleConnection update must complete as a single unit.
        // _deviceFlow.emit() is intentionally outside this block — making it
        // non-cancellable could hang teardown on a slow collector.
        withContext(NonCancellable) {
            cleanUpPeripheral(device.address)
            peripheral = p
            ActiveBleConnection.active = ActiveConnection(p, device.address)
        }

        _deviceFlow.emit(device)

        stateJob?.cancel()
        var hasStartedConnecting = false
        stateJob =
            p.state
                .onEach { kableState ->
                    val mappedState = kableState.toBleConnectionState(hasStartedConnecting) ?: return@onEach
                    if (kableState is State.Connecting || kableState is State.Connected) {
                        hasStartedConnecting = true
                    }

                    meshtasticDevice.updateState(mappedState)

                    _connectionState.emit(mappedState)
                }
                .launchIn(scope)

        while (p.state.value !is State.Connected) {
            autoConnect =
                try {
                    connectionScope?.let { oldScope ->
                        Logger.d { "[${device.address}] Cancelling previous connectionScope before reconnect" }
                        oldScope.coroutineContext.job.cancel()
                    }
                    connectionScope = p.connect()
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                    if (autoConnect) {
                        // Already on the autoConnect path and still failing: surface a clear Disconnected
                        // and let the outer reconnect loop (BleRadioTransport) own the macro retry budget.
                        Logger.w {
                            "[${device.address}] autoConnect attempt also failed; deferring to outer reconnect loop"
                        }
                        _connectionState.emit(BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed))
                        throw e
                    }
                    Logger.d { "[${device.address}] Direct connect failed, falling back to autoConnect" }
                    delay(AUTOCONNECT_FALLBACK_DELAY)
                    true
                }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState = try {
        withTimeout(timeout) {
            connect(device)
            BleConnectionState.Connected
        }
    } catch (_: TimeoutCancellationException) {
        // Our own timeout expired — treat as a failed attempt so callers can retry.
        BleConnectionState.Disconnected(DisconnectReason.Timeout)
    } catch (e: CancellationException) {
        // External cancellation (scope closed) — must propagate.
        throw e
    } catch (_: Exception) {
        BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed)
    }

    override suspend fun disconnect() = withContext(NonCancellable) {
        // Emit Disconnected before cancelling stateJob so downstream collectors see the
        // state transition. If we cancel stateJob first, the peripheral's state flow
        // emission of Disconnected is never forwarded to _connectionState.
        _connectionState.emit(BleConnectionState.Disconnected(DisconnectReason.LocalDisconnect))

        stateJob?.cancel()
        stateJob = null

        // Capture the peripheral we own before clearing it so we can identity-check
        // ActiveBleConnection below. A stale disconnect from an earlier connection
        // attempt's exception handler must not clobber a newer connection that has
        // already installed itself as active.
        val owned = peripheral
        safeClosePeripheral("disconnect")
        peripheral = null
        connectionScope = null

        if (owned != null && ActiveBleConnection.active?.peripheral === owned) {
            ActiveBleConnection.active = null
        }

        _deviceFlow.emit(null)
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        val p = peripheral ?: error("Not connected")
        val cScope = connectionScope ?: error("No active connection scope")
        val service = KableBleService(p, serviceUuid)
        return withTimeout(timeout) { cScope.setup(service) }
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? = peripheral?.negotiatedMaxWriteLength()

    override fun requestHighConnectionPriority(): Boolean = peripheral?.requestHighConnectionPriority() == true

    /** Ensures the previous peripheral's GATT resources are fully released. */
    private suspend fun cleanUpPeripheral(tag: String) {
        withContext(NonCancellable) { safeClosePeripheral(tag) }
    }

    /**
     * Safely disconnects and closes the current [peripheral], logging any failures.
     *
     * Kable requires `close()` to release broadcast receivers on Android (Kable issue #359). Separate try/catch blocks
     * ensure `close()` always runs even if `disconnect()` throws.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun safeClosePeripheral(tag: String) {
        try {
            peripheral?.disconnect()
        } catch (_: NotConnectedException) {
            // Silence "Disconnect requested" which Kable throws if already disconnected.
            // This is a common non-fatal reported in Crashlytics that is safe to ignore here.
        } catch (e: Exception) {
            Logger.w(e) { "[$tag] Failed to disconnect peripheral" }
        }
        try {
            peripheral?.close()
        } catch (e: Exception) {
            Logger.w(e) { "[$tag] Failed to close peripheral" }
        }
    }
}
