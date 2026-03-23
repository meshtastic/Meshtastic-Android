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
package org.meshtastic.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ignoreException
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * Shared multiplatform connection orchestrator for Meshtastic radios.
 *
 * Manages the connection lifecycle (connect, active, disconnect, reconnect loop), device address state flows, and
 * hardware state observability (BLE/Network toggles). Delegates the actual raw byte transport mapping to a
 * platform-specific [RadioTransportFactory].
 */
@Suppress("LongParameterList", "TooManyFunctions")
@Single
class SharedRadioInterfaceService(
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val radioPrefs: RadioPrefs,
    private val transportFactory: RadioTransportFactory,
    private val analytics: PlatformAnalytics,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType>
        get() = transportFactory.supportedDeviceTypes

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val receivedData: SharedFlow<ByteArray> = _receivedData

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(
            extraBufferCapacity = 64,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    override val meshActivity: SharedFlow<MeshActivity> = _meshActivity.asSharedFlow()

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val connectionError: SharedFlow<String> = _connectionError.asSharedFlow()

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    private var radioIf: RadioTransport? = null
    private var isStarted = false

    private val listenersInitialized = kotlinx.atomicfu.atomic(false)
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var lastHeartbeatMillis = 0L

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 30 * 1000L
    }

    private val initLock = Mutex()

    private fun initStateListeners() {
        if (listenersInitialized.value) return
        processLifecycle.coroutineScope.launch {
            initLock.withLock {
                if (listenersInitialized.value) return@withLock
                listenersInitialized.value = true

                radioPrefs.devAddr
                    .onEach { addr ->
                        if (_currentDeviceAddressFlow.value != addr) {
                            _currentDeviceAddressFlow.value = addr
                            startInterface()
                        }
                    }
                    .launchIn(processLifecycle.coroutineScope)

                bluetoothRepository.state
                    .onEach { state ->
                        if (state.enabled) {
                            startInterface()
                        } else if (getBondedDeviceAddress()?.startsWith(InterfaceId.BLUETOOTH.id) == true) {
                            stopInterface()
                        }
                    }
                    .catch { Logger.e(it) { "bluetoothRepository.state flow crashed!" } }
                    .launchIn(processLifecycle.coroutineScope)

                networkRepository.networkAvailable
                    .onEach { state ->
                        if (state) {
                            startInterface()
                        } else if (getBondedDeviceAddress()?.startsWith(InterfaceId.TCP.id) == true) {
                            stopInterface()
                        }
                    }
                    .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed!" } }
                    .launchIn(processLifecycle.coroutineScope)
            }
        }
    }

    override fun connect() {
        startInterface()
        initStateListeners()
    }

    override fun isMockInterface(): Boolean = transportFactory.isMockInterface()

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        transportFactory.toInterfaceAddress(interfaceId, rest)

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    private fun getBondedDeviceAddress(): String? {
        val address = getDeviceAddress()
        return if (transportFactory.isAddressValid(address)) {
            address
        } else {
            null
        }
    }

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val sanitized = if (deviceAddr == "n" || deviceAddr.isNullOrBlank()) null else deviceAddr

        if (getBondedDeviceAddress() == sanitized && isStarted && _connectionState.value == ConnectionState.Connected) {
            Logger.w { "Ignoring setBondedDevice ${sanitized?.anonymize}, already using that device" }
            return false
        }

        analytics.track("mesh_bond")
        ignoreException { stopInterface() }

        Logger.d { "Setting bonded device to ${sanitized?.anonymize}" }
        radioPrefs.setDevAddr(sanitized)
        _currentDeviceAddressFlow.value = sanitized
        startInterface()
        return true
    }

    private fun startInterface() {
        if (radioIf != null) return

        val address =
            getBondedDeviceAddress()
                ?: if (isMockInterface()) transportFactory.toInterfaceAddress(InterfaceId.MOCK, "") else null

        if (address == null) {
            Logger.w { "No valid address to connect to." }
            return
        }

        Logger.i { "Starting radio interface for ${address.anonymize}" }
        isStarted = true
        radioIf = transportFactory.createTransport(address, this)
        startHeartbeat()
    }

    private fun stopInterface() {
        val currentIf = radioIf
        Logger.i { "Stopping interface $currentIf" }
        isStarted = false
        radioIf = null
        currentIf?.close()

        _serviceScope.cancel("stopping interface")
        _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

        if (currentIf != null) {
            onDisconnect(isPermanent = true)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                keepAlive()
            }
        }
    }

    fun keepAlive(now: Long = nowMillis) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            radioIf?.keepAlive()
            lastHeartbeatMillis = now
        }
    }

    override fun sendToRadio(bytes: ByteArray) {
        _serviceScope.handledLaunch {
            radioIf?.handleSendToRadio(bytes)
            _meshActivity.tryEmit(MeshActivity.Send)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleFromRadio(bytes: ByteArray) {
        try {
            processLifecycle.coroutineScope.launch(dispatchers.io) { _receivedData.emit(bytes) }
            _meshActivity.tryEmit(MeshActivity.Receive)
        } catch (t: Throwable) {
            Logger.e(t) { "RadioInterfaceService.handleFromRadio failed while emitting data" }
        }
    }

    override fun onConnect() {
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.d { "Broadcasting connection state change to Connected" }
            processLifecycle.coroutineScope.launch(dispatchers.default) {
                _connectionState.emit(ConnectionState.Connected)
            }
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        if (errorMessage != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionError.emit(errorMessage) }
        }
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newTargetState) {
            Logger.d { "Broadcasting connection state change to $newTargetState" }
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionState.emit(newTargetState) }
        }
    }
}
