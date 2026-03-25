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
package org.meshtastic.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import kotlin.time.Duration
import kotlin.uuid.Uuid

class FakeBleDevice(
    override val address: String,
    override val name: String? = "Fake Device",
    initialState: BleConnectionState = BleConnectionState.Disconnected,
) : BleDevice, BaseFake() {
    private val _state = mutableStateFlow(initialState)
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private val _isBonded = mutableStateFlow(false)
    override val isBonded: Boolean get() = _isBonded.value

    override val isConnected: Boolean get() = _state.value == BleConnectionState.Connected

    override suspend fun readRssi(): Int = -60

    override suspend fun bond() {
        _isBonded.value = true
    }

    fun setState(newState: BleConnectionState) {
        _state.value = newState
    }
}

class FakeBleScanner : BleScanner, BaseFake() {
    private val _foundDevices = mutableSharedFlow<BleDevice>(replay = 10)

    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flow {
        emitAll(_foundDevices)
    }

    fun emitDevice(device: BleDevice) {
        _foundDevices.tryEmit(device)
    }
}

class FakeBleConnection : BleConnection, BaseFake() {
    private val _device = mutableStateFlow<BleDevice?>(null)
    override val device: BleDevice? get() = _device.value

    private val _deviceFlow = mutableSharedFlow<BleDevice?>(replay = 1)
    override val deviceFlow: SharedFlow<BleDevice?> = _deviceFlow.asSharedFlow()

    private val _connectionState = mutableSharedFlow<BleConnectionState>(replay = 1)
    override val connectionState: SharedFlow<BleConnectionState> = _connectionState.asSharedFlow()

    override suspend fun connect(device: BleDevice) {
        _device.value = device
        _deviceFlow.emit(device)
        _connectionState.emit(BleConnectionState.Connecting)
        if (device is FakeBleDevice) {
            device.setState(BleConnectionState.Connecting)
        }
        _connectionState.emit(BleConnectionState.Connected)
        if (device is FakeBleDevice) {
            device.setState(BleConnectionState.Connected)
        }
    }

    override suspend fun connectAndAwait(
        device: BleDevice,
        timeoutMs: Long,
        onRegister: suspend () -> Unit,
    ): BleConnectionState {
        connect(device)
        onRegister()
        return BleConnectionState.Connected
    }

    override suspend fun disconnect() {
        val currentDevice = _device.value
        _connectionState.emit(BleConnectionState.Disconnected)
        if (currentDevice is FakeBleDevice) {
            currentDevice.setState(BleConnectionState.Disconnected)
        }
        _device.value = null
        _deviceFlow.emit(null)
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        return CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).setup(FakeBleService())
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int = 512
}

class FakeBleService : BleService

class FakeBleConnectionFactory(private val fakeConnection: FakeBleConnection = FakeBleConnection()) : BleConnectionFactory {
    override fun create(scope: CoroutineScope, tag: String): BleConnection = fakeConnection
}

class FakeBluetoothRepository : BluetoothRepository, BaseFake() {
    private val _state = mutableStateFlow(BluetoothState(hasPermissions = true, enabled = true))
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    override fun refreshState() {}

    override fun isValid(bleAddress: String): Boolean = bleAddress.isNotBlank()

    override fun isBonded(address: String): Boolean = _state.value.bondedDevices.any { it.address == address }

    override suspend fun bond(device: BleDevice) {
        val currentState = _state.value
        if (!currentState.bondedDevices.contains(device)) {
            _state.value = currentState.copy(bondedDevices = currentState.bondedDevices + device)
        }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setHasPermissions(hasPermissions: Boolean) {
        _state.value = _state.value.copy(hasPermissions = hasPermissions)
    }
}
