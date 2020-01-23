package com.geeksville.mesh

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.geeksville.android.Logging

class MeshService : Service(), Logging {

    val prefix = "com.geeksville.mesh"

    /*
    see com.geeksville.com.geeeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
     */
    fun broadcastReceivedOpaque(senderId: String, payload: ByteArray) {
        val intent = Intent("$prefix.RECEIVED_OPAQUE")
        intent.putExtra("$prefix.Sender", senderId)
        intent.putExtra("$prefix.Payload", payload)
        sendBroadcast(intent)
    }

    fun broadcastNodeChange(nodeId: String, isOnline: Boolean) {
        val intent = Intent("$prefix.NODE_CHANGE")
        intent.putExtra("$prefix.Id", nodeId)
        intent.putExtra("$prefix.Online", isOnline)
        sendBroadcast(intent)
    }

    fun broadcastConnectionChanged(isConnected: Boolean) {
        val intent = Intent("$prefix.CONNECTION_CHANGED")
        intent.putExtra("$prefix.Connected", isConnected)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        // Return the interface
        return binder
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