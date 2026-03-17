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
package org.meshtastic.core.network.radio

import android.app.Application
import android.provider.Settings
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
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.util.BinaryLogFile
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ignoreException
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.toRemoteExceptions
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio

/**
 * Handles the bluetooth link with a mesh radio device. Does not cache any device state, just does bluetooth comms
 * etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb. It doesn't understand protobuf framing etc... It is designed to be simple so it
 * can be stubbed out with a simulated version as needed.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@Single
class AndroidRadioInterfaceService(
    private val context: Application,
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val buildConfigProvider: BuildConfigProvider,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val radioPrefs: RadioPrefs,
    private val interfaceFactory: Lazy<InterfaceFactory>,
    private val analytics: PlatformAnalytics,
) : RadioInterfaceService {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val supportedDeviceTypes: List<org.meshtastic.core.model.DeviceType> =
        listOf(
            org.meshtastic.core.model.DeviceType.BLE,
            org.meshtastic.core.model.DeviceType.TCP,
            org.meshtastic.core.model.DeviceType.USB,
        )

    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val receivedData: SharedFlow<ByteArray> = _receivedData

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val connectionError: SharedFlow<String> = _connectionError.asSharedFlow()

    // Thread-safe StateFlow for tracking device address changes
    private val _currentDeviceAddressFlow = MutableStateFlow(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val logSends = false
    private val logReceives = false
    private lateinit var sentPacketsLog: BinaryLogFile
    private lateinit var receivedPacketsLog: BinaryLogFile

    val mockInterfaceAddress: String by lazy { toInterfaceAddress(InterfaceId.MOCK, "") }

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    /** We recreate this scope each time we stop an interface */
    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

    private var radioIf: RadioTransport = NopInterface("")

    /**
     * true if we have started our interface
     *
     * Note: an interface may be started without necessarily yet having a connection
     */
    private var isStarted = false

    @Volatile private var listenersInitialized = false

    private fun initStateListeners() {
        if (listenersInitialized) return
        synchronized(this) {
            if (listenersInitialized) return
            listenersInitialized = true

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
                    } else if (radioIf is BleRadioInterface) {
                        stopInterface()
                    }
                }
                .catch { Logger.e(it) { "bluetoothRepository.state flow crashed!" } }
                .launchIn(processLifecycle.coroutineScope)

            networkRepository.networkAvailable
                .onEach { state ->
                    if (state) {
                        startInterface()
                    } else if (radioIf is TCPInterface) {
                        stopInterface()
                    }
                }
                .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed!" } }
                .launchIn(processLifecycle.coroutineScope)
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 30 * 1000L
    }

    private var lastHeartbeatMillis = 0L

    fun keepAlive(now: Long = nowMillis) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            if (radioIf is SerialInterface) {
                Logger.i { "Sending ToRadio heartbeat" }
                val heartbeat = ToRadio(heartbeat = Heartbeat())
                handleSendToRadio(heartbeat.encode())
            } else {
                // For BLE and TCP this will check if the connection is still alive
                radioIf.keepAlive()
            }
            lastHeartbeatMillis = now
        }
    }

    /** Constructs a full radio address for the specific interface type. */
    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        interfaceFactory.value.toInterfaceAddress(interfaceId, rest)

    override fun isMockInterface(): Boolean =
        buildConfigProvider.isDebug || Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    override fun getDeviceAddress(): String? {
        // If the user has unpaired our device, treat things as if we don't have one
        return _currentDeviceAddressFlow.value
    }

    /**
     * Like getDeviceAddress, but filtered to return only devices we are currently bonded with
     *
     * at
     *
     * where a is either x for bluetooth or s for serial and t is an interface specific address (macaddr or a device
     * path)
     */
    fun getBondedDeviceAddress(): String? {
        // If the user has unpaired our device, treat things as if we don't have one
        val address = getDeviceAddress()
        return if (interfaceFactory.value.addressValid(address)) {
            address
        } else {
            null
        }
    }

    private fun broadcastConnectionChanged(newState: ConnectionState) {
        Logger.d { "Broadcasting connection state change to $newState" }
        processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionState.emit(newState) }
    }

    // Send a packet/command out the radio link, this routine can block if it needs to
    private fun handleSendToRadio(p: ByteArray) {
        radioIf.handleSendToRadio(p)
        emitSendActivity()
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    @Suppress("TooGenericExceptionCaught")
    override fun handleFromRadio(bytes: ByteArray) {
        if (logReceives) {
            try {
                receivedPacketsLog.write(bytes)
                receivedPacketsLog.flush()
            } catch (t: Throwable) {
                Logger.w(t) { "Failed to write receive log in handleFromRadio" }
            }
        }

        try {
            processLifecycle.coroutineScope.launch(dispatchers.io) { _receivedData.emit(bytes) }
            emitReceiveActivity()
        } catch (t: Throwable) {
            Logger.e(t) { "RadioInterfaceService.handleFromRadio failed while emitting data" }
        }
    }

    override fun onConnect() {
        if (_connectionState.value != ConnectionState.Connected) {
            broadcastConnectionChanged(ConnectionState.Connected)
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        if (errorMessage != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionError.emit(errorMessage) }
        }
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newTargetState) {
            broadcastConnectionChanged(newTargetState)
        }
    }

    /** Start our configured interface (if it isn't already running) */
    private fun startInterface() {
        if (radioIf !is NopInterface) {
            // Already running
            return
        }

        val isTestLab = Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
        val address =
            getBondedDeviceAddress()
                ?: if (isTestLab) {
                    mockInterfaceAddress
                } else {
                    null
                }

        if (address == null) {
            Logger.w { "No bonded mesh radio, can't start interface" }
        } else {
            Logger.i { "Starting radio ${address.anonymize}" }
            isStarted = true

            if (logSends) {
                sentPacketsLog = BinaryLogFile(context, "sent_log.pb")
            }
            if (logReceives) {
                receivedPacketsLog = BinaryLogFile(context, "receive_log.pb")
            }

            radioIf = interfaceFactory.value.createInterface(address, this)
            startHeartbeat()
        }
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob =
            serviceScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MILLIS)
                    keepAlive()
                }
            }
    }

    private fun stopInterface() {
        val r = radioIf
        Logger.i { "stopping interface $r" }
        isStarted = false
        radioIf = interfaceFactory.value.nopInterface
        r.close()

        // cancel any old jobs and get ready for the new ones
        _serviceScope.cancel("stopping interface")
        _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

        if (logSends) {
            sentPacketsLog.close()
        }
        if (logReceives) {
            receivedPacketsLog.close()
        }

        // Don't broadcast disconnects if we were just using the nop device
        if (r !is NopInterface) {
            onDisconnect(isPermanent = true) // Tell any clients we are now offline
        }
    }

    /**
     * Change to a new device
     *
     * @return true if the device changed, false if no change
     */
    private fun setBondedDeviceAddress(address: String?): Boolean =
        if (getBondedDeviceAddress() == address && isStarted && _connectionState.value == ConnectionState.Connected) {
            Logger.w { "Ignoring setBondedDevice ${address.anonymize}, because we are already using that device" }
            false
        } else {
            // Record that this use has configured a new radio
            analytics.track("mesh_bond")

            // Ignore any errors that happen while closing old device
            ignoreException { stopInterface() }

            // The device address "n" can be used to mean none

            Logger.d { "Setting bonded device to ${address.anonymize}" }

            // Stores the address if non-null, otherwise removes the pref
            radioPrefs.setDevAddr(address)
            _currentDeviceAddressFlow.value = address

            // Force the service to reconnect
            startInterface()
            true
        }

    override fun setDeviceAddress(deviceAddr: String?): Boolean = toRemoteExceptions {
        setBondedDeviceAddress(deviceAddr)
    }

    /**
     * If the service is not currently connected to the radio, try to connect now. At boot the radio interface service
     * will not connect to a radio until this call is received.
     */
    override fun connect() = toRemoteExceptions {
        // We don't start actually talking to our device until MeshService binds to us - this prevents
        // broadcasting connection events before MeshService is ready to receive them
        startInterface()
        initStateListeners()
    }

    override fun sendToRadio(bytes: ByteArray) {
        // Do this in the IO thread because it might take a while (and we don't care about the result code)
        _serviceScope.handledLaunch { handleSendToRadio(bytes) }
    }

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(
            extraBufferCapacity = 64,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    override val meshActivity: SharedFlow<MeshActivity> = _meshActivity.asSharedFlow()

    private fun emitSendActivity() {
        _meshActivity.tryEmit(MeshActivity.Send)
    }

    private fun emitReceiveActivity() {
        _meshActivity.tryEmit(MeshActivity.Receive)
    }
}
