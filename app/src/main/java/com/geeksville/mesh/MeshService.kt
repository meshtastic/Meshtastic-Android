package com.geeksville.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import com.geeksville.android.Logging
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.util.exceptionReporter
import com.geeksville.util.toOneLineString
import com.geeksville.util.toRemoteExceptions
import com.google.protobuf.ByteString
import java.nio.charset.Charset


class RadioNotConnectedException() : Exception("Can't find radio")

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
        class NotInMeshException() : Exception("We are not yet in a mesh")

        /// If we haven't yet received a node number from the radio
        private const val NODE_NUM_UNKNOWN = -2

        /// If the radio hasn't yet joined a mesh (i.e. no nodenum assigned)
        private const val NODE_NUM_NO_MESH = -1
    }

    /// A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()

    private var radioService: IRadioInterfaceService? = null

    /*
    see com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
     */

    private fun explicitBroadcast(intent: Intent) {
        clientPackages.forEach {
            intent.setClassName(it.value, it.key)
            sendBroadcast(intent)
        }
    }

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
        explicitBroadcast(intent)
    }

    private fun broadcastNodeChange(nodeId: String, isOnline: Boolean) {
        val intent = Intent("$prefix.NODE_CHANGE")
        intent.putExtra(EXTRA_ID, nodeId)
        intent.putExtra(EXTRA_ONLINE, isOnline)
        explicitBroadcast(intent)
    }

    /// Safely access the radio service, if not connected an exception will be thrown
    private val connectedRadio: IRadioInterfaceService
        get() {
            val s = radioService
            if (s == null || !isConnected)
                throw RadioNotConnectedException()

            return s
        }

    /// Send a command/packet to our radio.  But cope with the possiblity that we might start up
    /// before we are fully bound to the RadioInterfaceService
    private fun sendToRadio(p: ToRadio.Builder) {
        val b = p.build().toByteArray()

        connectedRadio.sendToRadio(b)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private val radioConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val m = IRadioInterfaceService.Stub.asInterface(service)
            radioService = m
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = "My Background Service"
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun startForeground() {

        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setPriority(PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            //.setContentTitle("Meshtastic") // leave this off for now so our notification looks smaller
            //.setContentText("Listening for mesh...")
            .build()
        startForeground(101, notification)
    }

    override fun onCreate() {
        super.onCreate()

        info("Creating mesh service")

        /*
        // This intent will be used if the user clicks on the item in the status bar
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, 0
        )

        val notification: Notification = NotificationCompat.Builder(this)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Meshtastic")
            .setContentText("Listening for mesh...")
            .setContentIntent(pendingIntent).build()

        // We are required to call this within a few seconds of create
        startForeground(1337, notification)

         */
        startForeground()

        // we listen for messages from the radio receiver _before_ trying to create the service
        val filter = IntentFilter()
        filter.addAction(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
        filter.addAction(RadioInterfaceService.CONNECTCHANGED_ACTION)
        registerReceiver(radioInterfaceReceiver, filter)

        // We in turn need to use the radiointerface service
        val intent = Intent(this, RadioInterfaceService::class.java)
        // intent.action = IMeshService::class.java.name
        logAssert(bindService(intent, radioConnection, Context.BIND_AUTO_CREATE))

        // the rest of our init will happen once we are in radioConnection.onServiceConnected
    }


    override fun onDestroy() {
        info("Destroying mesh service")
        unregisterReceiver(radioInterfaceReceiver)
        unbindService(radioConnection)
        radioService = null

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
    private var ourNodeNum = NODE_NUM_UNKNOWN

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
    private fun toNodeInfo(id: String) =
        nodeDBbyID[id]
            ?: throw IdNotFoundException(id)

    // ?: getOrCreateNodeInfo(10) // FIXME hack for now -  throw IdNotFoundException(id)

    private fun toNodeNum(id: String) = toNodeInfo(id).num

    /// A helper function that makes it easy to update node info objects
    private fun updateNodeInfo(nodeNum: Int, updatefn: (NodeInfo) -> Unit) {
        val info = getOrCreateNodeInfo(nodeNum)
        updatefn(info)

        // This might have been the first time we know an ID for this node, so also update the by ID map
        val userId = info.user?.id
        if (userId != null)
            nodeDBbyID[userId] = info
    }

    /// Generate a new mesh packet builder with our node as the sender, and the specified node num
    private fun newMeshPacketTo(idNum: Int) = MeshPacket.newBuilder().apply {
        from = ourNodeNum

        if (from == NODE_NUM_NO_MESH)
            throw NotInMeshException()
        else if (from == NODE_NUM_UNKNOWN)
            throw RadioNotConnectedException()

        to = idNum
    }

    /// Generate a new mesh packet builder with our node as the sender, and the specified recipient
    private fun newMeshPacketTo(id: String) = newMeshPacketTo(toNodeNum(id))

    // Helper to make it easy to build a subpacket in the proper protobufs
    private fun buildMeshPacket(
        destId: String,
        initFn: MeshProtos.SubPacket.Builder.() -> Unit
    ): MeshPacket = newMeshPacketTo(destId).apply {
        payload = MeshProtos.SubPacket.newBuilder().also {
            initFn(it)
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
        }
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        val toNum = packet.to

        val p = packet.payload
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
                    it.lastSeen = p.time
                }
            MeshProtos.SubPacket.DATA_FIELD_NUMBER ->
                handleReceivedData(fromNum, p.data)

            MeshProtos.SubPacket.USER_FIELD_NUMBER ->
                handleReceivedUser(fromNum, p.user)
            else -> TODO("Unexpected SubPacket variant")
        }
    }


    /// Called when we gain/lose connection to our radio
    private fun onConnectionChanged(c: Boolean) {
        debug("onConnectionChanged connected=$c")
        isConnected = c
        if (c) {
            // Do our startup init

            // FIXME - don't do this until after we see that the radio is connected to the phone
            //val sim = SimRadio(this@MeshService)
            //sim.start() // Fake up our node id info and some past packets from other nodes

            val myInfo = MeshProtos.MyNodeInfo.parseFrom(connectedRadio.readMyNode())
            ourNodeNum = myInfo.myNodeNum

            // Ask for the current node DB 
            connectedRadio.restartNodeInfo()

            // read all the infos until we get back null
            var infoBytes = connectedRadio.readNodeInfo()
            while (infoBytes != null) {
                val info = MeshProtos.NodeInfo.parseFrom(infoBytes)
                debug("Received initial nodeinfo $info")

                // Just replace/add any entry
                updateNodeInfo(info.num) {
                    if (info.hasUser())
                        it.user = MeshUser(info.user.id, info.user.longName, info.user.shortName)

                    if (info.hasPosition())
                        it.position = Position(
                            info.position.latitude,
                            info.position.longitude,
                            info.position.altitude
                        )

                    it.lastSeen = info.lastSeen
                }

                // advance to next
                infoBytes = connectedRadio.readNodeInfo()
            }
        }
    }

    /**
     * Receives messages from our BT radio service and processes them to update our model
     * and send to clients as needed.
     */
    private val radioInterfaceReceiver = object : BroadcastReceiver() {

        // Important to never throw exceptions out of onReceive
        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {

            debug("Received broadcast ${intent.action}")
            when (intent.action) {
                RadioInterfaceService.CONNECTCHANGED_ACTION -> {
                    onConnectionChanged(intent.getBooleanExtra(EXTRA_CONNECTED, false))
                    explicitBroadcast(intent) // forward the connection change message to anyone who is listening to us
                }

                RadioInterfaceService.RECEIVE_FROMRADIO_ACTION -> {
                    val proto =
                        MeshProtos.FromRadio.parseFrom(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
                    info("Received from radio service: ${proto.toOneLineString()}")
                    when (proto.variantCase.number) {
                        MeshProtos.FromRadio.PACKET_FIELD_NUMBER -> handleReceivedMeshPacket(proto.packet)

                        else -> TODO("Unexpected FromRadio variant")
                    }
                }

                else -> TODO("Unexpected radio interface broadcast")
            }
        }
    }

    private val binder = object : IMeshService.Stub() {
        // Note: bound methods don't get properly exception caught/logged, so do that with a wrapper
        // per https://blog.classycode.com/dealing-with-exceptions-in-aidl-9ba904c6d63
        override fun subscribeReceiver(packageName: String, receiverName: String) =
            toRemoteExceptions {
                clientPackages[receiverName] = packageName
            }

        override fun setOwner(myId: String, longName: String, shortName: String) =
            toRemoteExceptions {
                error("TODO setOwner $myId : $longName : $shortName")

                val user = MeshProtos.User.newBuilder().also {
                    it.id = myId
                    it.longName = longName
                    it.shortName = shortName
                }.build()

                // Also update our own map for our nodenum, by handling the packet just like packets from other users
                if (ourNodeNum >= 0) {
                    handleReceivedUser(ourNodeNum, user)
                }

                // set my owner info
                connectedRadio.writeOwner(user.toByteArray())
            }

        override fun sendData(destId: String, payloadIn: ByteArray, typ: Int) =
            toRemoteExceptions {
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

        override fun getOnline(): Array<String> = toRemoteExceptions {
            val r = nodeDBbyID.keys.toTypedArray()
            info("in getOnline, count=${r.size}")
            // return arrayOf("+16508675309")
            r
        }

        override fun isConnected(): Boolean = toRemoteExceptions {
            val r = this@MeshService.isConnected
            info("in isConnected=$r")
            r
        }
    }
}