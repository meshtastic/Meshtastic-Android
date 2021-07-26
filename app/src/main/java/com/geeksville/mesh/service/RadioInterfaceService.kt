package com.geeksville.mesh.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.content.edit
import com.geeksville.android.BinaryLogFile
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.concurrent.handledLaunch
import com.geeksville.mesh.IRadioInterfaceService
import com.geeksville.util.anonymize
import com.geeksville.util.ignoreException
import com.geeksville.util.toRemoteExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel


open class RadioNotConnectedException(message: String = "Not connected to radio") :
    BLEException(message)


/**
 * Handles the bluetooth link with a mesh radio device.  Does not cache any device state,
 * just does bluetooth comms etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb.  It doesn't understand protobuf framing etc...
 * It is designed to be simple so it can be stubbed out with a simulated version as needed.
 */
class RadioInterfaceService : Service(), Logging {

    companion object : Logging {
        /**
         * The RECEIVED_FROMRADIO
         * Payload will be the raw bytes which were contained within a MeshProtos.FromRadio protobuf
         */
        const val RECEIVE_FROMRADIO_ACTION = "$prefix.RECEIVE_FROMRADIO"

        /**
         * This is broadcast when connection state changed
         */
        const val RADIO_CONNECTED_ACTION = "$prefix.CONNECT_CHANGED"

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

        /// This is public only so that SimRadio can bootstrap our message flow
        fun broadcastReceivedFromRadio(context: Context, payload: ByteArray) {
            val intent = Intent(RECEIVE_FROMRADIO_ACTION)
            intent.putExtra(EXTRA_PAYLOAD, payload)
            context.sendBroadcast(intent)
        }

        fun getPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences("radio-prefs", Context.MODE_PRIVATE)

        /** Return the device we are configured to use, or null for none
         * device address strings are of the form:
         *
         * at
         *
         * where a is either x for bluetooth or s for serial
         * and t is an interface specific address (macaddr or a device path)
         */
        @SuppressLint("NewApi")
        fun getDeviceAddress(context: Context): String? {
            // If the user has unpaired our device, treat things as if we don't have one
            val prefs = getPrefs(context)
            var address = prefs.getString(DEVADDR_KEY, null)

            // If we are running on the emulator we default to the mock interface, so we can have some data to show to the user
            if (address == null && MockInterface.addressValid(context, ""))
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
        fun getBondedDeviceAddress(context: Context): String? {
            // If the user has unpaired our device, treat things as if we don't have one
            var address = getDeviceAddress(context)

            /// Interfaces can filter addresses to indicate that address is no longer acceptable
            if (address != null) {
                val c = address[0]
                val rest = address.substring(1)
                val isValid = InterfaceFactory.getFactory(c)?.addressValid(context, rest) ?: false
                if (!isValid)
                    return null
            }
            return address
        }

        /// If our service is currently running, this pointer can be used to reach it (in case setBondedDeviceAddress is called)
        private var runningService: RadioInterfaceService? = null
    }

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

    /**
     * If the user turns on bluetooth after we start, make sure to try and reconnected then
     */
    private val bluetoothStateReceiver = BluetoothStateReceiver { enabled ->
        if (enabled)
            startInterface() // If bluetooth just got turned on, try to restart our ble link (which might be bluetooth)
        else if (radioIf is BluetoothInterface)
            stopInterface() // Was using bluetooth, need to shutdown
    }

    private fun broadcastConnectionChanged(isConnected: Boolean, isPermanent: Boolean) {
        debug("Broadcasting connection=$isConnected")
        val intent = Intent(RADIO_CONNECTED_ACTION)
        intent.putExtra(EXTRA_CONNECTED, isConnected)
        intent.putExtra(EXTRA_PERMANENT, isPermanent)
        sendBroadcast(intent)
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

        broadcastReceivedFromRadio(
            this,
            p
        )
    }

    fun onConnect() {
        if (!isConnected) {
            isConnected = true
            broadcastConnectionChanged(true, false)
        }
    }

    fun onDisconnect(isPermanent: Boolean) {
        if (isConnected) {
            isConnected = false
            broadcastConnectionChanged(false, isPermanent)
        }
    }


    override fun onCreate() {
        runningService = this
        super.onCreate()
        registerReceiver(bluetoothStateReceiver, bluetoothStateReceiver.intentFilter)
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothStateReceiver)
        stopInterface()
        serviceScope.cancel("Destroying RadioInterface")
        runningService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }


    /** Start our configured interface (if it isn't already running) */
    private fun startInterface() {
        if (radioIf !is NopInterface)
            warn("Can't start interface - $radioIf is already running")
        else {
            val address = getBondedDeviceAddress(this)
            if (address == null)
                warn("No bonded mesh radio, can't start interface")
            else {
                info("Starting radio ${address.anonymize}")
                isStarted = true

                if (logSends)
                    sentPacketsLog = BinaryLogFile(this, "sent_log.pb")
                if (logReceives)
                    receivedPacketsLog = BinaryLogFile(this, "receive_log.pb")

                val c = address[0]
                val rest = address.substring(1)
                radioIf =
                    InterfaceFactory.getFactory(c)?.createInterface(this, rest) ?: NopInterface()
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
        return if (getBondedDeviceAddress(this) == address && isStarted) {
            warn("Ignoring setBondedDevice $address, because we are already using that device")
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

            getPrefs(this).edit(commit = true) {
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

    private val binder = object : IRadioInterfaceService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?): Boolean = toRemoteExceptions {
            setBondedDeviceAddress(deviceAddr)
        }

        /** If the service is not currently connected to the radio, try to connect now.  At boot the radio interface service will
         * not connect to a radio until this call is received.  */
        override fun connect() = toRemoteExceptions {
            // We don't start actually talking to our device until MeshService binds to us - this prevents
            // broadcasting connection events before MeshService is ready to receive them
            startInterface()
        }

        override fun sendToRadio(a: ByteArray) {
            // Do this in the IO thread because it might take a while (and we don't care about the result code)
            serviceScope.handledLaunch { handleSendToRadio(a) }
        }
    }
}
