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
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** [BleService] implementation backed by a Kable [Peripheral] for a specific GATT service. */
class KableBleService(private val peripheral: Peripheral, private val serviceUuid: Uuid) : BleService {
    override fun hasCharacteristic(characteristic: BleCharacteristic): Boolean = peripheral.services.value?.any { svc ->
        svc.serviceUuid == serviceUuid && svc.characteristics.any { it.characteristicUuid == characteristic.uuid }
    } == true

    override fun observe(characteristic: BleCharacteristic) =
        peripheral.observe(characteristicOf(serviceUuid, characteristic.uuid))

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
 * ([BleRadioInterface]) owns the macro-level retry/backoff loop.
 */
class KableBleConnection(private val scope: CoroutineScope) : BleConnection {

    private var peripheral: Peripheral? = null
    private var stateJob: Job? = null
    private var connectionScope: CoroutineScope? = null

    companion object {
        /** Settle delay between a direct connect failure and the autoConnect fallback attempt. */
        private const val AUTOCONNECT_FALLBACK_DELAY_MS = 1000L
    }

    private val _deviceFlow = MutableSharedFlow<BleDevice?>(replay = 1)
    override val deviceFlow: SharedFlow<BleDevice?> = _deviceFlow.asSharedFlow()

    override val device: BleDevice?
        get() = _deviceFlow.replayCache.firstOrNull()

    private val _connectionState =
        MutableSharedFlow<BleConnectionState>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val connectionState: SharedFlow<BleConnectionState> = _connectionState.asSharedFlow()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun connect(device: BleDevice) {
        val autoConnect = MutableStateFlow(device is DirectBleDevice)

        val p =
            when (device) {
                is KableBleDevice ->
                    Peripheral(device.advertisement) {
                        logging {
                            engine = KermitLogEngine
                            level = Logging.Level.Events
                            identifier = device.address
                        }
                        observationExceptionHandler { cause ->
                            Logger.w(cause) { "[${device.address}] Observation failure suppressed" }
                        }
                        platformConfig(device) { autoConnect.value }
                    }
                is DirectBleDevice ->
                    createPeripheral(device.address) {
                        logging {
                            engine = KermitLogEngine
                            level = Logging.Level.Events
                            identifier = device.address
                        }
                        observationExceptionHandler { cause ->
                            Logger.w(cause) { "[${device.address}] Observation failure suppressed" }
                        }
                        platformConfig(device) { autoConnect.value }
                    }
                else -> error("Unsupported BleDevice type: ${device::class}")
            }

        // Clean up previous peripheral under NonCancellable to prevent GATT resource leaks
        // if the calling coroutine is cancelled during teardown.
        cleanUpPeripheral(device.address)
        peripheral = p

        ActiveBleConnection.activePeripheral = p
        ActiveBleConnection.activeAddress = device.address

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

                    when (device) {
                        is KableBleDevice -> device.updateState(mappedState)
                        is DirectBleDevice -> device.updateState(mappedState)
                    }

                    _connectionState.emit(mappedState)
                }
                .launchIn(scope)

        // Kable SensorTag pattern: try direct connect, fall back to autoConnect on failure.
        // Only two attempts — the outer loop in BleRadioInterface owns retries and backoff.
        while (p.state.value !is State.Connected) {
            autoConnect.value =
                try {
                    // Cancel any previous connectionScope to avoid leaking the old coroutine scope.
                    connectionScope?.let { oldScope ->
                        Logger.d { "[${device.address}] Cancelling previous connectionScope before reconnect" }
                        oldScope.coroutineContext.job.cancel()
                    }
                    connectionScope = p.connect()
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                    if (autoConnect.value) {
                        // autoConnect already true and still failed — don't loop forever.
                        Logger.w { "[${device.address}] autoConnect attempt failed, giving up" }
                        _connectionState.emit(BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed))
                        return
                    }
                    Logger.d { "[${device.address}] Direct connect failed, falling back to autoConnect" }
                    delay(AUTOCONNECT_FALLBACK_DELAY_MS)
                    true
                }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun connectAndAwait(device: BleDevice, timeoutMs: Long): BleConnectionState = try {
        withTimeout(timeoutMs) {
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

        // Separate try/catch blocks ensure close() is always called even if disconnect()
        // throws. Kable requires close() to release broadcast receivers on Android (Kable
        // issue #359: "Too many receivers"). Skipping it leaks native resources.
        @Suppress("TooGenericExceptionCaught")
        try {
            peripheral?.disconnect()
        } catch (e: Exception) {
            Logger.w(e) { "Failed to disconnect peripheral" }
        }
        @Suppress("TooGenericExceptionCaught")
        try {
            peripheral?.close()
        } catch (e: Exception) {
            Logger.w(e) { "Failed to close peripheral" }
        }
        peripheral = null
        connectionScope = null

        ActiveBleConnection.activePeripheral = null
        ActiveBleConnection.activeAddress = null

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

    /** Ensures the previous peripheral's GATT resources are fully released. */
    private suspend fun cleanUpPeripheral(tag: String) {
        withContext(NonCancellable) {
            @Suppress("TooGenericExceptionCaught")
            try {
                peripheral?.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "[$tag] Failed to disconnect previous peripheral" }
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                peripheral?.close()
            } catch (e: Exception) {
                Logger.w(e) { "[$tag] Failed to close previous peripheral" }
            }
        }
    }
}
