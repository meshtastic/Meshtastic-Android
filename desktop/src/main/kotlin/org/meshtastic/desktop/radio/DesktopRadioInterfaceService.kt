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
package org.meshtastic.desktop.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.transport.TcpTransport
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs

/**
 * Desktop implementation of [RadioInterfaceService] with real TCP transport.
 *
 * Delegates all TCP socket management, stream framing, reconnect logic, and heartbeat to the shared [TcpTransport] from
 * `core:network`. Desktop supports TCP and BLE connections.
 */
@Suppress("TooManyFunctions")
class DesktopRadioInterfaceService(
    private val dispatchers: CoroutineDispatchers,
    private val radioPrefs: RadioPrefs,
    private val scanner: org.meshtastic.core.ble.BleScanner,
    private val bluetoothRepository: org.meshtastic.core.ble.BluetoothRepository,
    private val connectionFactory: org.meshtastic.core.ble.BleConnectionFactory,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<org.meshtastic.core.model.DeviceType> =
        listOf(
            org.meshtastic.core.model.DeviceType.TCP,
            org.meshtastic.core.model.DeviceType.BLE,
            org.meshtastic.core.model.DeviceType.USB,
        )

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val receivedData: SharedFlow<ByteArray> = _receivedData

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val meshActivity: SharedFlow<MeshActivity> = _meshActivity.asSharedFlow()

    override var serviceScope: CoroutineScope = CoroutineScope(dispatchers.io + SupervisorJob())
        private set

    private var transport: TcpTransport? = null
    private var bleTransport: DesktopBleInterface? = null
    private var serialTransport: org.meshtastic.core.network.SerialTransport? = null

    init {
        // Observe radioPrefs to handle asynchronous loads from DataStore
        radioPrefs.devAddr
            .onEach { addr ->
                if (_currentDeviceAddressFlow.value != addr) {
                    _currentDeviceAddressFlow.value = addr
                }
                // Auto-connect if we have a valid address and are disconnected
                if (addr != null && _connectionState.value == ConnectionState.Disconnected) {
                    Logger.i { "DesktopRadio: Auto-connecting to saved address ${addr.anonymize}" }
                    startConnection(addr)
                }
            }
            .launchIn(serviceScope)
    }

    override fun isMockInterface(): Boolean = false

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    // region RadioInterfaceService Implementation

    override fun connect() {
        val address = getDeviceAddress()
        if (address.isNullOrBlank() || address == "n") {
            Logger.w { "DesktopRadio: No address configured, skipping connect" }
            return
        }
        startConnection(address)
    }

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val sanitized = if (deviceAddr == "n" || deviceAddr.isNullOrBlank()) null else deviceAddr

        if (_currentDeviceAddressFlow.value == sanitized && _connectionState.value == ConnectionState.Connected) {
            Logger.w { "DesktopRadio: Already connected to ${sanitized?.anonymize}, ignoring" }
            return false
        }

        Logger.i { "DesktopRadio: Setting device address to ${sanitized?.anonymize}" }

        // Stop any existing connection
        stopInterface()

        // Persist and update address
        radioPrefs.setDevAddr(sanitized)
        _currentDeviceAddressFlow.value = sanitized

        // Start connection if we have a valid address
        if (sanitized != null && sanitized != "n") {
            startConnection(sanitized)
        }
        return true
    }

    override fun sendToRadio(bytes: ByteArray) {
        serviceScope.handledLaunch {
            transport?.sendPacket(bytes)
            bleTransport?.handleSendToRadio(bytes)
            serialTransport?.handleSendToRadio(bytes)
        }
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    override fun onConnect() {
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.i { "DesktopRadio: Connected" }
            _connectionState.value = ConnectionState.Connected
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        val newState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newState) {
            Logger.i { "DesktopRadio: Disconnected (permanent=$isPermanent, error=$errorMessage)" }
            _connectionState.value = newState
        }
    }

    override fun handleFromRadio(bytes: ByteArray) {
        serviceScope.launch(dispatchers.io) {
            _receivedData.emit(bytes)
            _meshActivity.tryEmit(MeshActivity.Receive)
        }
    }

    // endregion

    // region Connection Management

    private fun startConnection(address: String) {
        if (address.startsWith("t")) {
            startTcpConnection(address.removePrefix("t"))
        } else if (address.startsWith("s")) {
            startSerialConnection(address.removePrefix("s"))
        } else if (address.startsWith("x")) {
            startBleConnection(address.removePrefix("x"))
        } else {
            // Assume BLE if no prefix, or prefix is not supported
            val stripped = if (address.startsWith("!")) address.removePrefix("!") else address
            startBleConnection(stripped)
        }
    }

    private fun startSerialConnection(portName: String) {
        transport?.stop()
        bleTransport?.close()
        serialTransport?.close()

        val serial = org.meshtastic.core.network.SerialTransport(portName = portName, service = this)
        serialTransport = serial
        if (!serial.startConnection()) {
            onDisconnect(isPermanent = true, errorMessage = "Failed to connect to $portName")
        }
    }

    private fun startBleConnection(address: String) {
        transport?.stop()
        bleTransport?.close()

        bleTransport =
            DesktopBleInterface(
                serviceScope = serviceScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                service = this,
                address = address,
            )
    }

    private fun startTcpConnection(address: String) {
        transport?.stop()

        val tcpTransport =
            TcpTransport(
                dispatchers = dispatchers,
                scope = serviceScope,
                listener =
                object : TcpTransport.Listener {
                    override fun onConnected() {
                        onConnect()
                    }

                    override fun onDisconnected() {
                        onDisconnect(isPermanent = true)
                    }

                    override fun onPacketReceived(bytes: ByteArray) {
                        handleFromRadio(bytes)
                    }
                },
                logTag = "DesktopRadio",
            )
        transport = tcpTransport
        tcpTransport.start(address)
    }

    private fun stopInterface() {
        transport?.stop()
        transport = null

        bleTransport?.close()
        bleTransport = null

        serialTransport?.close()
        serialTransport = null

        // Recreate the service scope
        serviceScope.cancel("stopping interface")
        serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    }

    // endregion
}
