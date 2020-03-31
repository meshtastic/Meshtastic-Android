package com.geeksville.mesh.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.ServiceClient
import com.geeksville.mesh.*
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.util.Exceptions
import com.geeksville.util.exceptionReporter
import com.geeksville.util.toOneLineString
import com.geeksville.util.toRemoteExceptions
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.protobuf.ByteString
import java.nio.charset.Charset


class RadioNotConnectedException() : Exception("Not connected to radio")


/**
 * Handles all the communication with android apps.  Also keeps an internal model
 * of the network state.
 *
 * Note: this service will go away once all clients are unbound from it.
 */
class MeshService : Service(), Logging {

    companion object : Logging {

        /// Intents broadcast by MeshService
        const val ACTION_RECEIVED_DATA = "$prefix.RECEIVED_DATA"
        const val ACTION_NODE_CHANGE = "$prefix.NODE_CHANGE"
        const val ACTION_MESH_CONNECTED = "$prefix.MESH_CONNECTED"

        class IdNotFoundException(id: String) : Exception("ID not found $id")
        class NodeNumNotFoundException(id: Int) : Exception("NodeNum not found $id")
        class NotInMeshException() : Exception("We are not yet in a mesh")

        /// Helper function to start running our service, returns the intent used to reach it
        /// or null if the service could not be started (no bluetooth or no bonded device set)
        fun startService(context: Context): Intent? {
            if (RadioInterfaceService.getBondedDeviceAddress(context) == null) {
                warn("No mesh radio is bonded, not starting service")
                return null
            } else {
                // bind to our service using the same mechanism an external client would use (for testing coverage)
                // The following would work for us, but not external users
                //val intent = Intent(this, MeshService::class.java)
                //intent.action = IMeshService::class.java.name
                val intent = Intent()
                intent.setClassName(
                    "com.geeksville.mesh",
                    "com.geeksville.mesh.service.MeshService"
                )

                // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
                // listening for the bluetooth packets arriving from the radio.  And when they arrive forward them
                // to Signal or whatever.

                logAssert(
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }) != null
                )

                return intent
            }
        }

        /// A model object for a Text message
        data class TextMessage(val fromId: String, val text: String)
    }

    /// A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()

    val radio = ServiceClient {
        IRadioInterfaceService.Stub.asInterface(it)
    }

    /*
    see com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // ACTION_MESH_CONNECTED for losing/gaining connection to the packet radio (note, this is not
    the same as RadioInterfaceService.RADIO_CONNECTED_ACTION, because it implies we have assembled a valid
    node db.
     */

    private fun explicitBroadcast(intent: Intent) {
        sendBroadcast(intent) // We also do a regular (not explicit broadcast) so any context-registered rceivers will work
        clientPackages.forEach {
            intent.setClassName(it.value, it.key)
            sendBroadcast(intent)
        }
    }

    private val locationCallback = object : LocationCallback() {
        private var lastSendMsec = 0L

        override fun onLocationResult(locationResult: LocationResult) {
            exceptionReporter {
                super.onLocationResult(locationResult)
                var l = locationResult.lastLocation

                // Docs say lastLocation should always be !null if there are any locations, but that's not the case
                if (l == null) {
                    // try to only look at the accurate locations
                    val locs =
                        locationResult.locations.filter { !it.hasAccuracy() || it.accuracy < 200 }
                    l = locs.lastOrNull()
                }
                if (l != null) {
                    info("got location $l")
                    if (l.hasAccuracy() && l.accuracy >= 200) // if more than 200 meters off we won't use it
                        warn("accuracy ${l.accuracy} is too poor to use")
                    else {
                        val now = System.currentTimeMillis()

                        // we limit our sends onto the lora net to a max one once every FIXME
                        val sendLora = (now - lastSendMsec >= 30 * 1000)
                        if (sendLora)
                            lastSendMsec = now
                        try {
                            sendPosition(
                                l.latitude, l.longitude, l.altitude.toInt(),
                                destNum = if (sendLora) NODENUM_BROADCAST else myNodeNum,
                                wantResponse = sendLora
                            )
                        } catch (ex: RadioNotConnectedException) {
                            warn("Lost connection to radio, stopping location requests")
                            onConnectionChanged(false)
                        }
                    }
                }
            }
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null

    /**
     * start our location requests (if they weren't already running)
     *
     * per https://developer.android.com/training/location/change-location-settings
     */
    @SuppressLint("MissingPermission")
    private fun startLocationRequests() {
        if (fusedLocationClient == null) {
            GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

            val request = LocationRequest.create().apply {
                interval =
                    5 * 60 * 1000 // FIXME, do more like once every 5 mins while we are connected to our radio _and_ someone else is in the mesh

                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            val builder = LocationSettingsRequest.Builder().addLocationRequest(request)
            val locationClient = LocationServices.getSettingsClient(this)
            val locationSettingsResponse = locationClient.checkLocationSettings(builder.build())

            locationSettingsResponse.addOnSuccessListener {
                debug("We are now successfully listening to the GPS")
            }

            locationSettingsResponse.addOnFailureListener { exception ->
                errormsg("Failed to listen to GPS")
                if (exception is ResolvableApiException) {
                    Exceptions.report(exception) // FIXME, not yet implemented, report failure to mothership
                    exceptionReporter {
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.

                        // FIXME
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        /* exception.startResolutionForResult(
                            this@MainActivity,
                            REQUEST_CHECK_SETTINGS
                        ) */
                    }
                } else
                    Exceptions.report(exception)
            }

            val client = LocationServices.getFusedLocationProviderClient(this)

            // FIXME - should we use Looper.myLooper() in the third param per https://github.com/android/location-samples/blob/432d3b72b8c058f220416958b444274ddd186abd/LocationUpdatesForegroundService/app/src/main/java/com/google/android/gms/location/sample/locationupdatesforegroundservice/LocationUpdatesService.java
            client.requestLocationUpdates(request, locationCallback, null)

            fusedLocationClient = client
        }
    }

    private fun stopLocationRequests() {
        if (fusedLocationClient != null) {
            debug("Stopping location requests")
            GeeksvilleApplication.analytics.track("location_stop")
            fusedLocationClient?.removeLocationUpdates(locationCallback)
            fusedLocationClient = null
        }
    }

    /**
     * The RECEIVED_OPAQUE:
     * Payload will be the raw bytes which were contained within a MeshPacket.Opaque field
     * Sender will be a user ID string
     * Type will be the Data.Type enum code for this payload
     */
    private fun broadcastReceivedData(senderId: String, payload: ByteArray, typ: Int) {
        val intent = Intent(ACTION_RECEIVED_DATA)
        intent.putExtra(EXTRA_SENDER, senderId)
        intent.putExtra(EXTRA_PAYLOAD, payload)
        intent.putExtra(EXTRA_TYP, typ)
        explicitBroadcast(intent)
    }

    private fun broadcastNodeChange(info: NodeInfo) {
        debug("Broadcasting node change $info")
        val intent = Intent(ACTION_NODE_CHANGE)

        intent.putExtra(EXTRA_NODEINFO, info)
        explicitBroadcast(intent)
    }

    /// Safely access the radio service, if not connected an exception will be thrown
    private val connectedRadio: IRadioInterfaceService
        get() = (if (isConnected) radio.serviceP else null) ?: throw RadioNotConnectedException()

    /// Send a command/packet to our radio.  But cope with the possiblity that we might start up
    /// before we are fully bound to the RadioInterfaceService
    private fun sendToRadio(p: ToRadio.Builder) {
        val b = p.build().toByteArray()

        connectedRadio.sendToRadio(b)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
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
        notificationManager.createNotificationChannel(chan)
        return channelId
    }

    private val notifyId = 101
    val notificationManager: NotificationManager by lazy() {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /// This must be lazy because we use Context
    private val channelId: String by lazy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private val openAppIntent: PendingIntent by lazy() {
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)
    }

    /// A text message that has a arrived since the last notification update
    private var recentReceivedText: TextMessage? = null

    val summaryString
        get() = if (!isConnected)
            "No radio connected"
        else
            "Connected: $numOnlineNodes of $numNodes online"

    override fun toString() = summaryString

    /**
     * Generate a new version of our notification - reflecting current app state
     */
    private fun createNotification(): Notification {

        val notificationBuilder = NotificationCompat.Builder(this, channelId)

        val builder = notificationBuilder.setOngoing(true)
            .setPriority(PRIORITY_MIN)
            .setCategory(if (recentReceivedText != null) Notification.CATEGORY_SERVICE else Notification.CATEGORY_MESSAGE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(summaryString) // leave this off for now so our notification looks smaller
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)

        // FIXME, show information about the nearest node
        // if(shortContent != null) builder.setContentText(shortContent)

        // If a text message arrived include it with our notification
        recentReceivedText?.let { msg ->
            builder.setContentText("Message from ${msg.fromId}")

            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(msg.text)
            )
        }

        return builder.build()
    }

    /**
     * Update our notification with latest data
     */
    private fun updateNotification() {
        notificationManager.notify(notifyId, createNotification())
    }

    /**
     * tell android not to kill us
     */
    private fun startForeground() {
        startForeground(notifyId, createNotification())
    }

    override fun onCreate() {
        super.onCreate()

        info("Creating mesh service")
        startForeground()

        // we listen for messages from the radio receiver _before_ trying to create the service
        val filter = IntentFilter()
        filter.addAction(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
        filter.addAction(RadioInterfaceService.RADIO_CONNECTED_ACTION)
        registerReceiver(radioInterfaceReceiver, filter)

        // We in turn need to use the radiointerface service
        val intent = Intent(this, RadioInterfaceService::class.java)
        // intent.action = IMeshService::class.java.name
        radio.connect(this, intent, Context.BIND_AUTO_CREATE)

        // the rest of our init will happen once we are in radioConnection.onServiceConnected
    }


    override fun onDestroy() {
        info("Destroying mesh service")
        unregisterReceiver(radioInterfaceReceiver)
        radio.close()

        super.onDestroy()
    }


    ///
    /// BEGINNING OF MODEL - FIXME, move elsewhere
    ///

    /// special broadcast address
    val NODENUM_BROADCAST = 255

    // MyNodeInfo sent via special protobuf from radio
    data class MyNodeInfo(
        val myNodeNum: Int,
        val hasGPS: Boolean,
        val region: String,
        val model: String,
        val firmwareVersion: String
    )

    var myNodeInfo: MyNodeInfo? = null

    /// Is our radio connected to the phone?
    private var isConnected = false

    /// True after we've done our initial node db init
    private var haveNodeDB = false

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
    private fun toNodeInfo(n: Int) = nodeDBbyNodeNum[n] ?: throw NodeNumNotFoundException(
        n
    )

    /// Map a nodenum to the nodeid string, or throw an exception if not present
    private fun toNodeID(n: Int) = toNodeInfo(n).user?.id

    /// given a nodenum, return a db entry - creating if necessary
    private fun getOrCreateNodeInfo(n: Int) =
        nodeDBbyNodeNum.getOrPut(n) { -> NodeInfo(n) }

    /// Map a userid to a node/ node num, or throw an exception if not found
    private fun toNodeInfo(id: String) =
        nodeDBbyID[id]
            ?: throw IdNotFoundException(
                id
            )


    private val numNodes get() = nodeDBbyNodeNum.size

    /**
     * How many nodes are currently online (including our local node)
     */
    private val numOnlineNodes get() = nodeDBbyNodeNum.values.count { it.isOnline }

    private fun toNodeNum(id: String) = toNodeInfo(id).num

    /// A helper function that makes it easy to update node info objects
    private fun updateNodeInfo(nodeNum: Int, updatefn: (NodeInfo) -> Unit) {
        val info = getOrCreateNodeInfo(nodeNum)
        updatefn(info)

        // This might have been the first time we know an ID for this node, so also update the by ID map
        val userId = info.user?.id.orEmpty()
        if (userId.isNotEmpty())
            nodeDBbyID[userId] = info

        // parcelable is busted
        broadcastNodeChange(info)
    }

    /// My node num
    private val myNodeNum get() = myNodeInfo!!.myNodeNum

    /// My node ID string
    private val myNodeID get() = toNodeID(myNodeNum)

    /// Generate a new mesh packet builder with our node as the sender, and the specified node num
    private fun newMeshPacketTo(idNum: Int) = MeshPacket.newBuilder().apply {
        if (myNodeInfo == null)
            throw RadioNotConnectedException()

        from = myNodeNum
        to = idNum
    }

    /**
     * Generate a new mesh packet builder with our node as the sender, and the specified recipient
     *
     * If id is null we assume a broadcast message
     */
    private fun newMeshPacketTo(id: String?) =
        newMeshPacketTo(if (id != null) toNodeNum(id) else NODENUM_BROADCAST)

    /**
     * Helper to make it easy to build a subpacket in the proper protobufs
     *
     * If destId is null we assume a broadcast message
     */
    private fun buildMeshPacket(
        destId: String?,
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

        fun forwardData() {
            if (fromId == null)
                warn("Ignoring data from $fromNum because we don't yet know its ID")
            else {
                debug("Received data from $fromId ${bytes.size}")
                broadcastReceivedData(fromId, bytes, data.typValue)
            }
        }

        when (data.typValue) {
            MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                val text = bytes.toString(Charset.forName("UTF-8"))

                debug("Received CLEAR_TEXT from $fromString")

                recentReceivedText = TextMessage(fromString, text)
                updateNotification()
                forwardData()
            }

            MeshProtos.Data.Type.CLEAR_READACK_VALUE ->
                warn(
                    "TODO ignoring CLEAR_READACK from $fromString"
                )

            MeshProtos.Data.Type.SIGNAL_OPAQUE_VALUE ->
                forwardData()

            else -> TODO()
        }

        GeeksvilleApplication.analytics.track(
            "data_receive",
            DataPair("num_bytes", bytes.size),
            DataPair("type", data.typValue)
        )
    }

    /// Update our DB of users based on someone sending out a User subpacket
    private fun handleReceivedUser(fromNum: Int, p: MeshProtos.User) {
        updateNodeInfo(fromNum) {
            val oldId = it.user?.id.orEmpty()
            it.user = MeshUser(
                if (p.id.isNotEmpty()) p.id else oldId, // If the new update doesn't contain an ID keep our old value
                p.longName,
                p.shortName
            )
        }
    }

    /// Update our DB of users based on someone sending out a Position subpacket
    private fun handleReceivedPosition(fromNum: Int, p: MeshProtos.Position) {
        updateNodeInfo(fromNum) {
            it.position = Position(
                p.latitude,
                p.longitude,
                p.altitude,
                if (p.time != 0) p.time else it.position?.time
                    ?: 0 // if this position didn't include time, just keep our old one
            )
        }
    }

    /// If packets arrive before we have our node DB, we delay parsing them until the DB is ready
    private val earlyPackets = mutableListOf<MeshPacket>()

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        if (haveNodeDB) {
            processReceivedMeshPacket(packet)
            onNodeDBChanged()
        } else {
            earlyPackets.add(packet)
            logAssert(earlyPackets.size < 128) // The max should normally be about 32, but if the device is messed up it might try to send forever
        }
    }

    /// Process any packets that showed up too early
    private fun processEarlyPackets() {
        earlyPackets.forEach { processReceivedMeshPacket(it) }
        earlyPackets.clear()
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun processReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        //val toNum = packet.to

        val p = packet.payload

        // Update last seen for the node that sent the packet, but also for _our node_ because anytime a packet passes
        // through our node on the way to the phone that means that local node is also alive in the mesh
        updateNodeInfo(fromNum) {
            // Update our last seen based on any valid timestamps.  If the device didn't provide a timestamp make one
            val lastSeen =
                if (packet.rxTime != 0) packet.rxTime else currentSecond()

            it.position = it.position?.copy(time = lastSeen)
        }
        updateNodeInfo(myNodeNum) {
            it.position = it.position?.copy(time = currentSecond())
        }

        when (p.variantCase.number) {
            MeshProtos.SubPacket.POSITION_FIELD_NUMBER ->
                handleReceivedPosition(fromNum, p.position)

            MeshProtos.SubPacket.DATA_FIELD_NUMBER ->
                handleReceivedData(fromNum, p.data)

            MeshProtos.SubPacket.USER_FIELD_NUMBER ->
                handleReceivedUser(fromNum, p.user)
            else -> TODO("Unexpected SubPacket variant")
        }
    }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()

    /// We are reconnecting to a radio, redownload the full state.  This operation might take hundreds of milliseconds
    private fun reinitFromRadio() {
        // Read the MyNodeInfo object
        val myInfo = MeshProtos.MyNodeInfo.parseFrom(
            connectedRadio.readMyNode()
        )

        val mi = with(myInfo) {
            MyNodeInfo(myNodeNum, hasGps, region, hwModel, firmwareVersion)
        }

        myNodeInfo = mi

        /// Track types of devices and firmware versions in use
        GeeksvilleApplication.analytics.setUserInfo(
            DataPair("region", mi.region),
            DataPair("firmware", mi.firmwareVersion),
            DataPair("has_gps", mi.hasGPS),
            DataPair("hw_model", mi.model),
            DataPair("dev_error_count", myInfo.errorCount)
        )

        if (myInfo.errorCode != 0) {
            GeeksvilleApplication.analytics.track(
                "dev_error",
                DataPair("code", myInfo.errorCode),
                DataPair("address", myInfo.errorAddress),

                // We also include this info, because it is required to correctly decode address from the map file
                DataPair("firmware", mi.firmwareVersion),
                DataPair("hw_model", mi.model),
                DataPair("region", mi.region)
            )
        }

        // Ask for the current node DB
        connectedRadio.restartNodeInfo()

        // read all the infos until we get back null
        var infoBytes = connectedRadio.readNodeInfo()
        while (infoBytes != null) {
            val info =
                MeshProtos.NodeInfo.parseFrom(infoBytes)
            debug("Received initial nodeinfo $info")

            // Just replace/add any entry
            updateNodeInfo(info.num) {
                if (info.hasUser())
                    it.user =
                        MeshUser(
                            info.user.id,
                            info.user.longName,
                            info.user.shortName
                        )

                if (info.hasPosition()) {
                    // For the local node, it might not be able to update its times because it doesn't have a valid GPS reading yet
                    // so if the info is for _our_ node we always assume time is current
                    val time =
                        if (it.num == mi.myNodeNum) currentSecond() else info.position.time

                    it.position = Position(
                        info.position.latitude,
                        info.position.longitude,
                        info.position.altitude,
                        time
                    )
                }
            }

            // advance to next
            infoBytes = connectedRadio.readNodeInfo()
        }

        haveNodeDB = true // we've done our initial node db initialization
        processEarlyPackets() // handle any packets that showed up while we were booting

        onNodeDBChanged()
    }

    /// If we just changed our nodedb, we might want to do somethings
    private fun onNodeDBChanged() {
        updateNotification()

        // we don't ask for GPS locations from android if our device has a built in GPS
        if (!myNodeInfo!!.hasGPS) {
            // If we have at least one other person in the mesh, send our GPS position otherwise stop listening to GPS

            if (numOnlineNodes >= 2)
                startLocationRequests()
            else
                stopLocationRequests()
        } else
            debug("Our radio has a built in GPS, so not reading GPS in phone")
    }

    /// Called when we gain/lose connection to our radio
    private fun onConnectionChanged(c: Boolean) {
        debug("onConnectionChanged connected=$c")
        isConnected = c
        if (c) {
            // Do our startup init
            try {
                reinitFromRadio()

                val radioModel = DataPair("radio_model", myNodeInfo?.model ?: "unknown")
                GeeksvilleApplication.analytics.track(
                    "mesh_connect",
                    DataPair("num_nodes", numNodes),
                    DataPair("num_online", numOnlineNodes),
                    radioModel
                )

                // Once someone connects to hardware start tracking the approximate number of nodes in their mesh
                // this allows us to collect stats on what typical mesh size is and to tell difference between users who just
                // downloaded the app, vs has connected it to some hardware.
                GeeksvilleApplication.analytics.setUserInfo(
                    DataPair("num_nodes", numNodes),
                    radioModel
                )
            } catch (ex: RemoteException) {
                // It seems that when the ESP32 goes offline it can briefly come back for a 100ms ish which
                // causes the phone to try and reconnect.  If we fail downloading our initial radio state we don't want to
                // claim we have a valid connection still
                isConnected = false;
                throw ex; // Important to rethrow so that we don't tell the app all is well
            }
        } else {
            // lost radio connection, therefore no need to keep listening to GPS
            stopLocationRequests()

            GeeksvilleApplication.analytics.track(
                "mesh_disconnect",
                DataPair("num_nodes", numNodes),
                DataPair("num_online", numOnlineNodes)
            )
        }

        updateNotification()
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
                RadioInterfaceService.RADIO_CONNECTED_ACTION -> {
                    try {
                        onConnectionChanged(intent.getBooleanExtra(EXTRA_CONNECTED, false))

                        // forward the connection change message to anyone who is listening to us. but change the action
                        // to prevent an infinite loop from us receiving our own broadcast. ;-)
                        intent.action = ACTION_MESH_CONNECTED
                        explicitBroadcast(intent)
                    } catch (ex: RemoteException) {
                        // This can happen sometimes (especially if the device is slowly dying due to killing power, don't report to crashlytics
                        warn("Abandoning reconnect attempt, due to errors during init: ${ex.message}")
                    }
                }

                RadioInterfaceService.RECEIVE_FROMRADIO_ACTION -> {
                    val proto =
                        MeshProtos.FromRadio.parseFrom(
                            intent.getByteArrayExtra(
                                EXTRA_PAYLOAD
                            )!!
                        )
                    info("Received from radio service: ${proto.toOneLineString()}")
                    when (proto.variantCase.number) {
                        MeshProtos.FromRadio.PACKET_FIELD_NUMBER -> handleReceivedMeshPacket(
                            proto.packet
                        )

                        else -> TODO("Unexpected FromRadio variant")
                    }
                }

                else -> TODO("Unexpected radio interface broadcast")
            }
        }
    }

    /// Send a position (typically from our built in GPS) into the mesh
    private fun sendPosition(
        lat: Double,
        lon: Double,
        alt: Int,
        destNum: Int = NODENUM_BROADCAST,
        wantResponse: Boolean = false
    ) {
        debug("Sending our position to=$destNum lat=$lat, lon=$lon, alt=$alt")

        val position = MeshProtos.Position.newBuilder().also {
            it.latitude = lat
            it.longitude = lon
            it.altitude = alt
            it.time = currentSecond() // Include our current timestamp
        }.build()

        // encapsulate our payload in the proper protobufs and fire it off
        val packet = newMeshPacketTo(destNum)

        packet.payload = MeshProtos.SubPacket.newBuilder().also {
            it.position = position
            it.wantResponse = wantResponse
        }.build()

        // Also update our own map for our nodenum, by handling the packet just like packets from other users
        handleReceivedPosition(myNodeInfo!!.myNodeNum, position)

        // send the packet into the mesh
        sendToRadio(ToRadio.newBuilder().apply {
            this.packet = packet.build()
        })
    }

    private val binder = object : IMeshService.Stub() {
        // Note: bound methods don't get properly exception caught/logged, so do that with a wrapper
        // per https://blog.classycode.com/dealing-with-exceptions-in-aidl-9ba904c6d63
        override fun subscribeReceiver(packageName: String, receiverName: String) =
            toRemoteExceptions {
                clientPackages[receiverName] = packageName
            }

        override fun getMyId() = toRemoteExceptions { myNodeID }

        override fun setOwner(myId: String?, longName: String, shortName: String) =
            toRemoteExceptions {
                debug("SetOwner $myId : $longName : $shortName")

                val user = MeshProtos.User.newBuilder().also {
                    if (myId != null)  // Only set the id if it was provided
                        it.id = myId
                    it.longName = longName
                    it.shortName = shortName
                }.build()

                // Also update our own map for our nodenum, by handling the packet just like packets from other users
                if (myNodeInfo != null) {
                    handleReceivedUser(myNodeInfo!!.myNodeNum, user)
                }

                // set my owner info
                connectedRadio.writeOwner(user.toByteArray())
            }

        override fun sendData(destId: String?, payloadIn: ByteArray, typ: Int) =
            toRemoteExceptions {
                info("sendData dest=$destId <- ${payloadIn.size} bytes")

                // encapsulate our payload in the proper protobufs and fire it off
                val packet = buildMeshPacket(destId) {
                    data = MeshProtos.Data.newBuilder().also {
                        it.typ = MeshProtos.Data.Type.forNumber(typ)
                        it.payload = ByteString.copyFrom(payloadIn)
                    }.build()
                }

                sendToRadio(ToRadio.newBuilder().apply {
                    this.packet = packet
                })

                GeeksvilleApplication.analytics.track(
                    "data_send",
                    DataPair("num_bytes", payloadIn.size),
                    DataPair("type", typ)
                )
            }

        override fun getRadioConfig(): ByteArray = toRemoteExceptions {
            connectedRadio.readRadioConfig()
        }

        override fun setRadioConfig(payload: ByteArray) = toRemoteExceptions {
            connectedRadio.writeRadioConfig(payload)
        }

        override fun getNodes(): Array<NodeInfo> = toRemoteExceptions {
            val r = nodeDBbyID.values.toTypedArray()
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