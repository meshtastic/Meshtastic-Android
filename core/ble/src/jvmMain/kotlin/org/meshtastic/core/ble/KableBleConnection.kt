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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.uuid.Uuid

class KableBleService(val peripheral: Peripheral) : BleService

class KableBleConnection(private val scope: CoroutineScope, private val tag: String) : BleConnection {

    private var peripheral: Peripheral? = null

    private val _deviceFlow = MutableSharedFlow<BleDevice?>(replay = 1)
    override val deviceFlow: SharedFlow<BleDevice?> = _deviceFlow.asSharedFlow()

    override val device: BleDevice?
        get() = _deviceFlow.replayCache.firstOrNull()

    private val _connectionState = MutableSharedFlow<BleConnectionState>(replay = 1)
    override val connectionState: SharedFlow<BleConnectionState> = _connectionState.asSharedFlow()

    override suspend fun connect(device: BleDevice) {
        val kableDevice = device as KableBleDevice
        val p = Peripheral(kableDevice.advertisement)
        peripheral = p
        _deviceFlow.emit(device)

        p.state
            .onEach { kableState ->
                val mappedState =
                    when (kableState) {
                        is State.Connecting -> BleConnectionState.Connecting
                        is State.Connected -> BleConnectionState.Connected
                        is State.Disconnecting -> BleConnectionState.Disconnecting
                        is State.Disconnected -> BleConnectionState.Disconnected
                    }
                kableDevice.updateState(mappedState)
                _connectionState.emit(mappedState)
            }
            .launchIn(scope)

        p.connect()
    }

    override suspend fun connectAndAwait(
        device: BleDevice,
        timeoutMs: Long,
        onRegister: suspend () -> Unit,
    ): BleConnectionState {
        scope.launch { connect(device) }
        onRegister()
        // wait for connected or disconnected
        return connectionState.first { it is BleConnectionState.Connected || it is BleConnectionState.Disconnected }
    }

    override suspend fun disconnect() {
        peripheral?.disconnect()
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        val p = peripheral ?: throw IllegalStateException("Not connected")
        val service = KableBleService(p)
        return scope.setup(service)
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? {
        // Desktop MTU isn't always easily exposed, provide a safe default for Meshtastic
        return 512
    }
}
