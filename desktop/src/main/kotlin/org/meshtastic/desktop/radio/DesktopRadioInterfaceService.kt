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
 * `core:network`. Desktop only supports TCP connections (no BLE/USB/Serial).
 */
@Suppress("TooManyFunctions")
class DesktopRadioInterfaceService(private val dispatchers: CoroutineDispatchers, private val radioPrefs: RadioPrefs) :
    RadioInterfaceService {

    override val supportedDeviceTypes: List<org.meshtastic.core.model.DeviceType> =
        listOf(org.meshtastic.core.model.DeviceType.TCP)

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

    init {
        // Observe radioPrefs to handle asynchronous loads from DataStore
        radioPrefs.devAddr
            .onEach { addr ->
                if (_currentDeviceAddressFlow.value != addr) {
                    _currentDeviceAddressFlow.value = addr
                }
                // Auto-connect if we have a valid TCP address and are disconnected
                if (addr != null && addr.startsWith("t") && _connectionState.value == ConnectionState.Disconnected) {
                    Logger.i { "DesktopRadio: Auto-connecting to saved address ${addr.anonymize}" }
                    startTcpConnection(addr.removePrefix("t"))
                }
            }
            .launchIn(serviceScope)
    }

    override fun isMockInterface(): Boolean = false

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    // region RadioInterfaceService Implementation

    override fun connect() {
        val address = getDeviceAddress()
        if (address == null || !address.startsWith("t")) {
            Logger.w { "DesktopRadio: No TCP address configured, skipping connect" }
            return
        }
        startTcpConnection(address.removePrefix("t"))
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

        // Start connection if we have a TCP address
        if (sanitized != null && sanitized.startsWith("t")) {
            startTcpConnection(sanitized.removePrefix("t"))
        }
        return true
    }

    override fun sendToRadio(bytes: ByteArray) {
        serviceScope.handledLaunch { transport?.sendPacket(bytes) }
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

    // region TCP Connection Management

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

        // Recreate the service scope
        serviceScope.cancel("stopping interface")
        serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    }

    // endregion
}
