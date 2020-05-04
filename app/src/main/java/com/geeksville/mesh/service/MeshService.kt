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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import androidx.core.content.edit
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.ServiceClient
import com.geeksville.mesh.*
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.mesh.R
import com.geeksville.util.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


private val errorHandler = CoroutineExceptionHandler { _, exception ->
    Exceptions.report(exception, "MeshService-coroutine", "coroutine-exception")
}

/// Wrap launch with an exception handler, FIXME, move into a utility lib
fun CoroutineScope.handledLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = this.launch(context = context + errorHandler, start = start, block = block)

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
                    // we have some samsung devices failing with https://issuetracker.google.com/issues/76112072#comment56 not sure what the fix is yet
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }) != null
            )

            return intent
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED,
        DEVICE_SLEEP // device is in LS sleep state, it will reconnected to us over bluetooth once it has data
    }

    /// A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()

    val radio = ServiceClient {
        IRadioInterfaceService.Stub.asInterface(it)
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /// The current state of our connection
    private var connectionState = ConnectionState.DISCONNECTED

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
            serviceScope.handledLaunch {
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
                    info("got phone location")
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
                        } catch (ex: RemoteException) { // Really a RadioNotConnected exception, but it has changed into this type via remoting
                            warn("Lost connection to radio, stopping location requests")
                            onConnectionChanged(ConnectionState.DEVICE_SLEEP)
                        }
                    }
                }
            }
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private fun warnUserAboutLocation() {
        Toast.makeText(
            this,
            getString(R.string.location_disabled),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * start our location requests (if they weren't already running)
     *
     * per https://developer.android.com/training/location/change-location-settings
     */
    @SuppressLint("MissingPermission")
    @UiThread
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
                    // Exceptions.report(exception) // FIXME, not yet implemented, report failure to mothership
                    exceptionReporter {
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.

                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        // exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)

                        // For now just punt and show a dialog
                        warnUserAboutLocation()
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
     * Payload will be a DataPacket
     */
    private fun broadcastReceivedData(payload: DataPacket) {
        val intent = Intent(ACTION_RECEIVED_DATA)
        intent.putExtra(EXTRA_PAYLOAD, payload)
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
        get() = (if (connectionState == ConnectionState.CONNECTED) radio.serviceP else null)
            ?: throw RadioNotConnectedException()

    /// Send a command/packet to our radio.  But cope with the possiblity that we might start up
    /// before we are fully bound to the RadioInterfaceService
    private fun sendToRadio(p: ToRadio.Builder) {
        val b = p.build().toByteArray()

        connectedRadio.sendToRadio(b)
    }

    /**
     * Send a mesh packet to the radio, if the radio is not currently connected this function will throw NotConnectedException
     */
    private fun sendToRadio(packet: MeshPacket) {
        sendToRadio(ToRadio.newBuilder().apply {
            this.packet = packet
        })
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
    private var recentReceivedText: DataPacket? = null

    private val summaryString
        get() = when (connectionState) {
            ConnectionState.CONNECTED -> getString(R.string.connected_count).format(
                numOnlineNodes,
                numNodes
            )
            ConnectionState.DISCONNECTED -> getString(R.string.disconnected)
            ConnectionState.DEVICE_SLEEP -> getString(R.string.device_sleeping)
        }


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
        recentReceivedText?.let { packet ->
            // Try to show the human name of the sender if possible
            val sender = nodeDBbyID[packet.from]?.user?.longName ?: packet.from
            builder.setContentText("Message from $sender")

            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(packet.bytes.toString(utf8))
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

        // Switch to the IO thread
        serviceScope.handledLaunch {
            loadSettings() // Load our last known node DB

            // we listen for messages from the radio receiver _before_ trying to create the service
            val filter = IntentFilter()
            filter.addAction(RadioInterfaceService.RECEIVE_FROMRADIO_ACTION)
            filter.addAction(RadioInterfaceService.RADIO_CONNECTED_ACTION)
            registerReceiver(radioInterfaceReceiver, filter)

            // We in turn need to use the radiointerface service
            val intent = Intent(this@MeshService, RadioInterfaceService::class.java)
            // intent.action = IMeshService::class.java.name
            radio.connect(this@MeshService, intent, Context.BIND_AUTO_CREATE)

            // the rest of our init will happen once we are in radioConnection.onServiceConnected
        }
    }


    override fun onDestroy() {
        info("Destroying mesh service")

        // This might fail if we get destroyed before the handledLaunch completes
        ignoreException {
            unregisterReceiver(radioInterfaceReceiver)
        }

        radio.close()
        saveSettings()

        super.onDestroy()
        serviceJob.cancel()
    }


    ///
    /// BEGINNING OF MODEL - FIXME, move elsewhere
    ///

    /// special broadcast address
    val NODENUM_BROADCAST = 255

    // MyNodeInfo sent via special protobuf from radio
    @Serializable
    data class MyNodeInfo(
        val myNodeNum: Int,
        val hasGPS: Boolean,
        val region: String,
        val model: String,
        val firmwareVersion: String
    )

    /// Our saved preferences as stored on disk
    @Serializable
    private data class SavedSettings(
        val nodeDB: Array<NodeInfo>,
        val myInfo: MyNodeInfo,
        val messages: Array<DataPacket>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SavedSettings

            if (!nodeDB.contentEquals(other.nodeDB)) return false
            if (myInfo != other.myInfo) return false
            if (!messages.contentEquals(other.messages)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = nodeDB.contentHashCode()
            result = 31 * result + myInfo.hashCode()
            result = 31 * result + messages.contentHashCode()
            return result
        }
    }

    private fun getPrefs() = getSharedPreferences("service-prefs", Context.MODE_PRIVATE)

    /// Save information about our mesh to disk, so we will have it when we next start the service (even before we hear from our device)
    private fun saveSettings() {
        myNodeInfo?.let { myInfo ->
            val settings = SavedSettings(
                myInfo = myInfo,
                nodeDB = nodeDBbyNodeNum.values.toTypedArray(),
                messages = recentDataPackets.toTypedArray()
            )
            val json = Json(JsonConfiguration.Default)
            val asString = json.stringify(SavedSettings.serializer(), settings)
            debug("Saving settings as $asString")
            getPrefs().edit(commit = true) {
                // FIXME, not really ideal to store this bigish blob in preferences
                putString("json", asString)
            }
        }
    }

    /**
     * Install a new node DB
     */
    private fun installNewNodeDB(newMyNodeInfo: MyNodeInfo, nodes: Array<NodeInfo>) {
        discardNodeDB() // Get rid of any old state

        myNodeInfo = newMyNodeInfo

        // put our node array into our two different map representations
        nodeDBbyNodeNum.putAll(nodes.map { Pair(it.num, it) })
        nodeDBbyID.putAll(nodes.mapNotNull {
            it.user?.let { user -> // ignore records that don't have a valid user
                Pair(
                    user.id,
                    it
                )
            }
        })
    }

    /// Load our saved DB state
    private fun loadSettings() {
        try {
            getPrefs().getString("json", null)?.let { asString ->

                val json = Json(JsonConfiguration.Default)
                val settings = json.parse(SavedSettings.serializer(), asString)
                installNewNodeDB(settings.myInfo, settings.nodeDB)

                // Note: we do not haveNodeDB = true because that means we've got a valid db from a real device (rather than this possibly stale hint)

                recentDataPackets.addAll(settings.messages)
            }
        } catch (ex: Exception) {
            errormsg("Ignoring error loading saved state for service: ${ex.message}")
        }
    }

    /**
     * discard entire node db & message state - used when changing radio channels
     */
    private fun discardNodeDB() {
        myNodeInfo = null
        nodeDBbyNodeNum.clear()
        nodeDBbyID.clear()
        recentDataPackets.clear()
        haveNodeDB = false
    }

    var myNodeInfo: MyNodeInfo? = null

    private var radioConfig: MeshProtos.RadioConfig? = null

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

    /// Map a nodenum to the nodeid string, or return null if not present or no id found
    private fun toNodeID(n: Int) = nodeDBbyNodeNum[n]?.user?.id

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
    private val myNodeNum
        get() = myNodeInfo?.myNodeNum
            ?: throw RadioNotConnectedException("We don't yet have our myNodeInfo")

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

    private val recentDataPackets = mutableListOf<DataPacket>()

    /// Generate a DataPacket from a MeshPacket, or null if we didn't have enough data to do so
    private fun toDataPacket(packet: MeshPacket): DataPacket? {
        return if (!packet.hasPayload() || !packet.payload.hasData()) {
            // We never convert packets that are not DataPackets
            null
        } else {
            val data = packet.payload.data
            val bytes = data.payload.toByteArray()
            val fromId = toNodeID(packet.from)
            val toId = toNodeID(packet.to)
                ?: packet.to.toString() // FIXME, we don't currently have IDs specified for the broadcast address

            // If the rxTime was not set by the device (because device software was old), guess at a time
            val rxTime = if (packet.rxTime == 0) packet.rxTime else currentSecond()

            if (fromId != null) {
                DataPacket(
                    fromId,
                    toId,
                    rxTime * 1000L,
                    packet.id,
                    data.typValue,
                    bytes
                )
            } else {
                warn("Ignoring data from ${packet.from} because we don't yet know its ID")
                null
            }
        }
    }

    private fun rememberDataPacket(dataPacket: DataPacket) {
        // discard old messages if needed then add the new one
        while (recentDataPackets.size > 20) // FIXME, we should instead serialize this list to flash on shutdown
            recentDataPackets.removeAt(0)
        recentDataPackets.add(dataPacket)
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedData(packet: MeshPacket) {
        val data = packet.payload.data
        val bytes = data.payload.toByteArray()
        val fromId = toNodeID(packet.from)
        val dataPacket = toDataPacket(packet)

        if (dataPacket != null) {
            debug("Received data from $fromId ${bytes.size}")

            rememberDataPacket(dataPacket)

            when (data.typValue) {
                MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                    debug("Received CLEAR_TEXT from $fromId")

                    recentReceivedText = dataPacket
                    updateNotification()
                    broadcastReceivedData(dataPacket)
                }

                MeshProtos.Data.Type.CLEAR_READACK_VALUE ->
                    warn(
                        "TODO ignoring CLEAR_READACK from $fromId"
                    )

                MeshProtos.Data.Type.OPAQUE_VALUE ->
                    broadcastReceivedData(dataPacket)

                else -> TODO()
            }

            GeeksvilleApplication.analytics.track(
                "num_data_receive",
                DataPair(1)
            )

            GeeksvilleApplication.analytics.track(
                "data_receive",
                DataPair("num_bytes", bytes.size),
                DataPair("type", data.typValue)
            )
        }
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
            it.position = Position(p, it.position?.time ?: 0)
        }
    }

    /// If packets arrive before we have our node DB, we delay parsing them until the DB is ready
    private val earlyReceivedPackets = mutableListOf<MeshPacket>()

    /// If apps try to send packets when our radio is sleeping, we queue them here instead
    private val offlineSentPackets = mutableListOf<MeshPacket>()

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        if (haveNodeDB) {
            processReceivedMeshPacket(packet)
            onNodeDBChanged()
        } else {
            earlyReceivedPackets.add(packet)
            logAssert(earlyReceivedPackets.size < 128) // The max should normally be about 32, but if the device is messed up it might try to send forever
        }
    }

    /// Process any packets that showed up too early
    private fun processEarlyPackets() {
        earlyReceivedPackets.forEach { processReceivedMeshPacket(it) }
        earlyReceivedPackets.clear()

        offlineSentPackets.forEach { sendToRadio(it) }
        offlineSentPackets.clear()
    }


    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun processReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        //val toNum = packet.to

        val p = packet.payload

        // If the rxTime was not set by the device (because device software was old), guess at a time
        val rxTime = if (packet.rxTime == 0) packet.rxTime else currentSecond()

        // Update last seen for the node that sent the packet, but also for _our node_ because anytime a packet passes
        // through our node on the way to the phone that means that local node is also alive in the mesh
        updateNodeInfo(fromNum) {
            // Update our last seen based on any valid timestamps.  If the device didn't provide a timestamp make one
            val lastSeen = rxTime

            it.position = it.position?.copy(time = lastSeen)
        }
        updateNodeInfo(myNodeNum) {
            it.position = it.position?.copy(time = currentSecond())
        }

        if (p.hasPosition())
            handleReceivedPosition(fromNum, p.position)

        if (p.hasData())
            handleReceivedData(packet)

        if (p.hasUser())
            handleReceivedUser(fromNum, p.user)
    }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()


    /**
     * Note: this is the deprecated REV1 API way of getting the nodedb
     * We are reconnecting to a radio, redownload the full state.  This operation might take hundreds of milliseconds
     * */
    private fun reinitFromRadioREV1() {
        // Read the MyNodeInfo object
        val myInfo = MeshProtos.MyNodeInfo.parseFrom(
            connectedRadio.readMyNode()
        )

        val mi = with(myInfo) {
            MyNodeInfo(myNodeNum, hasGps, region, hwModel, firmwareVersion)
        }

        myNodeInfo = mi

        radioConfig = MeshProtos.RadioConfig.parseFrom(connectedRadio.readRadioConfig())

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
            val info = MeshProtos.NodeInfo.parseFrom(infoBytes)
            installNodeInfo(info)

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

            serviceScope.handledLaunch(Dispatchers.Main) {
                if (numOnlineNodes >= 2)
                    startLocationRequests()
                else
                    stopLocationRequests()
            }
        } else
            debug("Our radio has a built in GPS, so not reading GPS in phone")
    }


    /**
     * Send in analytics about mesh connection
     */
    private fun reportConnection() {
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
    }

    private var sleepTimeout: Job? = null

    /// msecs since 1970 we started this connection
    private var connectTimeMsec = 0L

    /// Called when we gain/lose connection to our radio
    private fun onConnectionChanged(c: ConnectionState) {
        debug("onConnectionChanged=$c")

        /// Perform all the steps needed once we start waiting for device sleep to complete
        fun startDeviceSleep() {
            // lost radio connection, therefore no need to keep listening to GPS
            stopLocationRequests()

            if (connectTimeMsec != 0L) {
                val now = System.currentTimeMillis()
                connectTimeMsec = 0L

                GeeksvilleApplication.analytics.track(
                    "connected_seconds",
                    DataPair((now - connectTimeMsec) / 1000.0)
                )
            }

            // Have our timeout fire in the approprate number of seconds
            sleepTimeout = serviceScope.handledLaunch {
                try {
                    // If we have a valid timeout, wait that long (+30 seconds) otherwise, just wait 30 seconds
                    val timeout = (radioConfig?.preferences?.lsSecs ?: 0) + 30

                    debug("Waiting for sleeping device, timeout=$timeout secs")
                    delay(timeout * 1000L)
                    warn("Device timeout out, setting disconnected")
                    onConnectionChanged(ConnectionState.DISCONNECTED)
                } catch (ex: CancellationException) {
                    debug("device sleep timeout cancelled")
                }
            }
        }

        fun startDisconnect() {
            // Just in case the user uncleanly reboots the phone, save now (we normally save in onDestroy)
            saveSettings()

            GeeksvilleApplication.analytics.track(
                "mesh_disconnect",
                DataPair("num_nodes", numNodes),
                DataPair("num_online", numOnlineNodes)
            )
            GeeksvilleApplication.analytics.track("num_nodes", DataPair(numNodes))
        }

        fun startConnect() {
            // Do our startup init
            try {
                connectTimeMsec = System.currentTimeMillis()
                if (RadioInterfaceService.isOldApi!!)
                    reinitFromRadioREV1()
                else
                    startConfig()

                reportConnection()
            } catch (ex: RadioNotConnectedException) {
                // note: no need to call startDeviceSleep(), because this exception could only have reached us if it was already called
                error("Lost connection to radio during init - waiting for reconnect")
            } catch (ex: RemoteException) {
                // It seems that when the ESP32 goes offline it can briefly come back for a 100ms ish which
                // causes the phone to try and reconnect.  If we fail downloading our initial radio state we don't want to
                // claim we have a valid connection still
                connectionState = ConnectionState.DEVICE_SLEEP
                startDeviceSleep()
                throw ex; // Important to rethrow so that we don't tell the app all is well
            }
        }

        // Cancel any existing timeouts
        sleepTimeout?.let {
            it.cancel()
            sleepTimeout = null
        }

        connectionState = c
        when (c) {
            ConnectionState.CONNECTED ->
                startConnect()
            ConnectionState.DEVICE_SLEEP ->
                startDeviceSleep()
            ConnectionState.DISCONNECTED ->
                startDisconnect()
        }

        // broadcast an intent with our new connection state
        val intent = Intent(ACTION_MESH_CONNECTED)
        intent.putExtra(
            EXTRA_CONNECTED,
            connectionState.toString()
        )
        explicitBroadcast(intent)

        // Update the android notification in the status bar
        updateNotification()
    }

    /**
     * Receives messages from our BT radio service and processes them to update our model
     * and send to clients as needed.
     */
    private val radioInterfaceReceiver = object : BroadcastReceiver() {

        // Important to never throw exceptions out of onReceive
        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            serviceScope.handledLaunch {
                debug("Received broadcast ${intent.action}")
                when (intent.action) {
                    RadioInterfaceService.RADIO_CONNECTED_ACTION -> {
                        try {
                            val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                            val permanent = intent.getBooleanExtra(EXTRA_PERMANENT, false)
                            onConnectionChanged(
                                when {
                                    connected -> ConnectionState.CONNECTED
                                    permanent -> ConnectionState.DISCONNECTED
                                    else -> ConnectionState.DEVICE_SLEEP
                                }
                            )
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

                            MeshProtos.FromRadio.CONFIG_COMPLETE_ID_FIELD_NUMBER -> handleConfigComplete(
                                proto.configCompleteId
                            )

                            MeshProtos.FromRadio.MY_INFO_FIELD_NUMBER -> handleMyInfo(proto.myInfo)

                            MeshProtos.FromRadio.NODE_INFO_FIELD_NUMBER -> handleNodeInfo(proto.nodeInfo)

                            MeshProtos.FromRadio.RADIO_FIELD_NUMBER -> handleRadioConfig(proto.radio)

                            else -> errormsg("Unexpected FromRadio variant")
                        }
                    }

                    else -> errormsg("Unexpected radio interface broadcast")
                }
            }
        }
    }

    /// A provisional MyNodeInfo that we will install if all of our node config downloads go okay
    private var newMyNodeInfo: MyNodeInfo? = null

    /// provisional NodeInfos we will install if all goes well
    private val newNodes = mutableListOf<MeshProtos.NodeInfo>()

    /// Used to make sure we never get foold by old BLE packets
    private var configNonce = 1


    private fun handleRadioConfig(radio: MeshProtos.RadioConfig) {
        radioConfig = radio
    }

    /**
     * Convert a protobuf NodeInfo into our model objects and update our node DB
     */
    private fun installNodeInfo(info: MeshProtos.NodeInfo) {
        val mi = myNodeInfo!! // It better be set by now

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
                it.position = Position(info.position)
            }
        }
    }

    private fun handleNodeInfo(info: MeshProtos.NodeInfo) {
        debug("Received nodeinfo num=${info.num}, hasUser=${info.hasUser()}, hasPosition=${info.hasPosition()}")

        logAssert(newNodes.size <= 256) // Sanity check to make sure a device bug can't fill this list forever
        newNodes.add(info)
    }


    private fun handleMyInfo(myInfo: MeshProtos.MyNodeInfo) {
        val mi = with(myInfo) {
            MyNodeInfo(myNodeNum, hasGps, region, hwModel, firmwareVersion)
        }

        newMyNodeInfo = mi

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
    }

    private fun handleConfigComplete(configCompleteId: Int) {
        if (configCompleteId == configNonce) {
            // This was our config request
            if (newMyNodeInfo == null || newNodes.isEmpty())
                reportError("Did not receive a valid config")
            else {
                debug("Installing new node DB")
                discardNodeDB()
                myNodeInfo = newMyNodeInfo

                newNodes.forEach(::installNodeInfo)
                newNodes.clear() // Just to save RAM ;-)

                haveNodeDB = true // we now have nodes from real hardware
                processEarlyPackets() // send receive any packets that were queued up
                onNodeDBChanged()
                reportConnection()
            }
        } else
            warn("Ignoring stale config complete")
    }

    /**
     * Start the modern (REV2) API configuration flow
     */
    private fun startConfig() {
        configNonce += 1
        newNodes.clear()
        newMyNodeInfo = null

        sendToRadio(ToRadio.newBuilder().apply {
            this.wantConfigId = configNonce
        })
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
            it.latitudeD = lat // Only old radios will use this variant, others will just ignore it
            it.longitudeD = lon

            it.longitudeI = Position.degI(lon)
            it.latitudeI = Position.degI(lat)

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
        sendToRadio(packet.build())
    }

    /** Set our radio config either with the new or old API
     */
    private fun setRadioConfig(payload: ByteArray) {
        val parsed = MeshProtos.RadioConfig.parseFrom(payload)

        // Update our device
        if (RadioInterfaceService.isOldApi!!)
            connectedRadio.writeRadioConfig(payload)
        else
            sendToRadio(ToRadio.newBuilder().apply {
                this.setRadio = parsed
            })

        // Update our cached copy
        this@MeshService.radioConfig = parsed
    }

    /**
     * Set our owner with either the new or old API
     */
    fun setOwner(myId: String?, longName: String, shortName: String) {
        debug("SetOwner $myId : ${longName.anonymized} : $shortName")

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
        if (RadioInterfaceService.isOldApi!!)
            connectedRadio.writeOwner(user.toByteArray())
        else sendToRadio(ToRadio.newBuilder().apply {
            this.setOwner = user
        })

    }

    private val binder = object : IMeshService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
            debug("Passing through device change to radio service: $deviceAddr")
            discardNodeDB()
            radio.service.setDeviceAddress(deviceAddr)
        }

        // Note: bound methods don't get properly exception caught/logged, so do that with a wrapper
        // per https://blog.classycode.com/dealing-with-exceptions-in-aidl-9ba904c6d63
        override fun subscribeReceiver(packageName: String, receiverName: String) =
            toRemoteExceptions {
                clientPackages[receiverName] = packageName
            }

        override fun getOldMessages(): MutableList<DataPacket> {
            return recentDataPackets
        }

        override fun getMyId() = toRemoteExceptions { myNodeID }

        override fun setOwner(myId: String?, longName: String, shortName: String) =
            toRemoteExceptions {
                this@MeshService.setOwner(myId, longName, shortName)
            }

        override fun sendData(destId: String?, payloadIn: ByteArray, typ: Int): Boolean =
            toRemoteExceptions {
                info("sendData dest=$destId <- ${payloadIn.size} bytes (connectionState=$connectionState)")

                // encapsulate our payload in the proper protobufs and fire it off
                val packet = buildMeshPacket(destId) {
                    data = MeshProtos.Data.newBuilder().also {
                        it.typ = MeshProtos.Data.Type.forNumber(typ)
                        it.payload = ByteString.copyFrom(payloadIn)
                    }.build()
                }
                // Keep a record of datapackets, so GUIs can show proper chat history
                toDataPacket(packet)?.let {
                    rememberDataPacket(it)
                }

                // If radio is sleeping, queue the packet
                when (connectionState) {
                    ConnectionState.DEVICE_SLEEP ->
                        offlineSentPackets.add(packet)
                    else ->
                        sendToRadio(packet)
                }

                GeeksvilleApplication.analytics.track(
                    "data_send",
                    DataPair("num_bytes", payloadIn.size),
                    DataPair("type", typ)
                )

                GeeksvilleApplication.analytics.track(
                    "num_data_sent",
                    DataPair(1)
                )

                connectionState == ConnectionState.CONNECTED
            }

        override fun getRadioConfig(): ByteArray = toRemoteExceptions {
            this@MeshService.radioConfig?.toByteArray() ?: throw RadioNotConnectedException()
        }

        override fun setRadioConfig(payload: ByteArray) = toRemoteExceptions {
            this@MeshService.setRadioConfig(payload)
        }

        override fun getNodes(): MutableList<NodeInfo> = toRemoteExceptions {
            val r = nodeDBbyID.values.toMutableList()
            info("in getOnline, count=${r.size}")
            // return arrayOf("+16508675309")
            r
        }

        override fun connectionState(): String = toRemoteExceptions {
            val r = this@MeshService.connectionState
            info("in connectionState=$r")
            r.toString()
        }
    }
}