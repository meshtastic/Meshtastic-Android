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
import com.geeksville.util.exceptionReporter
import com.geeksville.util.exceptionsToStrings
import com.google.protobuf.ByteString
import java.nio.charset.Charset

/**
 * Handles all the communication with android apps.  Also keeps an internal model
 * of the network state.
 *
 * Note: this service will go away once all clients are unbound from it.
 */
class MeshService : Service(), Logging {

    companion object {
        class IdNotFoundException(id: String) : Exception("ID not found $id")
        class NodeNumNotFoundException(id: Int) : Exception("NodeNum not found $id")
    }

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
     * Type will be the Data.Type enum code for this payload
     */
    private fun broadcastReceivedOpaque(senderId: String, payload: ByteArray, typ: Int) {
        val intent = Intent("$prefix.RECEIVED_OPAQUE")
        intent.putExtra(EXTRA_SENDER, senderId)
        intent.putExtra(EXTRA_PAYLOAD, payload)
        intent.putExtra(EXTRA_TYP, typ)
        sendBroadcast(intent)
    }

    private fun broadcastNodeChange(nodeId: String, isOnline: Boolean) {
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

        info("Creating mesh service")
        val filter = IntentFilter(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
        registerReceiver(radioInterfaceReceiver, filter)

        // FIXME - don't do this until after we see that the radio is connected to the phone
        // Ask for the current node DB
        sendToRadio(ToRadio.newBuilder().apply {
            wantNodes = ToRadio.WantNodes.newBuilder().build()
        })

    }

    override fun onDestroy() {
        info("Destroying mesh service")
        unregisterReceiver(radioInterfaceReceiver)
        super.onDestroy()
    }

    // model objects that directly map to the corresponding protobufs
    data class MeshUser(val id: String, val longName: String, val shortName: String)

    data class Position(val latitude: Double, val longitude: Double, val altitude: Int)
    data class NodeInfo(
        val num: Int, // This is immutable, and used as a key
        var user: MeshUser? = null,
        var position: Position? = null,
        var lastSeen: Long? = null
    )

    ///
    /// BEGINNING OF MODEL - FIXME, move elsewhere
    ///

    /// Is our radio connected to the phone?
    private var isConnected = false

    /// We learn this from the node db sent by the device - it is stable for the entire session
    private var ourNodeNum = -1

    // The database of active nodes, index is the node number
    private val nodeDBbyNodeNum = mutableMapOf<Int, NodeInfo>()

    /// The database of active nodes, index is the node user ID string
    /// NOTE: some NodeInfos might be in only nodeDBbyNodeNum (because we don't yet know
    /// an ID).  But if a NodeInfo is in both maps, it must be one instance shared by
    /// both datastructures.
    private val nodeDBbyID = mutableMapOf<String, NodeInfo>()

    ///
    /// END OF MODEL
    ///

    /// Map a nodenum to a node, or throw an exception if not found
    private fun toNodeInfo(n: Int) = nodeDBbyNodeNum[n] ?: throw NodeNumNotFoundException(n)

    /// Map a nodenum to the nodeid string, or throw an exception if not present
    private fun toNodeID(n: Int) = toNodeInfo(n).user?.id

    /// given a nodenum, return a db entry - creating if necessary
    private fun getOrCreateNodeInfo(n: Int) =
        nodeDBbyNodeNum.getOrPut(n) { -> NodeInfo(n) }

    /// Map a userid to a node/ node num, or throw an exception if not found
    private fun toNodeInfo(id: String) = nodeDBbyID[id] ?: throw IdNotFoundException(id)

    private fun toNodeNum(id: String) = toNodeInfo(id).num

    /// A helper function that makes it easy to update node info objects
    private fun updateNodeInfo(nodeNum: Int, updatefn: (NodeInfo) -> Unit) {
        val info = getOrCreateNodeInfo(nodeNum)
        updatefn(info)
    }

    /// Generate a new mesh packet builder with our node as the sender, and the specified node num
    private fun newMeshPacketTo(idNum: Int) = MeshPacket.newBuilder().apply {
        from = ourNodeNum
        to = idNum
    }

    /// Generate a new mesh packet builder with our node as the sender, and the specified recipient
    private fun newMeshPacketTo(id: String) = newMeshPacketTo(toNodeNum(id))

    // Helper to make it easy to build a subpacket in the proper protobufs
    private fun buildMeshPacket(
        destId: String,
        initFn: MeshProtos.SubPacket.Builder.() -> Unit
    ): MeshPacket = newMeshPacketTo(destId).apply {
        payload = MeshProtos.MeshPayload.newBuilder().apply {
            addSubPackets(MeshProtos.SubPacket.newBuilder().also {
                initFn(it)
            }.build())
        }.build()
    }.build()

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedData(fromNum: Int, data: MeshProtos.Data) {
        val bytes = data.payload.toByteArray()
        val fromId = toNodeID(fromNum)

        /// the sending node ID if possible, else just its number
        val fromString = fromId ?: fromId.toString()

        when (data.typValue) {
            MeshProtos.Data.Type.CLEAR_TEXT_VALUE ->
                warn(
                    "TODO ignoring CLEAR_TEXT from $fromString: ${bytes.toString(
                        Charset.forName("UTF-8")
                    )}"
                )

            MeshProtos.Data.Type.CLEAR_READACK_VALUE ->
                warn(
                    "TODO ignoring CLEAR_READACK from $fromString"
                )

            MeshProtos.Data.Type.SIGNAL_OPAQUE_VALUE ->
                if (fromId == null)
                    error("Ignoring opaque from $fromNum because we don't yet know its ID")
                else {
                    debug("Received opaque from $fromId ${bytes.size}")
                    broadcastReceivedOpaque(fromId, bytes, data.typValue)
                }
            else -> TODO()
        }
    }

    /// Update our DB of users based on someone sending out a User subpacket
    private fun handleReceivedUser(fromNum: Int, p: MeshProtos.User) {
        updateNodeInfo(fromNum) {
            it.user = MeshUser(p.id, p.longName, p.shortName)

            // This might have been the first time we know an ID for this node, so also update the by ID map
            nodeDBbyID[p.id] = it
        }
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        val toNum = packet.to

        val payload = packet.payload
        payload.subPacketsList.forEach { p ->
            when (p.variantCase.number) {
                MeshProtos.SubPacket.POSITION_FIELD_NUMBER ->
                    updateNodeInfo(fromNum) {
                        it.position = Position(
                            p.position.latitude,
                            p.position.longitude,
                            p.position.altitude
                        )
                    }
                MeshProtos.SubPacket.TIME_FIELD_NUMBER ->
                    updateNodeInfo(fromNum) {
                        it.lastSeen = p.time.msecs
                    }
                MeshProtos.SubPacket.DATA_FIELD_NUMBER ->
                    handleReceivedData(fromNum, p.data)

                MeshProtos.SubPacket.USER_FIELD_NUMBER ->
                    handleReceivedUser(fromNum, p.user)
                MeshProtos.SubPacket.WANT_NODE_FIELD_NUMBER -> {
                    // This is managed by the radio on its own
                    debug("Ignoring WANT_NODE from $fromNum")
                }
                MeshProtos.SubPacket.DENY_NODE_FIELD_NUMBER -> {
                    // This is managed by the radio on its own
                    debug("Ignoring DENY_NODE from $fromNum to $toNum")
                }
                else -> TODO("Unexpected SubPacket variant")
            }
        }
    }

    private fun handleReceivedNodeInfo(info: MeshProtos.NodeInfo) {
        TODO()
    }

    /**
     * Receives messages from our BT radio service and processes them to update our model
     * and send to clients as needed.
     */
    private val radioInterfaceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val proto = MeshProtos.FromRadio.parseFrom(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
            info("Received from radio service: $proto")
            when (proto.variantCase.number) {
                MeshProtos.FromRadio.PACKET_FIELD_NUMBER -> handleReceivedMeshPacket(proto.packet)
                MeshProtos.FromRadio.NODE_INFO_FIELD_NUMBER -> handleReceivedNodeInfo(proto.nodeInfo)
                else -> TODO("Unexpected FromRadio variant")
            }
        }
    }

    private val binder = object : IMeshService.Stub() {
        // Note: bound methods don't get properly exception caught/logged, so do that with a wrapper
        // per https://blog.classycode.com/dealing-with-exceptions-in-aidl-9ba904c6d63

        override fun setOwner(myId: String, longName: String, shortName: String) =
            exceptionsToStrings {
                error("TODO setOwner $myId : $longName : $shortName")

                val user = MeshProtos.User.newBuilder().also {
                    it.id = myId
                    it.longName = longName
                    it.shortName = shortName
                }.build()

                // Also update our own map for our nodenum, by handling the packet just like packets from other users
                if (ourNodeNum != -1) {
                    handleReceivedUser(ourNodeNum, user)
                }

                sendToRadio(ToRadio.newBuilder().apply {
                    this.setOwner = user
                })
            }

        override fun sendData(destId: String, payloadIn: ByteArray, typ: Int) =
            exceptionsToStrings {
                info("sendData $destId <- ${payloadIn.size} bytes")

                // encapsulate our payload in the proper protobufs and fire it off
                val packet = buildMeshPacket(destId) {
                    data = MeshProtos.Data.newBuilder().also {
                        it.typ = MeshProtos.Data.Type.SIGNAL_OPAQUE
                        it.payload = ByteString.copyFrom(payloadIn)
                    }.build()
                }

                sendToRadio(ToRadio.newBuilder().apply {
                    this.packet = packet
                })
            }

        override fun getOnline(): Array<String> = exceptionReporter {
            val r = nodeDBbyID.keys.toTypedArray()
            info("in getOnline, count=${r.size}")
            // return arrayOf("+16508675309")
            r
        }

        override fun isConnected(): Boolean = exceptionReporter {
            val r = this@MeshService.isConnected
            info("in isConnected=r")
            r
        }
    }
}