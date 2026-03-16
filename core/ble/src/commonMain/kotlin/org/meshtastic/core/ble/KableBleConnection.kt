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

import com.juul.kable.Peripheral
import com.juul.kable.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.uuid.Uuid

class KableBleService(val peripheral: Peripheral) : BleService

@Suppress("UnusedPrivateProperty")
class KableBleConnection(private val scope: CoroutineScope, private val tag: String) : BleConnection {

    private var peripheral: Peripheral? = null
    private var stateJob: Job? = null
    private var connectionScope: CoroutineScope? = null

    private val _deviceFlow = MutableSharedFlow<BleDevice?>(replay = 1)
    override val deviceFlow: SharedFlow<BleDevice?> = _deviceFlow.asSharedFlow()

    override val device: BleDevice?
        get() = _deviceFlow.replayCache.firstOrNull()

    private val _connectionState =
        MutableSharedFlow<BleConnectionState>(
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    override val connectionState: SharedFlow<BleConnectionState> = _connectionState.asSharedFlow()

    override suspend fun connect(device: BleDevice) {
        val autoConnect = MutableStateFlow(device is DirectBleDevice)

        val p = when (device) {
            is KableBleDevice -> Peripheral(device.advertisement) {
                observationExceptionHandler { cause ->
                    co.touchlab.kermit.Logger.w(cause) { "[${device.address}] Observation failure suppressed" }
                }
                platformConfig(device) { autoConnect.value }
            }
            is DirectBleDevice -> createPeripheral(device.address) {
                observationExceptionHandler { cause ->
                    co.touchlab.kermit.Logger.w(cause) { "[${device.address}] Observation failure suppressed" }
                }
                platformConfig(device) { autoConnect.value }
            }
            else -> error("Unsupported BleDevice type: ${device::class}")
        }

        peripheral?.disconnect()
        peripheral?.close()
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

        while (p.state.value !is State.Connected) {
            autoConnect.value = try {
                connectionScope = p.connect()
                false
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                true
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun connectAndAwait(
        device: BleDevice,
        timeoutMs: Long,
        onRegister: suspend () -> Unit,
    ): BleConnectionState {
        onRegister()
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                connect(device)
                BleConnectionState.Connected
            }
        } catch (e: Exception) {
            BleConnectionState.Disconnected
        }
    }

    override suspend fun disconnect() = withContext(NonCancellable) {
        stateJob?.cancel()
        stateJob = null
        peripheral?.disconnect()
        peripheral?.close()
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
        val service = KableBleService(p)
        return cScope.setup(service)
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? {
        // Desktop MTU isn't always easily exposed, provide a safe default for Meshtastic
        return 512
    }
}
