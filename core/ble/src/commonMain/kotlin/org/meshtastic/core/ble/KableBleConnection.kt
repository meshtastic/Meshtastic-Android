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
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
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
        val p = when (device) {
            is KableBleDevice -> Peripheral(device.advertisement) { platformConfig(device) }
            is DirectBleDevice -> createPeripheral(device.address) { platformConfig(device) }
            else -> error("Unsupported BleDevice type: ${device::class}")
        }
        
        peripheral?.disconnect()
        peripheral?.close()
        peripheral = p
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

        p.connect()
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
        _deviceFlow.emit(null)
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        val p = peripheral ?: error("Not connected")
        val service = KableBleService(p)
        return scope.setup(service)
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? {
        // Desktop MTU isn't always easily exposed, provide a safe default for Meshtastic
        return 512
    }
}
