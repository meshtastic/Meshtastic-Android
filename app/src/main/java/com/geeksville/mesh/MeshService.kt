package com.geeksville.mesh

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import com.geeksville.android.Logging


/**
 * Handles all the communication with android apps.  Also keeps an internal model
 * of the network state.
 *
 * Note: this service will go away once all clients are unbound from it.
 */
class MeshService : Service(), Logging {

    /*
    see com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
     */

    /**
     * The RECEIVED_OPAQUE:
     * Payload will be the raw bytes which were contained within a MeshPacket.Opaque field
     * Sender will be a user ID string
     */
    fun broadcastReceivedOpaque(senderId: String, payload: ByteArray) {
        val intent = Intent("$prefix.RECEIVED_OPAQUE")
        intent.putExtra(EXTRA_SENDER, senderId)
        intent.putExtra(EXTRA_PAYLOAD, payload)
        sendBroadcast(intent)
    }

    fun broadcastNodeChange(nodeId: String, isOnline: Boolean) {
        val intent = Intent("$prefix.NODE_CHANGE")
        intent.putExtra(EXTRA_ID, nodeId)
        intent.putExtra(EXTRA_ONLINE, isOnline)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        // Return the interface
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
        registerReceiver(radioInterfaceReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(radioInterfaceReceiver)
        super.onDestroy()
    }

    /**
     * Receives messages from our BT radio service and processes them to update our model
     * and send to clients as needed.
     */
    private val radioInterfaceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val proto = MeshProtos.FromRadio.parseFrom(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
            TODO("FIXME - update model and send messages as needed")
        }
    }

    private val binder = object : IMeshService.Stub() {
        override fun setOwner(myId: String, longName: String, shortName: String) {
            error("TODO setOwner $myId : $longName : $shortName")
        }

        override fun sendOpaque(destId: String, payload: ByteArray) {
            error("TODO sendOpaque $destId <- ${payload.size}")
        }

        override fun getOnline(): Array<String> {
            error("TODO getOnline")
            return arrayOf("+16508675309")
        }

        override fun isConnected(): Boolean {
            error("TODO isConnected")
            return true
        }
    }
}