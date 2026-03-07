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
package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.common.core.simpleSharedFlow
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import kotlin.uuid.Uuid

/**
 * An Android implementation of [BleConnection] using Nordic's [CentralManager].
 *
 * @param centralManager The Nordic [CentralManager] to use for connection.
 * @param scope The [CoroutineScope] in which to monitor connection state.
 * @param tag A tag for logging.
 */
class AndroidBleConnection(
    private val centralManager: CentralManager,
    private val scope: CoroutineScope,
    private val tag: String = "BLE",
) : BleConnection {

    private var _device: AndroidBleDevice? = null
    override val device: BleDevice?
        get() = _device

    private val _deviceFlow = MutableSharedFlow<BleDevice?>(replay = 1)
    override val deviceFlow: SharedFlow<BleDevice?> = _deviceFlow.asSharedFlow()

    private val _connectionState = simpleSharedFlow<BleConnectionState>()
    override val connectionState: SharedFlow<BleConnectionState> = _connectionState.asSharedFlow()

    private var stateJob: Job? = null
    private var profileJob: Job? = null

    override suspend fun connect(device: BleDevice) = withContext(NonCancellable) {
        val androidDevice = device as AndroidBleDevice
        stateJob?.cancel()
        _device = androidDevice
        _deviceFlow.emit(androidDevice)

        centralManager.connect(
            peripheral = androidDevice.peripheral,
            options = CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
        )

        stateJob =
            androidDevice.peripheral.state
                .onEach { state ->
                    Logger.d { "[$tag] Connection state changed to $state" }
                    val commonState =
                        when (state) {
                            is ConnectionState.Connecting -> BleConnectionState.Connecting
                            is ConnectionState.Connected -> BleConnectionState.Connected
                            is ConnectionState.Disconnecting -> BleConnectionState.Disconnecting
                            is ConnectionState.Disconnected -> BleConnectionState.Disconnected
                        }

                    if (state is ConnectionState.Connected) {
                        androidDevice.peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
                        observePeripheralDetails(androidDevice)
                    }

                    androidDevice.updateState(state)
                    _connectionState.emit(commonState)
                }
                .launchIn(scope)
    }

    override suspend fun connectAndAwait(
        device: BleDevice,
        timeoutMs: Long,
        onRegister: suspend () -> Unit,
    ): BleConnectionState {
        onRegister()
        connect(device)
        return withTimeout(timeoutMs) {
            connectionState.first { it is BleConnectionState.Connected || it is BleConnectionState.Disconnected }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observePeripheralDetails(androidDevice: AndroidBleDevice) {
        val p = androidDevice.peripheral
        p.phy.onEach { phy -> Logger.i { "[$tag] BLE PHY changed to $phy" } }.launchIn(scope)

        p.connectionParameters
            .onEach { params ->
                Logger.i { "[$tag] BLE connection parameters changed to $params" }
                try {
                    val maxWriteLen = p.maximumWriteValueLength(WriteType.WITHOUT_RESPONSE)
                    Logger.i { "[$tag] Negotiated MTU (Write): $maxWriteLen bytes" }
                } catch (e: Exception) {
                    Logger.d { "[$tag] Could not read MTU: ${e.message}" }
                }
            }
            .launchIn(scope)
    }

    override suspend fun disconnect() = withContext(NonCancellable) {
        stateJob?.cancel()
        stateJob = null
        profileJob?.cancel()
        profileJob = null
        _device?.peripheral?.disconnect()
        _device = null
        _deviceFlow.emit(null)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: kotlin.time.Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        val androidDevice = deviceFlow.first { it != null } as AndroidBleDevice
        val p = androidDevice.peripheral
        val serviceReady = CompletableDeferred<T>()

        profileJob?.cancel()
        val job =
            scope.launch {
                try {
                    val profileScope = this
                    p.profile(serviceUuid = serviceUuid, required = true, scope = profileScope) { service ->
                        try {
                            val result = setup(AndroidBleService(service))
                            serviceReady.complete(result)
                            awaitCancellation()
                        } catch (e: Throwable) {
                            if (!serviceReady.isCompleted) serviceReady.completeExceptionally(e)
                            throw e
                        }
                    }
                } catch (e: Throwable) {
                    if (!serviceReady.isCompleted) serviceReady.completeExceptionally(e)
                }
            }
        profileJob = job

        return try {
            withTimeout(timeout) { serviceReady.await() }
        } catch (e: Throwable) {
            profileJob?.cancel()
            throw e
        }
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? {
        val nordicWriteType =
            when (writeType) {
                BleWriteType.WITH_RESPONSE -> WriteType.WITH_RESPONSE
                BleWriteType.WITHOUT_RESPONSE -> WriteType.WITHOUT_RESPONSE
            }
        return _device?.peripheral?.maximumWriteValueLength(nordicWriteType)
    }

    /** Requests a new connection priority for the current peripheral. */
    suspend fun requestConnectionPriority(priority: ConnectionPriority) {
        _device?.peripheral?.requestConnectionPriority(priority)
    }
}
