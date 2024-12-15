/*
 * Copyright (c) 2024 Meshtastic LLC
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the bluetooth link with a mesh radio device.  Does not cache any device state,
 * just does bluetooth comms etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb.  It doesn't understand protobuf framing etc...
 * It is designed to be simple so it can be stubbed out with a simulated version as needed.
 */
@Singleton
class RadioInterfaceService @Inject constructor(
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

    private val logSends = false
    private val logReceives = false
    private lateinit var sentPacketsLog: BinaryLogFile // inited in onCreate
    private lateinit var receivedPacketsLog: BinaryLogFile

    val mockInterfaceAddress: String by lazy {
        toInterfaceAddress(InterfaceId.MOCK, "")
    }

    /**
     * We recreate this scope each time we stop an interface
     */
    var serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var radioIf: IRadioInterface = NopInterface("")

    /** true if we have started our interface
     *
     * Note: an interface may be started without necessarily yet having a connection
     */
    private var isStarted = false

    // true if our interface is currently connected to a device
    private var isConnected = false

    private fun initStateListeners() {
        bluetoothRepository.state.onEach { state ->
            if (state.enabled) startInterface()
            else if (radioIf is BluetoothInterface) stopInterface()
        }.launchIn(processLifecycle.coroutineScope)

        networkRepository.networkAvailable.onEach { state ->
            if (state) startInterface()
            else if (radioIf is TCPInterface) stopInterface()
        }.launchIn(processLifecycle.coroutineScope)
    }

    companion object {
        const val DEVADDR_KEY = "devAddr2" // the new name for devaddr
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L
    }

    private var lastHeartbeatMillis = 0L
    private fun keepAlive(now: Long) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            info("Sending ToRadio heartbeat")
            val heartbeat = MeshProtos.ToRadio.newBuilder()
                .setHeartbeat(MeshProtos.Heartbeat.getDefaultInstance()).build()
            handleSendToRadio(heartbeat.toByteArray())
            lastHeartbeatMillis = now
        }
    }

    /**
     * Constructs a full radio address for the specific interface type.
     */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String {
        return interfaceFactory.toInterfaceAddress(interfaceId, rest)
    }

    val isMockInterface: Boolean by lazy {
        BuildUtils.isEmulator || (context as GeeksvilleApplication).isInTestLab
    }

    /** Return the device we are configured to use, or null for none
     * device address strings are of the form:
     *
     * at
     *
     * where a is either x for bluetooth or s for serial
     * and t is an interface specific address (macaddr or a device path)
     */
    fun getDeviceAddress(): String? {
        // If the user has unpaired our device, treat things as if we don't have one
        var address = prefs.getString(DEVADDR_KEY, null)

        // If we are running on the emulator we default to the mock interface, so we can have some data to show to the user
        if (address == null && isMockInterface) {
            address = mockInterfaceAddress
        }

        return address
    }

    /** Like getDeviceAddress, but filtered to return only devices we are currently bonded with
     *
     * at
     *
     * where a is either x for bluetooth or s for serial
     * and t is an interface specific address (macaddr or a device path)
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
            _connectionState.emit(
                RadioServiceConnectionState(isConnected, isPermanent)
            )
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

        processLifecycle.coroutineScope.launch(dispatchers.io) {
            _receivedData.emit(p)
        }
    }

    fun onConnect() {
        if (!isConnected) {
            isConnected = true
            broadcastConnectionChanged(isConnected = true, isPermanent = false)
        }
    }

    fun onDisconnect(isPermanent: Boolean) {
        if (isConnected) {
            isConnected = false
            broadcastConnectionChanged(isConnected = false, isPermanent = isPermanent)
        }
    }

    /** Start our configured interface (if it isn't already running) */
    private fun startInterface() {
        if (radioIf !is NopInterface) {
            warn("Can't start interface - $radioIf is already running")
        } else {
            val address = getBondedDeviceAddress()
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

    private fun stopInterface() {
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
            onDisconnect(isPermanent = true) // Tell any clients we are now offline
        }
    }

    /**
     * Change to a new device
     *
     * @return true if the device changed, false if no change
     */
    private fun setBondedDeviceAddress(address: String?): Boolean {
        return if (getBondedDeviceAddress() == address && isStarted) {
            warn("Ignoring setBondedDevice ${address.anonymize}, because we are already using that device")
            false
        } else {
            // Record that this use has configured a new radio
            GeeksvilleApplication.analytics.track(
                "mesh_bond"
            )

            // Ignore any errors that happen while closing old device
            ignoreException {
                stopInterface()
            }

            // The device address "n" can be used to mean none

            debug("Setting bonded device to ${address.anonymize}")

            prefs.edit {
                if (address == null) {
                    this.remove(DEVADDR_KEY)
                } else {
                    putString(DEVADDR_KEY, address)
                }
            }

            // Force the service to reconnect
            startInterface()
            true
        }
    }

    fun setDeviceAddress(deviceAddr: String?): Boolean = toRemoteExceptions {
        setBondedDeviceAddress(deviceAddr)
    }

    /** If the service is not currently connected to the radio, try to connect now.  At boot the radio interface service will
     * not connect to a radio until this call is received.  */
    fun connect() = toRemoteExceptions {
        // We don't start actually talking to our device until MeshService binds to us - this prevents
        // broadcasting connection events before MeshService is ready to receive them
        startInterface()
        initStateListeners()
    }

    fun sendToRadio(a: ByteArray) {
        // Do this in the IO thread because it might take a while (and we don't care about the result code)
        serviceScope.handledLaunch { handleSendToRadio(a) }
    }
}
