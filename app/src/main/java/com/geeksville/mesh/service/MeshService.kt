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
import android.os.Parcelable
import android.os.RemoteException
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import androidx.core.content.edit
import androidx.work.*
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.ServiceClient
import com.geeksville.android.isGooglePlayAvailable
import com.geeksville.concurrent.handledLaunch
import com.geeksville.mesh.*
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.mesh.R
import com.geeksville.util.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue


/**
 * Handles all the communication with android apps.  Also keeps an internal model
 * of the network state.
 *
 * Note: this service will go away once all clients are unbound from it.
 */
class MeshService : Service(), Logging {

    companion object : Logging {

        /// special broadcast address
        const val NODENUM_BROADCAST = (0xffffffff).toInt()

        /// Intents broadcast by MeshService
        const val ACTION_RECEIVED_DATA = "$prefix.RECEIVED_DATA"
        const val ACTION_NODE_CHANGE = "$prefix.NODE_CHANGE"
        const val ACTION_MESH_CONNECTED = "$prefix.MESH_CONNECTED"
        const val ACTION_MESSAGE_STATUS = "$prefix.MESSAGE_STATUS"

        class IdNotFoundException(id: String) : Exception("ID not found $id")
        class NodeNumNotFoundException(id: Int) : Exception("NodeNum not found $id")
        // class NotInMeshException() : Exception("We are not yet in a mesh")

        /** A little helper that just calls startService
         */
        class ServiceStarter(appContext: Context, workerParams: WorkerParameters) :
            Worker(appContext, workerParams) {

            override fun doWork(): Result = try {
                startService(this.applicationContext)

                // Indicate whether the task finished successfully with the Result
                Result.success()
            } catch (ex: Exception) {
                errormsg("failure starting service, will retry", ex)
                Result.retry()
            }
        }

        /**
         * Talk to our running service and try to set a new device address.  And then immediately
         * call start on the service to possibly promote our service to be a foreground service.
         */
        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        /**
         * Just after boot the android OS is super busy, so if we call startForegroundService then, our
         * thread might be stalled long enough to expose this google/samsung bug:
         * https://issuetracker.google.com/issues/76112072#comment56
         */
        fun startLater(context: Context) {
            // No point in even starting the service if the user doesn't have a device bonded
            info("Received boot complete announcement, starting mesh service in two minutes")
            val delayRequest = OneTimeWorkRequestBuilder<ServiceStarter>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .addTag("startLater")
                .build()

            WorkManager.getInstance(context).enqueue(delayRequest)
        }

        val intent = Intent().apply {
            setClassName(
                "com.geeksville.mesh",
                "com.geeksville.mesh.service.MeshService"
            )
        }

        /// Helper function to start running our service
        fun startService(context: Context) {
            // bind to our service using the same mechanism an external client would use (for testing coverage)
            // The following would work for us, but not external users
            //val intent = Intent(this, MeshService::class.java)
            //intent.action = IMeshService::class.java.name


            // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
            // listening for the bluetooth packets arriving from the radio.  And when they arrive forward them
            // to Signal or whatever.

            info("Trying to start service")
            val compName =
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                })

            if (compName == null)
                throw Exception("Failed to start service")
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
        val stub = IRadioInterfaceService.Stub.asInterface(it)

        // Now that we are connected to the radio service, tell it to connect to the radio
        stub.connect()

        stub
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
                        } catch (ex: BLEException) { // Really a RadioNotConnected exception, but it has changed into this type via remoting
                            warn("BLE exception, stopping location requests $ex")
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
        // FIXME - currently we don't support location reading without google play
        if (fusedLocationClient == null && isGooglePlayAvailable(this)) {
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

    private fun broadcastMessageStatus(p: DataPacket) {
        if (p.id == 0) {
            debug("Ignoring anonymous packet status")
        } else {
            debug("Broadcasting message status $p")
            val intent = Intent(ACTION_MESSAGE_STATUS)

            intent.putExtra(EXTRA_PACKET_ID, p.id)
            intent.putExtra(EXTRA_STATUS, p.status as Parcelable)
            explicitBroadcast(intent)
        }
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


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = getString(R.string.meshtastic_service_notifications)
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


    // Note: do not override toString, it causes infinite recursion on some androids (because contextWrapper.getResources calls to string)
    // override fun toString() = summaryString

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
                    .bigText(packet.bytes!!.toString(utf8))
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
        val wantForeground = RadioInterfaceService.getBondedDeviceAddress(this) != null

        info("Requesting foreground service=$wantForeground")

        // We always start foreground because that's how our service is always started (if we didn't then android would kill us)
        // but if we don't really need forground we immediately stop it.
        startForeground(notifyId, createNotification())
        if (!wantForeground)
            stopForeground(true)
    }

    override fun onCreate() {
        super.onCreate()

        info("Creating mesh service")

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

    /**
     * If someone binds to us, this will be called after on create
     */
    override fun onBind(intent: Intent?): IBinder? {
        startForeground()

        return binder
    }

    /**
     * If someone starts us (or restarts us) this will be called after onCreate)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        return super.onStartCommand(intent, flags, startId)
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
            debug("Saving settings")
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
     * discard entire node db & message state - used when downloading a new db from the device
     */
    private fun discardNodeDB() {
        debug("Discarding NodeDB")
        myNodeInfo = null
        nodeDBbyNodeNum.clear()
        nodeDBbyID.clear()
        // recentDataPackets.clear() We do NOT want to clear this, because it is the record of old messages the GUI still might want to show
        haveNodeDB = false
    }

    var myNodeInfo: MyNodeInfo? = null

    private var radioConfig: MeshProtos.RadioConfig? = null

    /// True after we've done our initial node db init
    @Volatile
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
    private fun toNodeID(n: Int) =
        if (n == NODENUM_BROADCAST) DataPacket.ID_BROADCAST else nodeDBbyNodeNum[n]?.user?.id

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

    private fun toNodeNum(id: String) =
        when (id) {
            DataPacket.ID_BROADCAST -> NODENUM_BROADCAST
            DataPacket.ID_LOCAL -> myNodeNum
            else -> toNodeInfo(id).num
        }

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
        val useShortAddresses = (myNodeInfo?.nodeNumBits ?: 8) != 32

        if (myNodeInfo == null)
            throw RadioNotConnectedException()

        from = myNodeNum

        // We might need to change broadcast addresses to work with old device loads
        to = if (useShortAddresses && idNum == NODENUM_BROADCAST) 255 else idNum
    }

    /**
     * Generate a new mesh packet builder with our node as the sender, and the specified recipient
     *
     * If id is null we assume a broadcast message
     */
    private fun newMeshPacketTo(id: String) =
        newMeshPacketTo(toNodeNum(id))

    /**
     * Helper to make it easy to build a subpacket in the proper protobufs
     *
     * If destId is null we assume a broadcast message
     */
    private fun buildMeshPacket(
        destId: String,
        wantAck: Boolean = false,
        id: Int = 0,
        initFn: MeshProtos.SubPacket.Builder.() -> Unit
    ): MeshPacket = newMeshPacketTo(destId).apply {
        this.wantAck = wantAck
        this.id = id
        decoded = MeshProtos.SubPacket.newBuilder().also {
            initFn(it)
        }.build()
    }.build()

    // FIXME - possible kotlin bug in 1.3.72 - it seems that if we start with the (globally shared) emptyList,
    // then adding items are affecting that shared list rather than a copy.   This was causing aliasing of
    // recentDataPackets with messages.value in the GUI.  So if the current list is empty we are careful to make a new list
    private var recentDataPackets = mutableListOf<DataPacket>()

    /// Generate a DataPacket from a MeshPacket, or null if we didn't have enough data to do so
    private fun toDataPacket(packet: MeshPacket): DataPacket? {
        return if (!packet.hasDecoded() || !packet.decoded.hasData()) {
            // We never convert packets that are not DataPackets
            null
        } else {
            val data = packet.decoded.data
            val bytes = data.payload.toByteArray()
            val fromId = toNodeID(packet.from)
            val toId = toNodeID(packet.to)

            // If the rxTime was not set by the device (because device software was old), guess at a time
            val rxTime = if (packet.rxTime == 0) packet.rxTime else currentSecond()

            when {
                fromId == null -> {
                    errormsg("Ignoring data from ${packet.from} because we don't yet know its ID")
                    null
                }
                toId == null -> {
                    errormsg("Ignoring data to ${packet.to} because we don't yet know its ID")
                    null
                }
                else -> {
                    DataPacket(
                        from = fromId,
                        to = toId,
                        time = rxTime * 1000L,
                        id = packet.id,
                        dataType = data.typValue,
                        bytes = bytes
                    )
                }
            }
        }
    }

    private fun toMeshPacket(p: DataPacket): MeshPacket {
        return buildMeshPacket(p.to!!, id = p.id, wantAck = true) {
            data = MeshProtos.Data.newBuilder().also {
                it.typ = MeshProtos.Data.Type.forNumber(p.dataType)
                it.payload = ByteString.copyFrom(p.bytes)
            }.build()
        }
    }

    private fun rememberDataPacket(dataPacket: DataPacket) {
        // discard old messages if needed then add the new one
        while (recentDataPackets.size > 50)
            recentDataPackets.removeAt(0)

        // FIXME - possible kotlin bug in 1.3.72 - it seems that if we start with the (globally shared) emptyList,
        // then adding items are affecting that shared list rather than a copy.   This was causing aliasing of
        // recentDataPackets with messages.value in the GUI.  So if the current list is empty we are careful to make a new list
        if (recentDataPackets.isEmpty())
            recentDataPackets = mutableListOf(dataPacket)
        else
            recentDataPackets.add(dataPacket)
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedData(packet: MeshPacket) {
        myNodeInfo?.let { myInfo ->
            val data = packet.decoded.data
            val bytes = data.payload.toByteArray()
            val fromId = toNodeID(packet.from)
            val dataPacket = toDataPacket(packet)

            if (dataPacket != null) {

                if (myInfo.myNodeNum == packet.from)
                    debug("Ignoring retransmission of our packet ${bytes.size}")
                else {
                    debug("Received data from $fromId ${bytes.size}")

                    dataPacket.status = MessageStatus.RECEIVED
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
    private fun handleReceivedPosition(fromNum: Int, p: MeshProtos.Position, defaultTime: Int = Position.currentTime()) {
        updateNodeInfo(fromNum) {
            it.position = Position(p)
            updateNodeInfoTime(it, defaultTime)
        }
    }

    /// If packets arrive before we have our node DB, we delay parsing them until the DB is ready
    private val earlyReceivedPackets = mutableListOf<MeshPacket>()

    /// If apps try to send packets when our radio is sleeping, we queue them here instead
    private val offlineSentPackets = mutableListOf<DataPacket>()

    /** Keep a record of recently sent packets, so we can properly handle ack/nak */
    private val sentPackets = mutableMapOf<Int, DataPacket>()

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

    private fun sendNow(p: DataPacket) {
        val packet = toMeshPacket(p)
        p.status = MessageStatus.ENROUTE
        p.time = System.currentTimeMillis() // update time to the actual time we started sending
        // debug("SENDING TO RADIO: $packet")
        sendToRadio(packet)
    }

    /// Process any packets that showed up too early
    private fun processEarlyPackets() {
        earlyReceivedPackets.forEach { processReceivedMeshPacket(it) }
        earlyReceivedPackets.clear()

        offlineSentPackets.forEach { p ->
            // encapsulate our payload in the proper protobufs and fire it off
            sendNow(p)
            broadcastMessageStatus(p)
        }
        offlineSentPackets.clear()
    }

    /**
     * Change the status on a data packet and update watchers
     */
    private fun changeStatus(p: DataPacket, m: MessageStatus) {
        p.status = m
        broadcastMessageStatus(p)
    }

    /**
     * Handle an ack/nak packet by updating sent message status
     */
    private fun handleAckNak(isAck: Boolean, id: Int) {
        sentPackets.remove(id)?.let { p ->
            changeStatus(p, if (isAck) MessageStatus.DELIVERED else MessageStatus.ERROR)
        }
    }

    /// Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun processReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        //val toNum = packet.to

        // debug("Recieved: $packet")
        val p = packet.decoded

        // If the rxTime was not set by the device (because device software was old), guess at a time
        val rxTime = if (packet.rxTime != 0) packet.rxTime else currentSecond()

        // Update last seen for the node that sent the packet, but also for _our node_ because anytime a packet passes
        // through our node on the way to the phone that means that local node is also alive in the mesh

        updateNodeInfo(myNodeNum) {
            it.position = it.position?.copy(time = currentSecond())
        }

        if (p.hasPosition())
            handleReceivedPosition(fromNum, p.position, rxTime)
        else
            updateNodeInfo(fromNum) {
                // Update our last seen based on any valid timestamps.  If the device didn't provide a timestamp make one
                updateNodeInfoTime(it, rxTime)
            }

        if (p.hasData())
            handleReceivedData(packet)

        if (p.hasUser())
            handleReceivedUser(fromNum, p.user)

        if (p.successId != 0)
            handleAckNak(true, p.successId)

        if (p.failId != 0)
            handleAckNak(false, p.failId)
    }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()


    /// If we just changed our nodedb, we might want to do somethings
    private fun onNodeDBChanged() {
        updateNotification()

        // we don't ask for GPS locations from android if our device has a built in GPS
        // Note: myNodeInfo can go away if we lose connections, so it might be null
        if (myNodeInfo?.hasGPS != true) {
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
            // Just in case the user uncleanly reboots the phone, save now (we normally save in onDestroy)
            saveSettings()

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

            // broadcast an intent with our new connection state
            broadcastConnection()
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

            // broadcast an intent with our new connection state
            broadcastConnection()
        }

        fun startConnect() {
            // Do our startup init
            try {
                connectTimeMsec = System.currentTimeMillis()
                startConfig()

            } catch (ex: InvalidProtocolBufferException) {
                errormsg(
                    "Invalid protocol buffer sent by device - update device software and try again",
                    ex
                )
            } catch (ex: RadioNotConnectedException) {
                // note: no need to call startDeviceSleep(), because this exception could only have reached us if it was already called
                errormsg("Lost connection to radio during init - waiting for reconnect")
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

        // Update the android notification in the status bar
        updateNotification()
    }

    /**
     * broadcast our current connection status
     */
    private fun broadcastConnection() {
        val intent = Intent(ACTION_MESH_CONNECTED)
        intent.putExtra(
            EXTRA_CONNECTED,
            connectionState.toString()
        )
        explicitBroadcast(intent)
    }

    /**
     * Receives messages from our BT radio service and processes them to update our model
     * and send to clients as needed.
     */
    private val radioInterfaceReceiver = object : BroadcastReceiver() {

        // Important to never throw exceptions out of onReceive
        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            // NOTE: Do not call handledLaunch here, because it can cause out of order message processing - because each routine is scheduled independently
            // serviceScope.handledLaunch {
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
                    val bytes = intent.getByteArrayExtra(EXTRA_PAYLOAD)!!
                    try {
                        val proto =
                            MeshProtos.FromRadio.parseFrom(bytes)
                        // info("Received from radio service: ${proto.toOneLineString()}")
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
                    } catch (ex: InvalidProtocolBufferException) {
                        errormsg("Invalid Protobuf from radio, len=${bytes.size}", ex)
                    }
                }

                else -> errormsg("Unexpected radio interface broadcast")
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


    /**
     * Update the nodeinfo (called from either new API version or the old one)
     */
    private fun handleMyInfo(myInfo: MeshProtos.MyNodeInfo) {
        setFirmwareUpdateFilename(myInfo)

        val mi = with(myInfo) {
            MyNodeInfo(
                myNodeNum,
                hasGps,
                region,
                hwModel,
                firmwareVersion,
                firmwareUpdateFilename != null,
                SoftwareUpdateService.shouldUpdate(this@MeshService, firmwareVersion),
                currentPacketId.toLong() and 0xffffffffL,
                if (nodeNumBits == 0) 8 else nodeNumBits,
                if (packetIdBits == 0) 8 else packetIdBits,
                if (messageTimeoutMsec == 0) 5 * 60 * 1000 else messageTimeoutMsec, // constants from current device code
                minAppVersion
            )
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
                errormsg("Did not receive a valid config")
            else {
                debug("Installing new node DB")
                discardNodeDB()
                myNodeInfo = newMyNodeInfo

                newNodes.forEach(::installNodeInfo)
                newNodes.clear() // Just to save RAM ;-)

                haveNodeDB = true // we now have nodes from real hardware
                processEarlyPackets() // send receive any packets that were queued up

                // broadcast an intent with our new connection state
                broadcastConnection()
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
        debug("Starting config nonce=$configNonce")

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
            it.longitudeI = Position.degI(lon)
            it.latitudeI = Position.degI(lat)

            it.altitude = alt
            it.time = currentSecond() // Include our current timestamp
        }.build()

        // encapsulate our payload in the proper protobufs and fire it off
        val packet = newMeshPacketTo(destNum)

        packet.decoded = MeshProtos.SubPacket.newBuilder().also {
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
        val myNode = myNodeInfo
        if (myNode != null) {


            val myInfo = toNodeInfo(myNode.myNodeNum)
            if (longName == myInfo.user?.longName && shortName == myInfo.user?.shortName)
                debug("Ignoring nop owner change")
            else {
                debug("SetOwner $myId : ${longName.anonymize} : $shortName")

                val user = MeshProtos.User.newBuilder().also {
                    if (myId != null)  // Only set the id if it was provided
                        it.id = myId
                    it.longName = longName
                    it.shortName = shortName
                }.build()

                // Also update our own map for our nodenum, by handling the packet just like packets from other users

                handleReceivedUser(myNode.myNodeNum, user)

                // set my owner info
                sendToRadio(ToRadio.newBuilder().apply {
                    this.setOwner = user
                })
            }
        } else
            throw Exception("Can't set user without a node info") // this shouldn't happen
    }

    /// Do not use directly, instead call generatePacketId()
    private var currentPacketId = 0L

    /**
     * Generate a unique packet ID (if we know enough to do so - otherwise return 0 so the device will do it)
     */
    private fun generatePacketId(): Int {

        myNodeInfo?.let {
            val numPacketIds =
                ((1L shl it.packetIdBits) - 1).toLong() // A mask for only the valid packet ID bits, either 255 or maxint

            if (currentPacketId == 0L) {
                logAssert(it.packetIdBits == 8 || it.packetIdBits == 32) // Only values I'm expecting (though we don't require this)

                val devicePacketId = if (it.currentPacketId == 0L) {
                    // Old devices don't send their current packet ID, in that case just pick something random and it will probably be fine ;-)
                    val random = Random(System.currentTimeMillis())
                    random.nextLong().absoluteValue
                } else
                    it.currentPacketId

                // Not inited - pick a number on the opposite side of what the device is using
                currentPacketId = devicePacketId + numPacketIds / 2
            } else {
                currentPacketId++
            }

            currentPacketId = currentPacketId and 0xffffffff // keep from exceeding 32 bits

            // Use modulus and +1 to ensure we skip 0 on any values we return
            return ((currentPacketId % numPacketIds) + 1L).toInt()
        }

        return 0 // We don't have mynodeinfo yet, so just let the radio eventually assign an ID
    }

    var firmwareUpdateFilename: String? = null

    /***
     * Return the filename we will install on the device
     */
    private fun setFirmwareUpdateFilename(info: MeshProtos.MyNodeInfo) {
        firmwareUpdateFilename = try {
            if (info.region != null && info.firmwareVersion != null && info.hwModel != null)
                SoftwareUpdateService.getUpdateFilename(
                    this,
                    info.region,
                    info.firmwareVersion,
                    info.hwModel
                )
            else
                null
        } catch (ex: Exception) {
            errormsg("Unable to update", ex)
            null
        }

        debug("setFirmwareUpdateFilename $firmwareUpdateFilename")
    }

    private fun doFirmwareUpdate() {
        // Run in the IO thread
        val filename = firmwareUpdateFilename ?: throw Exception("No update filename")
        val safe =
            BluetoothInterface.safe
                ?: throw Exception("Can't update - no bluetooth connected")

        serviceScope.handledLaunch {
            SoftwareUpdateService.doUpdate(this@MeshService, safe, filename)
        }
    }

    /**
     * Remove any sent packets that have been sitting around too long
     *
     * Note: we give each message what the timeout the device code is using, though in the normal
     * case the device will fail after 3 retries much sooner than that (and it will provide a nak to us)
     */
    private fun deleteOldPackets() {
        myNodeInfo?.apply {
            val now = System.currentTimeMillis()

            val old = sentPackets.values.filter { p ->
                (p.status == MessageStatus.ENROUTE && p.time + messageTimeoutMsec < now)
            }

            // Do this using a separate list to prevent concurrent modification exceptions
            old.forEach { p ->
                handleAckNak(false, p.id)
            }
        }
    }


    private fun enqueueForSending(p: DataPacket) {
        p.status = MessageStatus.QUEUED
        offlineSentPackets.add(p)
    }

    val binder = object : IMeshService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
            debug("Passing through device change to radio service: ${deviceAddr.anonymize}")

            val res = radio.service.setDeviceAddress(deviceAddr)
            if (res) {
                discardNodeDB()
            }
            res
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

        override fun getUpdateStatus(): Int = SoftwareUpdateService.progress

        override fun startFirmwareUpdate() = toRemoteExceptions {
            doFirmwareUpdate()
        }

        override fun getMyNodeInfo(): MyNodeInfo = toRemoteExceptions {
            this@MeshService.myNodeInfo ?: throw RadioNotConnectedException("No MyNodeInfo")
        }

        override fun getMyId() = toRemoteExceptions { myNodeID }

        override fun setOwner(myId: String?, longName: String, shortName: String) =
            toRemoteExceptions {
                this@MeshService.setOwner(myId, longName, shortName)
            }

        override fun send(
            p: DataPacket
        ) {
            toRemoteExceptions {
                // Init from and id
                myNodeID?.let { myId ->
                    if (p.from == DataPacket.ID_LOCAL)
                        p.from = myId

                    if (p.id == 0)
                        p.id = generatePacketId()
                }

                info("sendData dest=${p.to}, id=${p.id} <- ${p.bytes!!.size} bytes (connectionState=$connectionState)")

                // Keep a record of datapackets, so GUIs can show proper chat history
                rememberDataPacket(p)

                if (p.id != 0) { // If we have an ID we can wait for an ack or nak
                    deleteOldPackets()
                    sentPackets[p.id] = p
                }

                // If radio is sleeping or disconnected, queue the packet
                when (connectionState) {
                    ConnectionState.CONNECTED ->
                        try {
                            sendNow(p)
                        } catch (ex: Exception) {
                            // This can happen if a user is unlucky and the device goes to sleep after the GUI starts a send, but before we update connectionState
                            errormsg("Error sending message, so enqueueing", ex)
                            enqueueForSending(p)
                        }
                    else -> // sleeping or disconnected
                        enqueueForSending(p)
                }

                GeeksvilleApplication.analytics.track(
                    "data_send",
                    DataPair("num_bytes", p.bytes.size),
                    DataPair("type", p.dataType)
                )

                GeeksvilleApplication.analytics.track(
                    "num_data_sent",
                    DataPair(1)
                )
            }
        }

        override fun getRadioConfig(): ByteArray = toRemoteExceptions {
            this@MeshService.radioConfig?.toByteArray()
                ?: throw RadioNotConnectedException()
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

public fun updateNodeInfoTime(it: NodeInfo, rxTime: Int) {
    if (it.position?.time == null || it.position?.time!! < rxTime)
        it.position = it.position?.copy(time = rxTime)
}