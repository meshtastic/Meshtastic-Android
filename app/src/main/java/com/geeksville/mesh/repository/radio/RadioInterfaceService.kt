package com.geeksville.mesh.repository.radio

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.android.BinaryLogFile
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.nsd.NsdRepository
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.ignoreException
import com.geeksville.mesh.util.toRemoteExceptions
import kotlinx.coroutines.*
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
    bluetoothRepository: BluetoothRepository,
    nsdRepository: NsdRepository,
    private val processLifecycle: Lifecycle,
    private val usbRepository: UsbRepository,
    @RadioRepositoryQualifier private val prefs: SharedPreferences
) : Logging {

    private val _connectionState = MutableStateFlow(RadioServiceConnectionState())
    val connectionState = _connectionState.asStateFlow()

    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData

    private val logSends = false
    private val logReceives = false
    private lateinit var sentPacketsLog: BinaryLogFile // inited in onCreate
    private lateinit var receivedPacketsLog: BinaryLogFile

    /**
     * We recreate this scope each time we stop an interface
     */
    var serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var radioIf: IRadioInterface = NopInterface()

    /** true if we have started our interface
     *
     * Note: an interface may be started without necessarily yet having a connection
     */
    private var isStarted = false

    /// true if our interface is currently connected to a device
    private var isConnected = false

    init {
        bluetoothRepository.state.onEach { state ->
            if (state.enabled) startInterface()
            else if (radioIf is BluetoothInterface) stopInterface()
        }.launchIn(processLifecycle.coroutineScope)

        nsdRepository.networkAvailable.onEach { state ->
            if (state) startInterface()
            else if (radioIf is TCPInterface) stopInterface()
        }.launchIn(processLifecycle.coroutineScope)
    }

    companion object : Logging {
        const val DEVADDR_KEY = "devAddr2" // the new name for devaddr

        init {
            /// We keep this var alive so that the following factory objects get created and not stripped during the android build
            val factories = arrayOf<InterfaceFactory>(
                BluetoothInterface,
                SerialInterface,
                TCPInterface,
                MockInterface,
                NopInterface
            )
            info("Using ${factories.size} interface factories")
        }
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
        if (address == null && MockInterface.addressValid(context, usbRepository, ""))
            address = MockInterface.prefix.toString()

        return address
    }

    /** Like getDeviceAddress, but filtered to return only devices we are currently bonded with
     *
     * at
     *
     * where a is either x for bluetooth or s for serial
     * and t is an interface specific address (macaddr or a device path)
     */
    @SuppressLint("NewApi")
    fun getBondedDeviceAddress(): String? {
        // If the user has unpaired our device, treat things as if we don't have one
        val address = getDeviceAddress()

        /// Interfaces can filter addresses to indicate that address is no longer acceptable
        if (address != null) {
            val c = address[0]
            val rest = address.substring(1)
            val isValid = InterfaceFactory.getFactory(c)
                ?.addressValid(context, usbRepository, rest) ?: false
            if (!isValid)
                return null
        }
        return address
    }

    private fun broadcastConnectionChanged(isConnected: Boolean, isPermanent: Boolean) {
        debug("Broadcasting connection=$isConnected")

        processLifecycle.coroutineScope.launch(dispatchers.default) {
            _connectionState.emit(
                RadioServiceConnectionState(isConnected, isPermanent)
            )
        }
    }

    /// Send a packet/command out the radio link, this routine can block if it needs to
    private fun handleSendToRadio(p: ByteArray) {
        radioIf.handleSendToRadio(p)
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    fun handleFromRadio(p: ByteArray) {
        if (logReceives) {
            receivedPacketsLog.write(p)
            receivedPacketsLog.flush()
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
        if (radioIf !is NopInterface)
            warn("Can't start interface - $radioIf is already running")
        else {
            val address = getBondedDeviceAddress()
            if (address == null)
                warn("No bonded mesh radio, can't start interface")
            else {
                info("Starting radio ${address.anonymize}")
                isStarted = true

                if (logSends)
                    sentPacketsLog = BinaryLogFile(context, "sent_log.pb")
                if (logReceives)
                    receivedPacketsLog = BinaryLogFile(context, "receive_log.pb")

                val c = address[0]
                val rest = address.substring(1)
                radioIf =
                    InterfaceFactory.getFactory(c)?.createInterface(context, this, usbRepository, rest) ?: NopInterface()
            }
        }
    }

    private fun stopInterface() {
        val r = radioIf
        info("stopping interface $r")
        isStarted = false
        radioIf = NopInterface()
        r.close()

        // cancel any old jobs and get ready for the new ones
        serviceScope.cancel("stopping interface")
        serviceScope = CoroutineScope(Dispatchers.IO + Job())

        if (logSends)
            sentPacketsLog.close()
        if (logReceives)
            receivedPacketsLog.close()

        // Don't broadcast disconnects if we were just using the nop device
        if (r !is NopInterface)
            onDisconnect(isPermanent = true) // Tell any clients we are now offline
    }


    /**
     * Change to a new device
     *
     * @return true if the device changed, false if no change
     */
    @SuppressLint("NewApi")
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
                if (address == null)
                    this.remove(DEVADDR_KEY)
                else
                    putString(DEVADDR_KEY, address)
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
    }

    fun sendToRadio(a: ByteArray) {
        // Do this in the IO thread because it might take a while (and we don't care about the result code)
        serviceScope.handledLaunch { handleSendToRadio(a) }
    }
}
