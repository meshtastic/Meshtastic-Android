package com.geeksville.mesh

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import com.geeksville.android.Logging
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.google.protobuf.ByteString

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

    /// Send a command/packet to our radio
    private fun sendToRadio(p: ToRadio.Builder) {
        RadioInterfaceService.sendToRadio(this, p.build().toByteArray())
    }

    override fun onBind(intent: Intent): IBinder {
        // Return the interface
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
        registerReceiver(radioInterfaceReceiver, filter)

        // FIXME - don't do this until after we see that the radio is connected to the phone
        // Ask for the current node DB
        sendToRadio(ToRadio.newBuilder().apply {
            wantNodes = ToRadio.WantNodes.newBuilder().build()
        })

    }

    override fun onDestroy() {
        unregisterReceiver(radioInterfaceReceiver)
        super.onDestroy()
    }

    /// Is our radio connected to the phone?
    private var isConnected = false

    /// We learn this from the node db sent by the device - it is stable for the entire session
    private var ourNodeNum = -1

    // model objects that directly map to the corresponding protobufs
    data class MeshUser(val id: String, val longName: String, val shortName: String)

    data class Position(val latitude: Double, val longitude: Double, val altitude: Int)
    data class NodeInfo(
        val num: Int,
        val user: MeshUser,
        val position: Position,
        val lastSeen: Long
    )

    // The database of active nodes, index is the node number
    private val nodeDBbyNodeNum = mutableMapOf<Int, NodeInfo>()

    /// The database of active nodes, index is the node user ID string
    private val nodeDBbyID = mutableMapOf<String, NodeInfo>()

    /// Map a userid to a node num, or throw an exception if not found
    private fun idToNodeNum(id: String) = nodeDBbyID.getValue(id).num

    /// Generate a new mesh packet builder with our node as the sender, and the specified node num
    private

    fun newMeshPacketTo(idNum: Int) = MeshPacket.newBuilder().apply {
        from = ourNodeNum
        to = idNum
    }

    /// Generate a new mesh packet builder with our node as the sender, and the specified recipient
    private fun newMeshPacketTo(id: String) = newMeshPacketTo(idToNodeNum(id))

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

        override fun sendOpaque(destId: String, payloadIn: ByteArray) {
            info("sendOpaque $destId <- ${payloadIn.size}")

            // encapsulate our payload in the proper protobufs and fire it off
            val packet = newMeshPacketTo(destId).apply {
                payload = MeshProtos.MeshPayload.newBuilder().apply {
                    addSubPackets(MeshProtos.SubPacket.newBuilder().apply {
                        opaque = MeshProtos.Opaque.newBuilder().apply {
                            payload = ByteString.copyFrom(payloadIn)
                        }.build()
                    }.build())
                }.build()
            }.build()

            sendToRadio(ToRadio.newBuilder().apply {
                this.packet = packet
            })
        }

        override fun getOnline(): Array<String> {
            val r = nodeDBbyID.keys.toTypedArray()
            info("in getOnline, count=${r.size}")
            // return arrayOf("+16508675309")
            return r
        }

        override fun isConnected(): Boolean {
            val r = this@MeshService.isConnected
            info("in isConnected=r")
            return r
        }
    }
}