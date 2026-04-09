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
import kotlin.concurrent.Volatile

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
    override val connectionError: SharedFlow<String> = _connectionError.asSharedFlow()

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    private var radioIf: RadioTransport? = null
    private var runningInterfaceId: InterfaceId? = null
    private var isStarted = false

    private val listenersInitialized = kotlinx.atomicfu.atomic(false)
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var lastHeartbeatMillis = 0L

    @Volatile private var lastDataReceivedMillis = 0L

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 30 * 1000L

        // If we haven't received any data from the radio within this window after sending a
        // heartbeat while the connection is nominally "Connected", the connection is likely a
        // zombie (BLE stack didn't report disconnect). Two missed heartbeat intervals gives
        // the firmware a reasonable window to respond or send telemetry.
        private const val LIVENESS_TIMEOUT_MILLIS = HEARTBEAT_INTERVAL_MILLIS * 2
    }

    private val initLock = Mutex()
    private val interfaceMutex = Mutex()

    private fun initStateListeners() {
        if (listenersInitialized.value) return
        processLifecycle.coroutineScope.launch {
            initLock.withLock {
                if (listenersInitialized.value) return@withLock
                listenersInitialized.value = true

                radioPrefs.devAddr
                    .onEach { addr ->
                        interfaceMutex.withLock {
                            if (_currentDeviceAddressFlow.value != addr) {
                                _currentDeviceAddressFlow.value = addr
                                startInterfaceLocked()
                            }
                        }
                    }
                    .launchIn(processLifecycle.coroutineScope)

                bluetoothRepository.state
                    .onEach { state ->
                        interfaceMutex.withLock {
                            if (state.enabled) {
                                startInterfaceLocked()
                            } else if (runningInterfaceId == InterfaceId.BLUETOOTH) {
                                stopInterfaceLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "bluetoothRepository.state flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                networkRepository.networkAvailable
                    .onEach { state ->
                        interfaceMutex.withLock {
                            if (state) {
                                startInterfaceLocked()
                            } else if (runningInterfaceId == InterfaceId.TCP) {
                                stopInterfaceLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)
            }
        }
    }

    override fun connect() {
        processLifecycle.coroutineScope.launch { interfaceMutex.withLock { startInterfaceLocked() } }
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

        Logger.d { "Setting bonded device to ${sanitized?.anonymize}" }
        radioPrefs.setDevAddr(sanitized)
        _currentDeviceAddressFlow.value = sanitized

        processLifecycle.coroutineScope.launch {
            interfaceMutex.withLock {
                ignoreException { stopInterfaceLocked() }
                startInterfaceLocked()
            }
        }
        return true
    }

    /** Must be called under [interfaceMutex]. */
    private fun startInterfaceLocked() {
        if (radioIf != null) return

        // Never autoconnect to the simulated node. The mock transport may be offered in the
        // device-picker UI on debug builds, but it must only connect when the user explicitly
        // selects it (i.e. its address is stored in radioPrefs).
        val address = getBondedDeviceAddress()

        if (address == null) {
            Logger.d { "No valid address to connect to" }
            return
        }

        Logger.i { "Starting radio interface for ${address.anonymize}" }
        isStarted = true
        runningInterfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        radioIf = transportFactory.createTransport(address, this)
        startHeartbeat()
    }

    /** Must be called under [interfaceMutex]. */
    private fun stopInterfaceLocked() {
        val currentIf = radioIf
        Logger.i { "Stopping interface $currentIf" }
        isStarted = false
        radioIf = null
        runningInterfaceId = null
        currentIf?.close()

        _serviceScope.cancel("stopping interface")
        _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

        if (currentIf != null) {
            onDisconnect(isPermanent = true)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastDataReceivedMillis = nowMillis
        heartbeatJob =
            serviceScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MILLIS)
                    keepAlive()
                    checkLiveness()
                }
            }
    }

    /**
     * Detects zombie connections where the BLE stack didn't report a disconnect.
     *
     * If we believe we're connected but haven't received any data from the radio within [LIVENESS_TIMEOUT_MILLIS], the
     * connection is likely dead. Signal a non-permanent disconnect so the reconnect machinery can take over.
     */
    private fun checkLiveness() {
        if (_connectionState.value != ConnectionState.Connected) return

        val silenceMs = nowMillis - lastDataReceivedMillis
        if (silenceMs > LIVENESS_TIMEOUT_MILLIS) {
            Logger.w {
                "Liveness check failed: no data received for ${silenceMs}ms " +
                    "(threshold: ${LIVENESS_TIMEOUT_MILLIS}ms). Treating as disconnect."
            }
            onDisconnect(isPermanent = false, errorMessage = "Connection timeout — no data received")
        }
    }

    fun keepAlive(now: Long = nowMillis) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            radioIf?.keepAlive()
            lastHeartbeatMillis = now
        }
    }

    override fun sendToRadio(bytes: ByteArray) {
        // Capture radioIf reference atomically to avoid racing with stopInterfaceLocked()
        // which sets radioIf = null and cancels _serviceScope. Without this snapshot,
        // we could read a non-null radioIf but launch into an already-cancelled scope.
        val currentIf = radioIf ?: run {
            Logger.w { "sendToRadio: no active radio interface, dropping ${bytes.size} bytes" }
            return
        }
        _serviceScope.handledLaunch {
            currentIf.handleSendToRadio(bytes)
            _meshActivity.tryEmit(MeshActivity.Send)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleFromRadio(bytes: ByteArray) {
        try {
            lastDataReceivedMillis = nowMillis
            processLifecycle.coroutineScope.launch(dispatchers.io) { _receivedData.emit(bytes) }
            _meshActivity.tryEmit(MeshActivity.Receive)
        } catch (t: Throwable) {
            Logger.e(t) { "handleFromRadio failed while emitting data" }
        }
    }

    override fun onConnect() {
        // MutableStateFlow.value is thread-safe (backed by atomics) — assign directly rather than
        // launching a coroutine. The async launch pattern introduced a window where a concurrent
        // onDisconnect launch could execute AFTER an onConnect launch, leaving the service stuck
        // in Connected while the transport was actually disconnected.
        lastDataReceivedMillis = nowMillis
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.d { "Broadcasting connection state change to Connected" }
            _connectionState.value = ConnectionState.Connected
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        if (errorMessage != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionError.emit(errorMessage) }
        }
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newTargetState) {
            Logger.d { "Broadcasting connection state change to $newTargetState" }
            _connectionState.value = newTargetState
        }
    }
}
