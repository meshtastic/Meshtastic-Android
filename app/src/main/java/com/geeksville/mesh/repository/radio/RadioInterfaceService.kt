/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.radio

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.android.BinaryLogFile
import com.geeksville.mesh.android.BuildUtils
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.ignoreException
import com.geeksville.mesh.util.toRemoteExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the bluetooth link with a mesh radio device. Does not cache any device state, just does bluetooth comms
 * etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb. It doesn't understand protobuf framing etc... It is designed to be simple so it
 * can be stubbed out with a simulated version as needed.
 */
@Singleton
class RadioInterfaceService
@Inject
constructor(
    private val context: Application,
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val processLifecycle: Lifecycle,
    @RadioRepositoryQualifier private val prefs: SharedPreferences,
    private val interfaceFactory: InterfaceFactory,
) : Logging {

    private val _connectionState = MutableStateFlow(RadioServiceConnectionState())
    val connectionState = _connectionState.asStateFlow()

    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData

    // Thread-safe StateFlow for tracking device address changes
    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(prefs.getString(DEVADDR_KEY, null))
    val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val logSends = false
    private val logReceives = false
    private lateinit var sentPacketsLog: BinaryLogFile // inited in onCreate
    private lateinit var receivedPacketsLog: BinaryLogFile

    val mockInterfaceAddress: String by lazy { toInterfaceAddress(InterfaceId.MOCK, "") }

    /** We recreate this scope each time we stop an interface */
    var serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var radioIf: IRadioInterface = interfaceFactory.nopInterface

    /**
     * true if we have started our interface
     *
     * Note: an interface may be started without necessarily yet having a connection
     */
    private var isStarted = false

    // true if our interface is currently connected to a device
    private var isConnected = false

    private var reconnectJob: Job? = null

    private fun initStateListeners() {
        bluetoothRepository.state
            .onEach { bluetoothState ->
                val bondedAddress = getBondedDeviceAddress()
                val configuredDeviceType = interfaceFactory.getInterfaceIdFromAddress(bondedAddress)

                if (bluetoothState.enabled) {
                    // Bluetooth is ON
                    if (configuredDeviceType == InterfaceId.BLUETOOTH && radioIf !is BluetoothInterface) {
                        // Bluetooth device is configured, but not the active interface type
                        info("Bluetooth enabled and BT device configured, ensuring BT interface is active.")
                        onInterfaceChange(bondedAddress)
                    }
                } else {
                    // Bluetooth is OFF
                    if (radioIf is BluetoothInterface) {
                        // Active interface is Bluetooth, but BT is now off
                        info("Bluetooth disabled, stopping Bluetooth interface.")
                        onInterfaceChange(bondedAddress) // This will effectively stop/change the BT interface
                    }
                }
            }
            .launchIn(processLifecycle.coroutineScope)

        networkRepository.networkAvailable
            .onEach { networkAvailable ->
                val bondedAddress = getBondedDeviceAddress()
                val configuredDeviceType = interfaceFactory.getInterfaceIdFromAddress(bondedAddress)

                if (networkAvailable) {
                    // Network is AVAILABLE
                    if (configuredDeviceType == InterfaceId.TCP && radioIf !is TCPInterface) {
                        // TCP device is configured, but not the active interface type
                        info("Network available and TCP device configured, ensuring TCP interface is active.")
                        onInterfaceChange(bondedAddress)
                    }
                } else {
                    // Network is UNAVAILABLE
                    if (radioIf is TCPInterface) {
                        // Active interface is TCP, but network is now unavailable
                        info("Network unavailable, stopping TCP interface.")
                        onInterfaceChange(bondedAddress) // This will effectively stop/change the TCP interface
                    }
                }
            }
            .launchIn(processLifecycle.coroutineScope)
    }

    companion object {
        const val DEVADDR_KEY = "devAddr2" // the new name for devaddr
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val RECONNECT_INTERVAL_MILLIS = 15 * 1000L
    }

    private var lastHeartbeatMillis = 0L

    fun keepAlive(now: Long = System.currentTimeMillis()) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            info("Sending ToRadio heartbeat")
            val heartbeat =
                MeshProtos.ToRadio.newBuilder().setHeartbeat(MeshProtos.Heartbeat.getDefaultInstance()).build()
            handleSendToRadio(heartbeat.toByteArray())
            lastHeartbeatMillis = now
        }
    }

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        interfaceFactory.toInterfaceAddress(interfaceId, rest)

    fun isMockInterface(): Boolean = BuildConfig.DEBUG || (context as GeeksvilleApplication).isInTestLab

    /**
     * Determines whether to default to mock interface for device address. This keeps the decision logic separate and
     * easy to extend.
     */
    private fun shouldDefaultToMockInterface(): Boolean = BuildUtils.isEmulator

    /**
     * Return the device we are configured to use, or null for none device address strings are of the form:
     *
     * at
     *
     * where a is either x for bluetooth or s for serial and t is an interface specific address (macaddr or a device
     * path)
     */
    fun getDeviceAddress(): String? {
        // If the user has unpaired our device, treat things as if we don't have one
        var address = prefs.getString(DEVADDR_KEY, null)

        // If we are running on the emulator we default to the mock interface, so we can have some data to show to the
        // user
        if (address == null && shouldDefaultToMockInterface()) {
            address = mockInterfaceAddress
        }

        return address
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
        return if (interfaceFactory.addressValid(address)) {
            address
        } else {
            null
        }
    }

    private fun broadcastConnectionChanged(isConnected: Boolean, isPermanent: Boolean) {
        debug("Broadcasting connection=$isConnected")

        processLifecycle.coroutineScope.launch(dispatchers.default) {
            _connectionState.emit(RadioServiceConnectionState(isConnected, isPermanent))
        }
    }

    // Send a packet/command out the radio link, this routine can block if it needs to
    private fun handleSendToRadio(p: ByteArray) {
        radioIf.handleSendToRadio(p)
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    fun handleFromRadio(p: ByteArray) {
        if (logReceives) {
            receivedPacketsLog.write(p)
            receivedPacketsLog.flush()
        }

        if (radioIf is SerialInterface) {
            keepAlive(System.currentTimeMillis())
        }

        // ignoreException { debug("FromRadio: ${MeshProtos.FromRadio.parseFrom(p)}") }

        processLifecycle.coroutineScope.launch(dispatchers.io) { _receivedData.emit(p) }
    }

    fun onConnect() {
        if (!isConnected) {
            stopReconnectLoop("Connection established")
            isConnected = true
            broadcastConnectionChanged(isConnected = true, isPermanent = false)
        }
    }

    fun onDisconnect(isPermanent: Boolean) {
        if (isConnected) {
            isConnected = false
            broadcastConnectionChanged(isConnected = false, isPermanent = isPermanent)

            // For temporary disconnects (e.g., out of Bluetooth range), start trying to reconnect.
            // This applies only if we have a bonded device.
            if (!isPermanent && getBondedDeviceAddress() != null) {
                startReconnectLoop()
            }
        }
        // If this is a permanent disconnect (e.g., user unpairs, changes device), stop any
        // reconnection attempts.
        if (isPermanent) {
            stopReconnectLoop("Permanent disconnect")
        }
    }

    /** Start our configured interface (if it isn't already running) */
    private fun startInterface(address: String?) {
        if (radioIf !is NopInterface) {
            warn("Can't start interface - $radioIf is already running")
        } else {
            if (address == null) {
                warn("No bonded mesh radio, can't start interface")
            } else {
                info("Starting radio ${address.anonymize}")
                isStarted = true

                if (logSends) {
                    sentPacketsLog = BinaryLogFile(context, "sent_log.pb")
                }
                if (logReceives) {
                    receivedPacketsLog = BinaryLogFile(context, "receive_log.pb")
                }

                radioIf = interfaceFactory.createInterface(address)
            }
        }
    }

    private fun stopInterface(isFromReconnect: Boolean = false) {
        val r = radioIf
        info("stopping interface $r")
        isStarted = false
        radioIf = interfaceFactory.nopInterface
        r.close()

        // cancel any old jobs and get ready for the new ones
        serviceScope.cancel("stopping interface")
        serviceScope = CoroutineScope(Dispatchers.IO + Job())

        if (logSends) {
            sentPacketsLog.close()
        }
        if (logReceives) {
            receivedPacketsLog.close()
        }

        // Don't broadcast disconnects if we were just using the nop device
        if (r !is NopInterface) {
            // A disconnect from a reconnect attempt is not permanent in the sense that we want to keep trying.
            // A true permanent disconnect would be the user changing the device.
            val isPermanent = !isFromReconnect
            onDisconnect(isPermanent = isPermanent)
        }
    }

    private fun onInterfaceChange(address: String? = getBondedDeviceAddress(), isFromReconnect: Boolean = false) {
        // If this is not a reconnect attempt, it's a user- or system-initiated change.
        // We should stop any existing reconnect loop.
        if (!isFromReconnect) {
            stopReconnectLoop("Interface change initiated by user/system")
        }
        // Ignore any errors that happen while closing old device
        ignoreException { stopInterface(isFromReconnect = isFromReconnect) }
        startInterface(address)
    }

    /**
     * Starts a background coroutine to periodically attempt to reconnect to the bonded device. The loop will be
     * cancelled if a connection is established or a permanent disconnect is requested.
     */
    private fun startReconnectLoop() {
        // Don't start a new loop if one is already running, or if we are already connected.
        if (reconnectJob?.isActive == true || isConnected) return

        val address = getBondedDeviceAddress()
        if (address == null) {
            info("Cannot start reconnect loop, no bonded device.")
            return
        }

        info("Starting reconnect loop for ${address.anonymize}")
        reconnectJob =
            processLifecycle.coroutineScope.launch {
                // We will loop until the job is cancelled (e.g., by connection success or permanent disconnect)
                while (true) {
                    if (!isConnected) {
                        info("Attempting to reconnect...")
                        // Trigger a reconnect attempt by re-initializing the interface.
                        // Pass `isFromReconnect = true` to signal this is part of an auto-reconnect cycle.
                        onInterfaceChange(address, isFromReconnect = true)
                    }
                    delay(RECONNECT_INTERVAL_MILLIS)
                }
            }
    }

    /**
     * Stops the automatic reconnection loop if it is active.
     *
     * @param reason The reason for stopping the loop, used for logging.
     */
    private fun stopReconnectLoop(reason: String) {
        if (reconnectJob?.isActive == true) {
            info("Stopping reconnect loop: $reason")
            reconnectJob?.cancel(reason)
        }
        reconnectJob = null
    }

    /**
     * Change to a new device
     *
     * @return true if the device changed, false if no change
     */
    private fun setBondedDeviceAddress(address: String?): Boolean {
        val currentBondedAddress = getBondedDeviceAddress()
        if (currentBondedAddress == address && isStarted && radioIf !is NopInterface) {
            // Check if radioIf is NopInterface because isStarted could be true
            // but the interface creation might have failed or it's intentionally Nop.
            warn(
                "Ignoring setBondedDevice ${address.anonymize}, because we are already using that device and a valid interface is active.",
            )
            return false
        }

        // Record that this user has configured a new radio
        GeeksvilleApplication.analytics.track("mesh_bond")

        debug("Setting bonded device to ${address.anonymize}")

        prefs.edit {
            if (address == null) {
                this.remove(DEVADDR_KEY)
            } else {
                putString(DEVADDR_KEY, address)
            }
        }
        _currentDeviceAddressFlow.value = address

        // Force the service to reconnect. This is a user action, not an auto-reconnect.
        onInterfaceChange(address, isFromReconnect = false)
        return true
    }

    fun setDeviceAddress(deviceAddr: String?): Boolean = toRemoteExceptions { setBondedDeviceAddress(deviceAddr) }

    /**
     * If the service is not currently connected to the radio, try to connect now. At boot the radio interface service
     * will not connect to a radio until this call is received.
     */
    fun connect() = toRemoteExceptions {
        // We don't start actually talking to our device until MeshService binds to us - this prevents
        // broadcasting connection events before MeshService is ready to receive them
        onInterfaceChange(getBondedDeviceAddress())
        initStateListeners()
    }

    fun sendToRadio(a: ByteArray) {
        // Do this in the IO thread because it might take a while (and we don't care about the result code)
        serviceScope.handledLaunch { handleSendToRadio(a) }
    }
}
