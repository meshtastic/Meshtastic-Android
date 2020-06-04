package com.geeksville.mesh.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.geeksville.android.BinaryLogFile
import com.geeksville.android.Logging
import com.geeksville.concurrent.DeferredExecution
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

        /// mynode - read/write this to access a MyNodeInfo protobuf
        val BTM_MYNODE_CHARACTER =
            UUID.fromString("ea9f3f82-8dc4-4733-9452-1f6da28892a2")

        /// nodeinfo - read this to get a series of node infos (ending with a null empty record), write to this to restart the read statemachine that returns all the node infos
        val BTM_NODEINFO_CHARACTER =
            UUID.fromString("d31e02e0-c8ab-4d3f-9cc9-0b8466bdabe8")

        /// radio - read/write this to access a RadioConfig protobuf
        val BTM_RADIO_CHARACTER =
            UUID.fromString("b56786c8-839a-44a1-b98e-a1724c4a0262")

        /// owner - read/write this to access a User protobuf
        val BTM_OWNER_CHARACTER =
            UUID.fromString("6ff1d8b6-e2de-41e3-8c0b-8fa384f64eb6")

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

    /// Send a packet/command out the radio link
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
    protected abstract fun doWrite(uuid: UUID, a: ByteArray)

    /**
     * do an asynchronous write operation
     * Any error responses will be ignored (other than log messages)
     */
    protected abstract fun doAsyncWrite(uuid: UUID, a: ByteArray)

    protected abstract fun setBondedDeviceAddress(addr: String?)

    /**
     * do a synchronous read operation
     */
    protected abstract fun doRead(uuid: UUID): ByteArray?

    private val binder = object : IRadioInterfaceService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
            setBondedDeviceAddress(deviceAddr)
        }

        override fun sendToRadio(a: ByteArray) = handleSendToRadio(a)

        //
        // NOTE: the following methods are all deprecated and will be removed soon
        //

        // A write of any size to nodeinfo means restart reading
        override fun restartNodeInfo() = doWrite(BTM_NODEINFO_CHARACTER, ByteArray(0))

        override fun readMyNode() =
            doRead(BTM_MYNODE_CHARACTER)
                ?: throw RemoteException("Device returned empty MyNodeInfo")

        override fun readRadioConfig() =
            doRead(BTM_RADIO_CHARACTER)
                ?: throw RemoteException("Device returned empty RadioConfig")

        override fun readOwner() =
            doRead(BTM_OWNER_CHARACTER) ?: throw RemoteException("Device returned empty Owner")

        override fun writeOwner(owner: ByteArray) = doWrite(BTM_OWNER_CHARACTER, owner)
        override fun writeRadioConfig(config: ByteArray) = doWrite(BTM_RADIO_CHARACTER, config)

        override fun readNodeInfo() = doRead(BTM_NODEINFO_CHARACTER)
    }
}
