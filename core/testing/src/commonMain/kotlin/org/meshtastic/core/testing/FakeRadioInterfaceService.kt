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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.repository.RadioInterfaceService

/**
 * A test double for [RadioInterfaceService] that provides an in-memory implementation.
 *
 * The [connectionState] here mirrors the transport-level semantics of the real implementation. In production, only
 * [MeshConnectionManager][org.meshtastic.core.repository.MeshConnectionManager] observes this flow; tests should verify
 * that bridging behavior rather than consuming it directly from UI/feature test code (use
 * [FakeServiceRepository.connectionState] instead).
 */
@Suppress("TooManyFunctions")
class FakeRadioInterfaceService(override val serviceScope: CoroutineScope = MainScope()) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType> = emptyList()

    /** Transport-level connection state (raw hardware link status). */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(null)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow

    // Use an unbounded Channel to mirror SharedRadioInterfaceService semantics. A MutableSharedFlow would
    // hide the stop/start backlog bug that motivated the resetReceivedBuffer() API.
    private val _receivedData = Channel<ByteArray>(Channel.UNLIMITED)
    override val receivedData: Flow<ByteArray> = _receivedData.receiveAsFlow()

    private val _meshActivity = MutableSharedFlow<MeshActivity>()
    override val meshActivity: SharedFlow<MeshActivity> = _meshActivity

    private val _connectionError = MutableSharedFlow<String>()
    override val connectionError: SharedFlow<String> = _connectionError

    val sentToRadio = mutableListOf<ByteArray>()
    var connectCalled = false

    override fun isMockTransport(): Boolean = true

    override fun sendToRadio(bytes: ByteArray) {
        sentToRadio.add(bytes)
    }

    override fun connect() {
        connectCalled = true
    }

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        _currentDeviceAddressFlow.value = deviceAddr
        return true
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "$interfaceId:$rest"

    override fun onConnect() {
        _connectionState.value = ConnectionState.Connected
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun handleFromRadio(bytes: ByteArray) {
        _receivedData.trySend(bytes)
    }

    override fun resetReceivedBuffer() {
        @Suppress("EmptyWhileBlock", "ControlFlowWithEmptyBody")
        while (_receivedData.tryReceive().isSuccess) Unit
    }

    // --- Helper methods for testing ---

    fun emitFromRadio(bytes: ByteArray) {
        _receivedData.trySend(bytes)
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
