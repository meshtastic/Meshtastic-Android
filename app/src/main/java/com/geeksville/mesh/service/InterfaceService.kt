package com.geeksville.mesh.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.geeksville.android.BinaryLogFile
import com.geeksville.android.Logging
import com.geeksville.concurrent.DeferredExecution
import com.geeksville.concurrent.handledLaunch
import com.geeksville.mesh.IRadioInterfaceService
import com.geeksville.util.toRemoteExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*


class RadioNotConnectedException(message: String = "Not connected to radio") :
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
abstract class InterfaceService : Service(), Logging {

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

        const val DEVADDR_KEY = "devAddr"

        val BTM_FROMRADIO_CHARACTER =
            UUID.fromString("8ba2bcc2-ee02-4a55-a531-c525c5e454d5")
        val BTM_TORADIO_CHARACTER =
            UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val BTM_FROMNUM_CHARACTER =
            UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

        /// This is public only so that SimRadio can bootstrap our message flow
        fun broadcastReceivedFromRadio(context: Context, payload: ByteArray) {
            val intent = Intent(RECEIVE_FROMRADIO_ACTION)
            intent.putExtra(EXTRA_PAYLOAD, payload)
            context.sendBroadcast(intent)
        }

        fun getPrefs(context: Context) =
            context.getSharedPreferences("radio-prefs", Context.MODE_PRIVATE)


    }

    protected val logSends = false
    protected val logReceives = false
    protected lateinit var sentPacketsLog: BinaryLogFile // inited in onCreate
    protected lateinit var receivedPacketsLog: BinaryLogFile

    protected var isConnected = false

    private val serviceJob = Job()
    protected val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /// Work that users of our service want done, which might get deferred until after
    /// we have completed our initial connection
    private val clientOperations = DeferredExecution()

    protected fun broadcastConnectionChanged(isConnected: Boolean, isPermanent: Boolean) {
        debug("Broadcasting connection=$isConnected")
        val intent = Intent(RADIO_CONNECTED_ACTION)
        intent.putExtra(EXTRA_CONNECTED, isConnected)
        intent.putExtra(EXTRA_PERMANENT, isPermanent)
        sendBroadcast(intent)
    }

    /**
     * With the new rev2 api, our first send is to start the configure readbacks.  In that case,
     * rather than waiting for FromNum notifies - we try to just aggressively read all of the responses.
     */
    private var isFirstSend = true

    /// Send a packet/command out the radio link, this routine can block if it needs to
    protected abstract fun handleSendToRadio(p: ByteArray)

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    protected fun handleFromRadio(p: ByteArray) {
        if (logReceives) {
            receivedPacketsLog.write(p)
            receivedPacketsLog.flush()
        }

        broadcastReceivedFromRadio(
            this,
            p
        )
    }


    protected fun onDisconnect(isPermanent: Boolean) {
        broadcastConnectionChanged(false, isPermanent)
        isConnected = false
    }


    override fun onCreate() {
        super.onCreate()
        setEnabled(true)
    }

    override fun onDestroy() {
        setEnabled(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder;
    }


    /// Open or close a bluetooth connection to our device
    protected open fun setEnabled(on: Boolean) {
        if (on) {
            if (logSends)
                sentPacketsLog = BinaryLogFile(this, "sent_log.pb")
            if (logReceives)
                receivedPacketsLog = BinaryLogFile(this, "receive_log.pb")
        } else {
            if (logSends)
                sentPacketsLog.close()
            if (logReceives)
                receivedPacketsLog.close()

            onDisconnect(isPermanent = true) // Tell any clients we are now offline
        }
    }

    /**
     * do a synchronous write operation
     */
    protected open fun doWrite(uuid: UUID, a: ByteArray) {
        throw NotImplementedError("Only implemented temporarily until rev1 api is removed")
    }

    /**
     * do an asynchronous write operation
     * Any error responses will be ignored (other than log messages)
     */
    protected open fun doAsyncWrite(uuid: UUID, a: ByteArray) {
        throw NotImplementedError("Only implemented temporarily until rev1 api is removed")
    }

    protected open fun setBondedDeviceAddress(addr: String?) {
        TODO()
    }

    /**
     * do a synchronous read operation
     */
    protected open fun doRead(uuid: UUID): ByteArray? {
        throw NotImplementedError("Only implemented temporarily until rev1 api is removed")
    }

    private val binder = object : IRadioInterfaceService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
            setBondedDeviceAddress(deviceAddr)
        }

        override fun sendToRadio(a: ByteArray) {
            // Do this in the IO thread because it might take a while (and we don't care about the result code)
            serviceScope.handledLaunch { handleSendToRadio(a) }
        }
    }
}
