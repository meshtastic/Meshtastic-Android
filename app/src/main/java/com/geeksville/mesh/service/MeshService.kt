package com.geeksville.mesh.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.RemoteException
import androidx.core.app.ServiceCompat
import androidx.core.location.LocationCompat
import com.geeksville.mesh.*
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.mesh.TelemetryProtos.LocalStats
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.toNodeInfo
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.repository.network.MQTTRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.radio.RadioServiceConnectionState
import com.geeksville.mesh.util.*
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * Handles all the communication with android apps.  Also keeps an internal model
 * of the network state.
 *
 * Note: this service will go away once all clients are unbound from it.
 * Warning: do not override toString, it causes infinite recursion on some androids (because contextWrapper.getResources calls to string
 */
@AndroidEntryPoint
class MeshService : Service(), Logging {
    @Inject
    lateinit var dispatchers: CoroutineDispatchers

    @Inject
    lateinit var packetRepository: Lazy<PacketRepository>

    @Inject
    lateinit var meshLogRepository: Lazy<MeshLogRepository>

    @Inject
    lateinit var radioInterfaceService: RadioInterfaceService

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var radioConfigRepository: RadioConfigRepository

    @Inject
    lateinit var mqttRepository: MQTTRepository

    companion object : Logging {

        // Intents broadcast by MeshService

        private fun actionReceived(portNum: String) = "$prefix.RECEIVED.$portNum"

        // generate a RECEIVED action filter string that includes either the portnumber as an int, or preferably a symbolic name from portnums.proto
        fun actionReceived(portNum: Int): String {
            val portType = Portnums.PortNum.forNumber(portNum)
            val portStr = portType?.toString() ?: portNum.toString()

            return actionReceived(portStr)
        }

        const val ACTION_NODE_CHANGE = "$prefix.NODE_CHANGE"
        const val ACTION_MESH_CONNECTED = "$prefix.MESH_CONNECTED"
        const val ACTION_MESSAGE_STATUS = "$prefix.MESSAGE_STATUS"

        open class NodeNotFoundException(reason: String) : Exception(reason)
        class InvalidNodeIdException(id: String) : NodeNotFoundException("Invalid NodeId $id")
        class NodeNumNotFoundException(id: Int) : NodeNotFoundException("NodeNum not found $id")
        class IdNotFoundException(id: String) : NodeNotFoundException("ID not found $id")

        class NoDeviceConfigException(message: String = "No radio settings received (is our app too old?)") :
            RadioNotConnectedException(message)

        /**
         * Talk to our running service and try to set a new device address.  And then immediately
         * call start on the service to possibly promote our service to be a foreground service.
         */
        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        fun createIntent() = Intent().setClassName(
            "com.geeksville.mesh",
            "com.geeksville.mesh.service.MeshService"
        )

        /** The minimum firmware version we know how to talk to. We'll still be able
         * to talk to 2.0 firmwares but only well enough to ask them to firmware update.
         */
        val minDeviceVersion = DeviceVersion("2.3.2")
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED,
        DEVICE_SLEEP // device is in LS sleep state, it will reconnected to us over bluetooth once it has data
    }

    private var previousSummary: String? = null
    private var previousStats: LocalStats? = null

    // A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()
    private val serviceNotifications = MeshServiceNotifications(this)
    private val serviceBroadcasts = MeshServiceBroadcasts(this, clientPackages) {
        connectionState.also { radioConfigRepository.setConnectionState(it) }
    }
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var connectionState = ConnectionState.DISCONNECTED

    private var locationFlow: Job? = null
    private var mqttMessageFlow: Job? = null

    private fun getSenderName(packet: DataPacket?): String {
        val name = nodeDBbyID[packet?.from]?.user?.longName
        return name ?: getString(R.string.unknown_username)
    }

    private val notificationSummary
        get() = when (connectionState) {
            ConnectionState.CONNECTED -> getString(R.string.connected_count).format(
                numOnlineNodes,
                numNodes
            )
            ConnectionState.DISCONNECTED -> getString(R.string.disconnected)
            ConnectionState.DEVICE_SLEEP -> getString(R.string.device_sleeping)
        }

    private var localStatsTelemetry: TelemetryProtos.Telemetry? = null
    private val localStats: LocalStats? get() = localStatsTelemetry?.localStats
    private val localStatsUpdatedAtMillis: Long? get() = localStatsTelemetry?.time?.let { it * 1000L }

    /**
     * start our location requests (if they weren't already running)
     */
    private fun startLocationRequests() {
        // If we're already observing updates, don't register again
        if (locationFlow?.isActive == true) return

        @SuppressLint("MissingPermission")
        if (hasLocationPermission()) {
            locationFlow = locationRepository.getLocations().onEach { location ->
                sendPosition(
                    position {
                        latitudeI = Position.degI(location.latitude)
                        longitudeI = Position.degI(location.longitude)
                        if (LocationCompat.hasMslAltitude(location)) {
                            altitude = LocationCompat.getMslAltitudeMeters(location).toInt()
                        }
                        altitudeHae = location.altitude.toInt()
                        time = (location.time / 1000).toInt()
                        groundSpeed = location.speed.toInt()
                        groundTrack = location.bearing.toInt()
                        locationSource = MeshProtos.Position.LocSource.LOC_EXTERNAL
                    }
                )
            }.launchIn(serviceScope)
        }
    }

    private fun stopLocationRequests() {
        if (locationFlow?.isActive == true) {
            info("Stopping location requests")
            locationFlow?.cancel()
            locationFlow = null
        }
    }

    /** Send a command/packet to our radio.  But cope with the possibility that we might start up
    before we are fully bound to the RadioInterfaceService
     */
    private fun sendToRadio(p: ToRadio.Builder) {
        val built = p.build()
        debug("Sending to radio ${built.toPIIString()}")
        val b = built.toByteArray()

        radioInterfaceService.sendToRadio(b)
        changeStatus(p.packet.id, MessageStatus.ENROUTE)

        if (p.packet.hasDecoded()) {
            val packetToSave = MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Packet",
                received_date = System.currentTimeMillis(),
                raw_message = p.packet.toString(),
                fromNum = p.packet.from,
                portNum = p.packet.decoded.portnumValue,
                fromRadio = fromRadio { packet = p.packet },
            )
            insertMeshLog(packetToSave)
        }
    }

    /**
     * Send a mesh packet to the radio, if the radio is not currently connected this function will throw NotConnectedException
     */
    private fun sendToRadio(packet: MeshPacket) {
        queuedPackets.add(packet)
        startPacketQueue()
    }

    private fun updateMessageNotification(dataPacket: DataPacket) {
        val message: String = when (dataPacket.dataType) {
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> dataPacket.text!!
            Portnums.PortNum.WAYPOINT_APP_VALUE -> {
                getString(R.string.waypoint_received, dataPacket.waypoint!!.name)
            }

            else -> return
        }
        serviceNotifications.updateMessageNotification(getSenderName(dataPacket), message)
    }

    override fun onCreate() {
        super.onCreate()

        info("Creating mesh service")

        // Switch to the IO thread
        serviceScope.handledLaunch {
            radioInterfaceService.connect()
        }
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState)
            .launchIn(serviceScope)
        radioInterfaceService.receivedData.onEach(::onReceiveFromRadio)
            .launchIn(serviceScope)
        radioConfigRepository.localConfigFlow.onEach { localConfig = it }
            .launchIn(serviceScope)
        radioConfigRepository.moduleConfigFlow.onEach { moduleConfig = it }
            .launchIn(serviceScope)
        radioConfigRepository.channelSetFlow.onEach { channelSet = it }
            .launchIn(serviceScope)

        loadSettings() // Load our last known node DB

        // the rest of our init will happen once we are in radioConnection.onServiceConnected
    }

    /**
     * If someone binds to us, this will be called after on create
     */
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * If someone starts us (or restarts us) this will be called after onCreate)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = radioInterfaceService.getBondedDeviceAddress()
        val wantForeground = a != null && a != "n"

        info("Requesting foreground service=$wantForeground")

        // We always start foreground because that's how our service is always started (if we didn't then android would kill us)
        // but if we don't really need foreground we immediately stop it.
        val notification = serviceNotifications.createServiceStateNotification(notificationSummary)

        try {
            ServiceCompat.startForeground(
                this,
                serviceNotifications.notifyId,
                notification,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (hasLocationPermission()) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    }
                } else {
                    0
                },
            )
        } catch (ex: Exception) {
            errormsg("startForeground failed", ex)
            return START_NOT_STICKY
        }
        return if (!wantForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }

    override fun onDestroy() {
        info("Destroying mesh service")

        // Make sure we aren't using the notification first
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceNotifications.close()

        super.onDestroy()
        serviceJob.cancel()
    }

    //
    // BEGINNING OF MODEL - FIXME, move elsewhere
    //

    private fun loadSettings() {
        discardNodeDB() // Get rid of any old state
        myNodeInfo = radioConfigRepository.myNodeInfo.value
        nodeDBbyNodeNum.putAll(radioConfigRepository.nodeDBbyNum.value)
        // Note: we do not haveNodeDB = true because that means we've got a valid db from a real device (rather than this possibly stale hint)
    }

    /**
     * discard entire node db & message state - used when downloading a new db from the device
     */
    private fun discardNodeDB() {
        debug("Discarding NodeDB")
        myNodeInfo = null
        nodeDBbyNodeNum.clear()
        haveNodeDB = false
    }

    private var myNodeInfo: MyNodeEntity? = null

    private val configTotal by lazy { ConfigProtos.Config.getDescriptor().fields.size }
    private val moduleTotal by lazy { ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size }
    private var sessionPasskey: ByteString = ByteString.EMPTY

    private var localConfig: LocalConfig = LocalConfig.getDefaultInstance()
    private var moduleConfig: LocalModuleConfig = LocalModuleConfig.getDefaultInstance()
    private var channelSet: AppOnlyProtos.ChannelSet = AppOnlyProtos.ChannelSet.getDefaultInstance()

    // True after we've done our initial node db init
    @Volatile
    private var haveNodeDB = false

    // The database of active nodes, index is the node number
    private val nodeDBbyNodeNum = ConcurrentHashMap<Int, NodeEntity>()

    // The database of active nodes, index is the node user ID string
    // NOTE: some NodeInfos might be in only nodeDBbyNodeNum (because we don't yet know an ID).
    private val nodeDBbyID get() = nodeDBbyNodeNum.mapKeys { it.value.user.id }

    //
    // END OF MODEL
    //

    private val deviceVersion get() = DeviceVersion(myNodeInfo?.firmwareVersion ?: "")
    private val appVersion get() = BuildConfig.VERSION_CODE
    private val minAppVersion get() = myNodeInfo?.minAppVersion ?: 0

    // Map a nodenum to a node, or throw an exception if not found
    private fun toNodeInfo(n: Int) = nodeDBbyNodeNum[n] ?: throw NodeNumNotFoundException(n)

    /** Map a nodeNum to the nodeId string
    If we have a NodeInfo for this ID we prefer to return the string ID inside the user record.
    but some nodes might not have a user record at all (because not yet received), in that case, we return
    a hex version of the ID just based on the number */
    private fun toNodeID(n: Int): String =
        if (n == DataPacket.NODENUM_BROADCAST) {
            DataPacket.ID_BROADCAST
        } else {
            nodeDBbyNodeNum[n]?.user?.id ?: DataPacket.nodeNumToDefaultId(n)
        }

    // given a nodeNum, return a db entry - creating if necessary
    private fun getOrCreateNodeInfo(n: Int) = nodeDBbyNodeNum.getOrPut(n) {
        val userId = DataPacket.nodeNumToDefaultId(n)
        val defaultUser = user {
            id = userId
            longName = "Meshtastic ${userId.takeLast(n = 4)}"
            shortName = userId.takeLast(n = 4)
            hwModel = MeshProtos.HardwareModel.UNSET
        }

        NodeEntity(
            num = n,
            user = defaultUser,
            longName = defaultUser.longName,
        )
    }

    private val hexIdRegex = """\!([0-9A-Fa-f]+)""".toRegex()
    private val rangeTestRegex = Regex("seq (\\d{1,10})")

    // Map a userid to a node/ node num, or throw an exception if not found
    // We prefer to find nodes based on their assigned IDs, but if no ID has been assigned to a node, we can also find it based on node number
    private fun toNodeInfo(id: String): NodeEntity {
        // If this is a valid hexaddr will be !null
        val hexStr = hexIdRegex.matchEntire(id)?.groups?.get(1)?.value

        return nodeDBbyID[id] ?: when {
            id == DataPacket.ID_LOCAL -> toNodeInfo(myNodeNum)
            hexStr != null -> {
                val n = hexStr.toLong(16).toInt()
                nodeDBbyNodeNum[n] ?: throw IdNotFoundException(id)
            }
            else -> throw InvalidNodeIdException(id)
        }
    }

    private fun getUserName(num: Int): String {
        val user = nodeDBbyNodeNum[num]?.user
        val longName = user?.longName ?: getString(R.string.unknown_username)
        val shortName = user?.shortName ?: DataPacket.nodeNumToDefaultId(num)
        return "$longName ($shortName)"
    }

    private val numNodes get() = nodeDBbyNodeNum.size

    /**
     * How many nodes are currently online (including our local node)
     */
    private val numOnlineNodes get() = nodeDBbyNodeNum.values.count { it.isOnline }

    private fun toNodeNum(id: String): Int = when (id) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        DataPacket.ID_LOCAL -> myNodeNum
        else -> toNodeInfo(id).num
    }

    // A helper function that makes it easy to update node info objects
    private inline fun updateNodeInfo(
        nodeNum: Int,
        withBroadcast: Boolean = true,
        crossinline updateFn: (NodeEntity) -> Unit,
    ) {
        val info = getOrCreateNodeInfo(nodeNum)
        updateFn(info)

        if (info.user.id.isNotEmpty() && haveNodeDB) {
            serviceScope.handledLaunch {
                radioConfigRepository.upsert(info)
            }
        }

        if (withBroadcast) {
            serviceBroadcasts.broadcastNodeChange(info.toNodeInfo())
        }
    }

    // My node num
    private val myNodeNum
        get() = myNodeInfo?.myNodeNum
            ?: throw RadioNotConnectedException("We don't yet have our myNodeInfo")

    // My node ID string
    private val myNodeID get() = toNodeID(myNodeNum)

    // Admin channel index
    private val MeshPacket.Builder.adminChannelIndex: Int
        get() = when {
            myNodeNum == to -> 0
            nodeDBbyNodeNum[myNodeNum]?.hasPKC == true && nodeDBbyNodeNum[to]?.hasPKC == true ->
                DataPacket.PKC_CHANNEL_INDEX

            else -> channelSet.settingsList
                .indexOfFirst { it.name.equals("admin", ignoreCase = true) }
                .coerceAtLeast(0)
        }

    // Generate a new mesh packet builder with our node as the sender, and the specified node num
    private fun newMeshPacketTo(idNum: Int) = MeshPacket.newBuilder().apply {
        if (myNodeInfo == null) {
            throw RadioNotConnectedException()
        }

        from = 0 // don't add myNodeNum

        to = idNum
    }

    /**
     * Generate a new mesh packet builder with our node as the sender, and the specified recipient
     *
     * If id is null we assume a broadcast message
     */
    private fun newMeshPacketTo(id: String) = newMeshPacketTo(toNodeNum(id))

    /**
     * Helper to make it easy to build a subpacket in the proper protobufs
     */
    private fun MeshPacket.Builder.buildMeshPacket(
        wantAck: Boolean = false,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        hopLimit: Int = localConfig.lora.hopLimit,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        initFn: MeshProtos.Data.Builder.() -> Unit
    ): MeshPacket {
        this.wantAck = wantAck
        this.id = id
        this.hopLimit = hopLimit
        this.priority = priority
        decoded = MeshProtos.Data.newBuilder().also {
            initFn(it)
        }.build()
        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            pkiEncrypted = true
            nodeDBbyNodeNum[to]?.user?.publicKey?.let { publicKey ->
                this.publicKey = publicKey
            }
        } else {
            this.channel = channel
        }

        return build()
    }

    /**
     * Helper to make it easy to build a subpacket in the proper protobufs
     */
    private fun MeshPacket.Builder.buildAdminPacket(
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        wantResponse: Boolean = false,
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit
    ): MeshPacket = buildMeshPacket(
        id = id,
        wantAck = true,
        channel = adminChannelIndex,
        priority = MeshPacket.Priority.RELIABLE
    ) {
        this.wantResponse = wantResponse
        portnumValue = Portnums.PortNum.ADMIN_APP_VALUE
        payload = AdminProtos.AdminMessage.newBuilder().also {
            initFn(it)
            it.sessionPasskey = sessionPasskey
        }.build().toByteString()
    }

    // Generate a DataPacket from a MeshPacket, or null if we didn't have enough data to do so
    private fun toDataPacket(packet: MeshPacket): DataPacket? {
        return if (!packet.hasDecoded()) {
            // We never convert packets that are not DataPackets
            null
        } else {
            val data = packet.decoded

            // If the rxTime was not set by the device (because device software was old), guess at a time
            val rxTime = if (packet.rxTime != 0) packet.rxTime else currentSecond()

            DataPacket(
                from = toNodeID(packet.from),
                to = toNodeID(packet.to),
                time = rxTime * 1000L,
                id = packet.id,
                dataType = data.portnumValue,
                bytes = data.payload.toByteArray(),
                hopLimit = packet.hopLimit,
                channel = if (packet.pkiEncrypted) DataPacket.PKC_CHANNEL_INDEX else packet.channel,
            )
        }
    }

    private fun toMeshPacket(p: DataPacket): MeshPacket {
        return newMeshPacketTo(p.to!!).buildMeshPacket(
            id = p.id,
            wantAck = true,
            hopLimit = p.hopLimit,
            channel = p.channel,
        ) {
            portnumValue = p.dataType
            payload = ByteString.copyFrom(p.bytes)
        }
    }

    private val rememberDataType = setOf(
        Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
        Portnums.PortNum.WAYPOINT_APP_VALUE,
    )

    private fun rememberDataPacket(dataPacket: DataPacket, updateNotification: Boolean = true) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal = dataPacket.from == DataPacket.ID_LOCAL
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from

        // contactKey: unique contact key filter (channel)+(nodeId)
        val contactKey = "${dataPacket.channel}$contactId"

        val packetToSave = Packet(
            uuid = 0L, // autoGenerated
            myNodeNum = myNodeNum,
            packetId = dataPacket.id,
            port_num = dataPacket.dataType,
            contact_key = contactKey,
            received_time = System.currentTimeMillis(),
            read = fromLocal,
            data = dataPacket
        )
        serviceScope.handledLaunch {
            packetRepository.get().apply {
                insert(packetToSave)
                val isMuted = getContactSettings(contactKey).isMuted
                if (updateNotification && !isMuted) updateMessageNotification(dataPacket)
            }
        }
    }

    // Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedData(packet: MeshPacket) {
        myNodeInfo?.let { myInfo ->
            val data = packet.decoded
            val bytes = data.payload.toByteArray()
            val fromId = toNodeID(packet.from)
            val dataPacket = toDataPacket(packet)

            if (dataPacket != null) {

                // We ignore most messages that we sent
                val fromUs = myInfo.myNodeNum == packet.from

                debug("Received data from $fromId, portnum=${data.portnum} ${bytes.size} bytes")

                dataPacket.status = MessageStatus.RECEIVED

                // if (p.hasUser()) handleReceivedUser(fromNum, p.user)

                // We tell other apps about most message types, but some may have sensitive data, so that is not shared'
                var shouldBroadcast = !fromUs

                when (data.portnumValue) {
                    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> {
                        if (fromUs) return

                        // TODO temporary solution to Range Test spam, may be removed in the future
                        val isRangeTest = rangeTestRegex.matches(data.payload.toStringUtf8())
                        if (!moduleConfig.rangeTest.enabled && isRangeTest) return

                        debug("Received CLEAR_TEXT from $fromId")
                        rememberDataPacket(dataPacket)
                    }

                    Portnums.PortNum.WAYPOINT_APP_VALUE -> {
                        val u = MeshProtos.Waypoint.parseFrom(data.payload)
                        // Validate locked Waypoints from the original sender
                        if (u.lockedTo != 0 && u.lockedTo != packet.from) return
                        rememberDataPacket(dataPacket, u.expire > currentSecond())
                    }

                    // Handle new style position info
                    Portnums.PortNum.POSITION_APP_VALUE -> {
                        val u = MeshProtos.Position.parseFrom(data.payload)
                        // debug("position_app ${packet.from} ${u.toOneLineString()}")
                        if (data.wantResponse && u.latitudeI == 0 && u.longitudeI == 0) {
                            debug("Ignoring nop position update from position request")
                        } else {
                            handleReceivedPosition(packet.from, u, dataPacket.time)
                        }
                    }

                    // Handle new style user info
                    Portnums.PortNum.NODEINFO_APP_VALUE ->
                        if (!fromUs) {
                            val u = MeshProtos.User.parseFrom(data.payload)
                                .copy { if (packet.viaMqtt) longName = "$longName (MQTT)" }
                            handleReceivedUser(packet.from, u, packet.channel)
                        }

                    // Handle new telemetry info
                    Portnums.PortNum.TELEMETRY_APP_VALUE -> {
                        val u = TelemetryProtos.Telemetry.parseFrom(data.payload)
                            .copy { if (time == 0) time = (dataPacket.time / 1000L).toInt() }
                        handleReceivedTelemetry(packet.from, u)
                    }

                    Portnums.PortNum.ROUTING_APP_VALUE -> {
                        // We always send ACKs to other apps, because they might care about the messages they sent
                        shouldBroadcast = true
                        val u = MeshProtos.Routing.parseFrom(data.payload)

                        if (u.errorReason == MeshProtos.Routing.Error.DUTY_CYCLE_LIMIT) {
                            radioConfigRepository.setErrorMessage(getString(R.string.error_duty_cycle))
                        }

                        handleAckNak(data.requestId, fromId, u.errorReasonValue)
                        queueResponse.remove(data.requestId)?.complete(true)
                    }

                    Portnums.PortNum.ADMIN_APP_VALUE -> {
                        val u = AdminProtos.AdminMessage.parseFrom(data.payload)
                        handleReceivedAdmin(packet.from, u)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.PAXCOUNTER_APP_VALUE -> {
                        val p = PaxcountProtos.Paxcount.parseFrom(data.payload)
                        handleReceivedPaxcounter(packet.from, p)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.STORE_FORWARD_APP_VALUE -> {
                        val u = StoreAndForwardProtos.StoreAndForward.parseFrom(data.payload)
                        handleReceivedStoreAndForward(dataPacket, u)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.RANGE_TEST_APP_VALUE -> {
                        if (!moduleConfig.rangeTest.enabled) return
                        val u = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                        rememberDataPacket(u)
                    }

                    Portnums.PortNum.DETECTION_SENSOR_APP_VALUE -> {
                        val u = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                        rememberDataPacket(u)
                    }

                    Portnums.PortNum.TRACEROUTE_APP_VALUE -> {
                        if (data.wantResponse) return // ignore data from traceroute requests
                        val parsed = MeshProtos.RouteDiscovery.parseFrom(data.payload)
                        handleReceivedTraceroute(packet, parsed)
                    }

                    else -> debug("No custom processing needed for ${data.portnumValue}")
                }

                // We always tell other apps when new data packets arrive
                if (shouldBroadcast) {
                    serviceBroadcasts.broadcastReceivedData(dataPacket)
                }

                GeeksvilleApplication.analytics.track(
                    "num_data_receive",
                    DataPair(1)
                )

                GeeksvilleApplication.analytics.track(
                    "data_receive",
                    DataPair("num_bytes", bytes.size),
                    DataPair("type", data.portnumValue)
                )
            }
        }
    }

    private fun handleReceivedAdmin(fromNodeNum: Int, a: AdminProtos.AdminMessage) {
        if (fromNodeNum == myNodeNum) {
            when (a.payloadVariantCase) {
                AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                    val response = a.getConfigResponse
                    debug("Admin: received config ${response.payloadVariantCase}")
                    setLocalConfig(response)
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                    val mi = myNodeInfo
                    if (mi != null) {
                        val ch = a.getChannelResponse
                        debug("Admin: Received channel ${ch.index}")

                        if (ch.index + 1 < mi.maxChannels) {
                            handleChannel(ch)
                        }
                    }
                }
                else -> warn("No special processing needed for ${a.payloadVariantCase}")
            }
        } else {
            debug("Admin: Received session_passkey from $fromNodeNum")
            sessionPasskey = a.sessionPasskey
        }
    }

    // Update our DB of users based on someone sending out a User subpacket
    private fun handleReceivedUser(fromNum: Int, p: MeshProtos.User, channel: Int = 0) {
        updateNodeInfo(fromNum) {
            val keyMatch = !it.hasPKC || it.user.publicKey == p.publicKey
            it.user = if (keyMatch) p else p.copy {
                warn("Public key mismatch from $longName ($shortName)")
                publicKey = it.errorByteString
            }
            it.longName = p.longName
            it.shortName = p.shortName
            it.channel = channel
        }
    }

    /** Update our DB of users based on someone sending out a Position subpacket
     * @param defaultTime in msecs since 1970
     */
    private fun handleReceivedPosition(
        fromNum: Int,
        p: MeshProtos.Position,
        defaultTime: Long = System.currentTimeMillis()
    ) {
        // Nodes periodically send out position updates, but those updates might not contain a lat & lon (because no GPS lock)
        // We like to look at the local node to see if it has been sending out valid lat/lon, so for the LOCAL node (only)
        // we don't record these nop position updates
        if (myNodeNum == fromNum && p.latitudeI == 0 && p.longitudeI == 0) {
            debug("Ignoring nop position update for the local node")
        } else {
            updateNodeInfo(fromNum) {
                debug("update position: ${it.longName?.toPIIString()} with ${p.toPIIString()}")
                it.setPosition(p, (defaultTime / 1000L).toInt())
            }
        }
    }

    // Update our DB of users based on someone sending out a Telemetry subpacket
    private fun handleReceivedTelemetry(
        fromNum: Int,
        t: TelemetryProtos.Telemetry,
    ) {
        if (t.hasLocalStats()) {
            localStatsTelemetry = t
            maybeUpdateServiceStatusNotification()
        }
        updateNodeInfo(fromNum) {
            when {
                t.hasDeviceMetrics() -> it.deviceTelemetry = t
                t.hasEnvironmentMetrics() -> it.environmentTelemetry = t
                t.hasPowerMetrics() -> it.powerTelemetry = t
            }
        }
    }

    private fun handleReceivedPaxcounter(fromNum: Int, p: PaxcountProtos.Paxcount) {
        updateNodeInfo(fromNum) { it.paxcounter = p }
    }

    private fun handleReceivedStoreAndForward(
        dataPacket: DataPacket,
        s: StoreAndForwardProtos.StoreAndForward,
    ) {
        debug("StoreAndForward: ${s.variantCase} ${s.rr} from ${dataPacket.from}")
        when (s.variantCase) {
            StoreAndForwardProtos.StoreAndForward.VariantCase.STATS -> {
                val u = dataPacket.copy(
                    bytes = s.stats.toString().encodeToByteArray(),
                    dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                )
                rememberDataPacket(u)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY -> {
                val text = """
                    Total messages: ${s.history.historyMessages}
                    History window: ${s.history.window / 60000} min
                    Last request: ${s.history.lastRequest}
                """.trimIndent()
                val u = dataPacket.copy(
                    bytes = text.encodeToByteArray(),
                    dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                )
                rememberDataPacket(u)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.TEXT -> {
                if (s.rr == StoreAndForwardProtos.StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                    dataPacket.to = DataPacket.ID_BROADCAST
                }
                val u = dataPacket.copy(
                    bytes = s.text.toByteArray(),
                    dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                )
                rememberDataPacket(u)
            }

            else -> {}
        }
    }

    @Suppress("MagicNumber")
    private fun formatTraceroutePath(nodesList: List<Int>, snrList: List<Int>): String {
        // nodesList should include both origin and destination nodes
        // origin will not have an SNR value, but destination should
        val snrStr = if (snrList.size == nodesList.size - 1) {
            snrList
        } else {
            // use unknown SNR for entire route if snrList has invalid size
            List(nodesList.size - 1) { -128 }
        }.map { snr ->
            val str = if (snr == -128) "?" else "${snr / 4}"
            "⇊ $str dB"
        }

        return nodesList.map { nodeId ->
            "■ ${getUserName(nodeId)}"
        }.flatMapIndexed { i, nodeStr ->
            if (i == 0) listOf(nodeStr) else listOf(snrStr[i - 1], nodeStr)
        }.joinToString("\n")
    }

    private fun handleReceivedTraceroute(packet: MeshPacket, trace: MeshProtos.RouteDiscovery) {
        val nodesToward = mutableListOf<Int>()
        nodesToward.add(packet.to)
        nodesToward += trace.routeList
        nodesToward.add(packet.from)

        val nodesBack = mutableListOf<Int>()
        if (packet.hopStart > 0 && trace.snrBackList.size > 0) { // otherwise back route is invalid
            nodesBack.add(packet.from)
            nodesBack += trace.routeBackList
            nodesBack.add(packet.to)
        }

        radioConfigRepository.setTracerouteResponse(buildString {
            append("Route traced toward destination:\n\n")
            append(formatTraceroutePath(nodesToward, trace.snrTowardsList))
            if (nodesBack.size > 0) {
                append("\n\n")
                append("Route traced back to us:\n\n")
                append(formatTraceroutePath(nodesBack, trace.snrBackList))
            }
        })
    }

    // If apps try to send packets when our radio is sleeping, we queue them here instead
    private val offlineSentPackets = mutableListOf<DataPacket>()

    // Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        if (haveNodeDB) {
            processReceivedMeshPacket(packet)
            onNodeDBChanged()
        } else {
            warn("Ignoring early received packet: ${packet.toOneLineString()}")
            // earlyReceivedPackets.add(packet)
            // logAssert(earlyReceivedPackets.size < 128) // The max should normally be about 32, but if the device is messed up it might try to send forever
        }
    }

    private val queuedPackets = ConcurrentLinkedQueue<MeshPacket>()
    private val queueResponse = mutableMapOf<Int, CompletableFuture<Boolean>>()
    private var queueJob: Job? = null

    private fun sendPacket(packet: MeshPacket): CompletableFuture<Boolean> {
        // send the packet to the radio and return a CompletableFuture that will be completed with the result
        val future = CompletableFuture<Boolean>()
        queueResponse[packet.id] = future
        try {
            if (connectionState != ConnectionState.CONNECTED) throw RadioNotConnectedException()
            sendToRadio(ToRadio.newBuilder().apply {
                this.packet = packet
            })
        } catch (ex: Exception) {
            errormsg("sendToRadio error:", ex)
            future.complete(false)
        }
        return future
    }

    private fun startPacketQueue() {
        if (queueJob?.isActive == true) return
        queueJob = serviceScope.handledLaunch {
            debug("packet queueJob started")
            while (connectionState == ConnectionState.CONNECTED) {
                // take the first packet from the queue head
                val packet = queuedPackets.poll() ?: break
                try {
                    // send packet to the radio and wait for response
                    val response = sendPacket(packet)
                    debug("queueJob packet id=${packet.id.toUInt()} waiting")
                    val success = response.get(2, TimeUnit.MINUTES)
                    debug("queueJob packet id=${packet.id.toUInt()} success $success")
                } catch (e: TimeoutException) {
                    debug("queueJob packet id=${packet.id.toUInt()} timeout")
                } catch (e: Exception) {
                    debug("queueJob packet id=${packet.id.toUInt()} failed")
                }
            }
        }
    }

    private fun stopPacketQueue() {
        if (queueJob?.isActive == true) {
            info("Stopping packet queueJob")
            queueJob?.cancel()
            queueJob = null
            queuedPackets.clear()
            queueResponse.entries.lastOrNull { !it.value.isDone }?.value?.complete(false)
            queueResponse.clear()
        }
    }

    private fun sendNow(p: DataPacket) {
        val packet = toMeshPacket(p)
        p.time = System.currentTimeMillis() // update time to the actual time we started sending
        // debug("Sending to radio: ${packet.toPIIString()}")
        sendToRadio(packet)
    }

    private fun processQueuedPackets() {
        val sentPackets = mutableListOf<DataPacket>()
        offlineSentPackets.forEach { p ->
            try {
                sendNow(p)
                sentPackets.add(p)
            } catch (ex: Exception) {
                errormsg("Error sending queued message:", ex)
            }
        }
        offlineSentPackets.removeAll(sentPackets)
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1000) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.get().getPacketById(packetId)?.data
            if (dataPacket == null) delay(100)
        }
        dataPacket
    }

    /**
     * Change the status on a DataPacket and update watchers
     */
    private fun changeStatus(packetId: Int, m: MessageStatus) = serviceScope.handledLaunch {
        if (packetId != 0) getDataPacketById(packetId)?.let { p ->
            if (p.status == m) return@handledLaunch
            packetRepository.get().updateMessageStatus(p, m)
            serviceBroadcasts.broadcastMessageStatus(packetId, m)
        }
    }

    /**
     * Handle an ack/nak packet by updating sent message status
     */
    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int) {
        serviceScope.handledLaunch {
            val isAck = routingError == MeshProtos.Routing.Error.NONE_VALUE
            val p = packetRepository.get().getPacketById(requestId)
            // distinguish real ACKs coming from the intended receiver
            val m = when {
                isAck && fromId == p?.data?.to -> MessageStatus.RECEIVED
                isAck -> MessageStatus.DELIVERED
                else -> MessageStatus.ERROR
            }
            if (p != null && p.data.status != MessageStatus.RECEIVED) {
                p.data.status = m
                p.routingError = routingError
                packetRepository.get().update(p)
            }
            serviceBroadcasts.broadcastMessageStatus(requestId, m)
        }
    }

    // Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun processReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from

        // FIXME, perhaps we could learn our node ID by looking at any to packets the radio
        // decided to pass through to us (except for broadcast packets)
        // val toNum = packet.to

        // debug("Recieved: $packet")
        if (packet.hasDecoded()) {
            val packetToSave = MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Packet",
                received_date = System.currentTimeMillis(),
                raw_message = packet.toString(),
                fromNum = packet.from,
                portNum = packet.decoded.portnumValue,
                fromRadio = fromRadio { this.packet = packet },
            )
            insertMeshLog(packetToSave)

            serviceScope.handledLaunch {
                radioConfigRepository.emitMeshPacket(packet)
            }

            // Update last seen for the node that sent the packet, but also for _our node_ because anytime a packet passes
            // through our node on the way to the phone that means that local node is also alive in the mesh

            val isOtherNode = myNodeNum != fromNum
            updateNodeInfo(myNodeNum, withBroadcast = isOtherNode) {
                it.lastHeard = currentSecond()
            }

            // Do not generate redundant broadcasts of node change for this bookkeeping updateNodeInfo call
            // because apps really only care about important updates of node state - which handledReceivedData will give them
            updateNodeInfo(fromNum, withBroadcast = false) {
                // If the rxTime was not set by the device (because device software was old), guess at a time
                val rxTime = if (packet.rxTime != 0) packet.rxTime else currentSecond()

                // Update our last seen based on any valid timestamps.  If the device didn't provide a timestamp make one
                it.lastHeard = rxTime
                it.snr = packet.rxSnr
                it.rssi = packet.rxRssi

                // Generate our own hopsAway, comparing hopStart to hopLimit.
                it.hopsAway = if (packet.hopStart == 0 || packet.hopLimit > packet.hopStart) {
                    -1
                } else {
                    packet.hopStart - packet.hopLimit
                }
            }
            handleReceivedData(packet)
        }
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        serviceScope.handledLaunch {
            // Do not log, because might contain PII
            // info("insert: ${packetToSave.message_type} = ${packetToSave.raw_message.toOneLineString()}")
            meshLogRepository.get().insert(packetToSave)
        }
    }

    private fun setLocalConfig(config: ConfigProtos.Config) {
        serviceScope.handledLaunch {
            radioConfigRepository.setLocalConfig(config)
        }
    }

    private fun setLocalModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        serviceScope.handledLaunch {
            radioConfigRepository.setLocalModuleConfig(config)
        }
    }

    private fun clearLocalConfig() {
        serviceScope.handledLaunch {
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    private fun updateChannelSettings(ch: ChannelProtos.Channel) = serviceScope.handledLaunch {
        radioConfigRepository.updateChannelSettings(ch)
    }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()

    // If we just changed our nodedb, we might want to do somethings
    private fun onNodeDBChanged() {
        maybeUpdateServiceStatusNotification()
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

    // msecs since 1970 we started this connection
    private var connectTimeMsec = 0L

    // Called when we gain/lose connection to our radio
    private fun onConnectionChanged(c: ConnectionState) {
        debug("onConnectionChanged: $connectionState -> $c")

        // Perform all the steps needed once we start waiting for device sleep to complete
        fun startDeviceSleep() {
            stopPacketQueue()
            stopLocationRequests()
            stopMqttClientProxy()

            if (connectTimeMsec != 0L) {
                val now = System.currentTimeMillis()
                connectTimeMsec = 0L

                GeeksvilleApplication.analytics.track(
                    "connected_seconds",
                    DataPair((now - connectTimeMsec) / 1000.0)
                )
            }

            // Have our timeout fire in the appropriate number of seconds
            sleepTimeout = serviceScope.handledLaunch {
                try {
                    // If we have a valid timeout, wait that long (+30 seconds) otherwise, just wait 30 seconds
                    val timeout = (localConfig.power?.lsSecs ?: 0) + 30

                    debug("Waiting for sleeping device, timeout=$timeout secs")
                    delay(timeout * 1000L)
                    warn("Device timeout out, setting disconnected")
                    onConnectionChanged(ConnectionState.DISCONNECTED)
                } catch (ex: CancellationException) {
                    debug("device sleep timeout cancelled")
                }
            }

            // broadcast an intent with our new connection state
            serviceBroadcasts.broadcastConnection()
        }

        fun startDisconnect() {
            stopPacketQueue()
            stopLocationRequests()
            stopMqttClientProxy()

            GeeksvilleApplication.analytics.track(
                "mesh_disconnect",
                DataPair("num_nodes", numNodes),
                DataPair("num_online", numOnlineNodes)
            )
            GeeksvilleApplication.analytics.track("num_nodes", DataPair(numNodes))

            // broadcast an intent with our new connection state
            serviceBroadcasts.broadcastConnection()
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
                throw ex // Important to rethrow so that we don't tell the app all is well
            }
        }

        // Cancel any existing timeouts
        sleepTimeout?.let {
            it.cancel()
            sleepTimeout = null
        }

        connectionState = c
        when (c) {
            ConnectionState.CONNECTED -> startConnect()
            ConnectionState.DEVICE_SLEEP -> startDeviceSleep()
            ConnectionState.DISCONNECTED -> startDisconnect()
        }

        // Update the android notification in the status bar
        maybeUpdateServiceStatusNotification()
    }

    private fun maybeUpdateServiceStatusNotification() {
        var update = false
        val currentSummary = notificationSummary
        val currentStats = localStats
        val currentStatsUpdatedAtMillis = localStatsUpdatedAtMillis
        if (
            !currentSummary.isNullOrBlank() &&
            (previousSummary == null || !previousSummary.equals(currentSummary))
        ) {
            previousSummary = currentSummary
            update = true
        }
        if (
            currentStats != null &&
            (previousStats == null || !(previousStats?.equals(currentStats) ?: false))
        ) {
            previousStats = currentStats
            update = true
        }
        if (update) {
            serviceNotifications.updateServiceStateNotification(
                summaryString = currentSummary,
                localStats = currentStats,
                currentStatsUpdatedAtMillis = currentStatsUpdatedAtMillis
            )
        }
    }

    private fun onRadioConnectionState(state: RadioServiceConnectionState) {
        // sleep now disabled by default on ESP32, permanent is true unless light sleep enabled
        val isRouter = localConfig.device.role == ConfigProtos.Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power.isPowerSaving || isRouter
        val connected = state.isConnected
        val permanent = state.isPermanent || !lsEnabled
        onConnectionChanged(
            when {
                connected -> ConnectionState.CONNECTED
                permanent -> ConnectionState.DISCONNECTED
                else -> ConnectionState.DEVICE_SLEEP
            }
        )
    }

    private fun onReceiveFromRadio(bytes: ByteArray) {
        try {
            val proto = MeshProtos.FromRadio.parseFrom(bytes)
            // info("Received from radio service: ${proto.toOneLineString()}")
            when (proto.payloadVariantCase.number) {
                MeshProtos.FromRadio.PACKET_FIELD_NUMBER -> handleReceivedMeshPacket(proto.packet)
                MeshProtos.FromRadio.CONFIG_COMPLETE_ID_FIELD_NUMBER -> handleConfigComplete(proto.configCompleteId)
                MeshProtos.FromRadio.MY_INFO_FIELD_NUMBER -> handleMyInfo(proto.myInfo)
                MeshProtos.FromRadio.NODE_INFO_FIELD_NUMBER -> handleNodeInfo(proto.nodeInfo)
                MeshProtos.FromRadio.CHANNEL_FIELD_NUMBER -> handleChannel(proto.channel)
                MeshProtos.FromRadio.CONFIG_FIELD_NUMBER -> handleDeviceConfig(proto.config)
                MeshProtos.FromRadio.MODULECONFIG_FIELD_NUMBER -> handleModuleConfig(proto.moduleConfig)
                MeshProtos.FromRadio.QUEUESTATUS_FIELD_NUMBER -> handleQueueStatus(proto.queueStatus)
                MeshProtos.FromRadio.METADATA_FIELD_NUMBER -> handleMetadata(proto.metadata)
                MeshProtos.FromRadio.MQTTCLIENTPROXYMESSAGE_FIELD_NUMBER -> handleMqttProxyMessage(proto.mqttClientProxyMessage)
                MeshProtos.FromRadio.CLIENTNOTIFICATION_FIELD_NUMBER -> {
                    handleClientNotification(proto.clientNotification)
                }
                else -> errormsg("Unexpected FromRadio variant")
            }
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("Invalid Protobuf from radio, len=${bytes.size}", ex)
        }
    }

    // A provisional MyNodeInfo that we will install if all of our node config downloads go okay
    private var newMyNodeInfo: MyNodeEntity? = null

    // provisional NodeInfos we will install if all goes well
    private val newNodes = mutableListOf<MeshProtos.NodeInfo>()

    // Used to make sure we never get foold by old BLE packets
    private var configNonce = 1

    private fun handleDeviceConfig(config: ConfigProtos.Config) {
        debug("Received config ${config.toOneLineString()}")
        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "Config ${config.payloadVariantCase}",
            received_date = System.currentTimeMillis(),
            raw_message = config.toString(),
            fromRadio = fromRadio { this.config = config },
        )
        insertMeshLog(packetToSave)
        setLocalConfig(config)
        val configCount = localConfig.allFields.size
        radioConfigRepository.setStatusMessage("Device config ($configCount / $configTotal)")
    }

    private fun handleModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        debug("Received moduleConfig ${config.toOneLineString()}")
        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "ModuleConfig ${config.payloadVariantCase}",
            received_date = System.currentTimeMillis(),
            raw_message = config.toString(),
            fromRadio = fromRadio { moduleConfig = config },
        )
        insertMeshLog(packetToSave)
        setLocalModuleConfig(config)
        val moduleCount = moduleConfig.allFields.size
        radioConfigRepository.setStatusMessage("Module config ($moduleCount / $moduleTotal)")
    }

    private fun handleQueueStatus(queueStatus: MeshProtos.QueueStatus) {
        debug("queueStatus ${queueStatus.toOneLineString()}")
        val (success, isFull, requestId) = with(queueStatus) {
            Triple(res == 0, free == 0, meshPacketId)
        }
        if (success && isFull) return // Queue is full, wait for free != 0
        if (requestId != 0) {
            queueResponse.remove(requestId)?.complete(success)
        } else {
            queueResponse.entries.lastOrNull { !it.value.isDone }?.value?.complete(success)
        }
    }

    private fun handleChannel(ch: ChannelProtos.Channel) {
        debug("Received channel ${ch.index}")
        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "Channel",
            received_date = System.currentTimeMillis(),
            raw_message = ch.toString(),
            fromRadio = fromRadio { channel = ch },
        )
        insertMeshLog(packetToSave)
        if (ch.role != ChannelProtos.Channel.Role.DISABLED) updateChannelSettings(ch)
        val maxChannels = myNodeInfo?.maxChannels ?: 8
        radioConfigRepository.setStatusMessage("Channels (${ch.index + 1} / $maxChannels)")
    }

    /**
     * Convert a protobuf NodeInfo into our model objects and update our node DB
     */
    private fun installNodeInfo(info: MeshProtos.NodeInfo) {
        // Just replace/add any entry
        updateNodeInfo(info.num) {
            if (info.hasUser()) {
                it.user = info.user.copy { if (info.viaMqtt) longName = "$longName (MQTT)" }
                it.longName = info.user.longName
                it.shortName = info.user.shortName
            }

            if (info.hasPosition()) {
                it.position = info.position
                it.latitude = Position.degD(info.position.latitudeI)
                it.longitude = Position.degD(info.position.longitudeI)
            }

            it.lastHeard = info.lastHeard

            if (info.hasDeviceMetrics()) {
                it.deviceTelemetry = telemetry { deviceMetrics = info.deviceMetrics }
            }

            it.channel = info.channel
            it.viaMqtt = info.viaMqtt

            // hopsAway should be nullable/optional from the proto, but explicitly checking it's existence first
            it.hopsAway = if (info.hasHopsAway()) {
                info.hopsAway
            } else {
                -1
            }
            it.isFavorite = info.isFavorite
        }
    }

    private fun handleNodeInfo(info: MeshProtos.NodeInfo) {
        debug("Received nodeinfo num=${info.num}, hasUser=${info.hasUser()}, hasPosition=${info.hasPosition()}, hasDeviceMetrics=${info.hasDeviceMetrics()}")

        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "NodeInfo",
            received_date = System.currentTimeMillis(),
            raw_message = info.toString(),
            fromRadio = fromRadio { nodeInfo = info },
        )
        insertMeshLog(packetToSave)

        newNodes.add(info)
        radioConfigRepository.setStatusMessage("Nodes (${newNodes.size} / 100)")
    }

    private var rawMyNodeInfo: MeshProtos.MyNodeInfo? = null
    private var rawDeviceMetadata: MeshProtos.DeviceMetadata? = null

    /** Regenerate the myNodeInfo model.  We call this twice.  Once after we receive myNodeInfo from the device
     * and again after we have the node DB (which might allow us a better notion of our HwModel.
     */
    private fun regenMyNodeInfo() {
        val myInfo = rawMyNodeInfo
        if (myInfo != null) {
            val mi = with(myInfo) {
                MyNodeEntity(
                    myNodeNum = myNodeNum,
                    model = when (val hwModel = rawDeviceMetadata?.hwModel) {
                        null, MeshProtos.HardwareModel.UNSET -> null
                        else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                    },
                    firmwareVersion = rawDeviceMetadata?.firmwareVersion,
                    couldUpdate = false,
                    shouldUpdate = false, // TODO add check after re-implementing firmware updates
                    currentPacketId = currentPacketId and 0xffffffffL,
                    messageTimeoutMsec = 5 * 60 * 1000, // constants from current firmware code
                    minAppVersion = minAppVersion,
                    maxChannels = 8,
                    hasWifi = rawDeviceMetadata?.hasWifi ?: false,
                )
            }
            newMyNodeInfo = mi
        }
    }

    private fun sendAnalytics() {
        val myInfo = rawMyNodeInfo
        val mi = myNodeInfo
        if (myInfo != null && mi != null) {
            // Track types of devices and firmware versions in use
            GeeksvilleApplication.analytics.setUserInfo(
                DataPair("firmware", mi.firmwareVersion),
                DataPair("hw_model", mi.model),
            )
        }
    }

    /**
     * Update MyNodeInfo (called from either new API version or the old one)
     */
    private fun handleMyInfo(myInfo: MeshProtos.MyNodeInfo) {
        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "MyNodeInfo",
            received_date = System.currentTimeMillis(),
            raw_message = myInfo.toString(),
            fromRadio = fromRadio { this.myInfo = myInfo },
        )
        insertMeshLog(packetToSave)

        rawMyNodeInfo = myInfo

        // We'll need to get a new set of channels and settings now
        serviceScope.handledLaunch {
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    /**
     * Update our DeviceMetadata
     */
    private fun handleMetadata(metadata: MeshProtos.DeviceMetadata) {
        debug("Received deviceMetadata ${metadata.toOneLineString()}")
        val packetToSave = MeshLog(
            uuid = UUID.randomUUID().toString(),
            message_type = "DeviceMetadata",
            received_date = System.currentTimeMillis(),
            raw_message = metadata.toString(),
            fromRadio = fromRadio { this.metadata = metadata },
        )
        insertMeshLog(packetToSave)

        rawDeviceMetadata = metadata
        regenMyNodeInfo()
    }

    /**
     * Publish MqttClientProxyMessage (fromRadio)
     */
    private fun handleMqttProxyMessage(message: MeshProtos.MqttClientProxyMessage) {
        with(message) {
            when (payloadVariantCase) {
                MeshProtos.MqttClientProxyMessage.PayloadVariantCase.TEXT -> {
                    mqttRepository.publish(topic, text.encodeToByteArray(), retained)
                }

                MeshProtos.MqttClientProxyMessage.PayloadVariantCase.DATA -> {
                    mqttRepository.publish(topic, data.toByteArray(), retained)
                }

                else -> {}
            }
        }
    }

    private fun handleClientNotification(notification: MeshProtos.ClientNotification) {
        debug("Received clientNotification ${notification.toOneLineString()}")
        radioConfigRepository.setErrorMessage(notification.message)
        // if the future for the originating request is still in the queue, complete as unsuccessful for now
        queueResponse.remove(notification.replyId)?.complete(false)
    }

    /**
     * Connect, subscribe and receive Flow of MqttClientProxyMessage (toRadio)
     */
    private fun startMqttClientProxy() {
        if (mqttMessageFlow?.isActive == true) return
        if (moduleConfig.mqtt.enabled && moduleConfig.mqtt.proxyToClientEnabled) {
            mqttMessageFlow = mqttRepository.proxyMessageFlow.onEach { message ->
                sendToRadio(ToRadio.newBuilder().apply { mqttClientProxyMessage = message })
            }.catch { throwable ->
                radioConfigRepository.setErrorMessage("MqttClientProxy failed: $throwable")
            }.launchIn(serviceScope)
        }
    }

    private fun stopMqttClientProxy() {
        if (mqttMessageFlow?.isActive == true) {
            info("Stopping MqttClientProxy")
            mqttMessageFlow?.cancel()
            mqttMessageFlow = null
        }
    }

    // If we've received our initial config, our radio settings and all of our channels, send any queued packets and broadcast connected to clients
    private fun onHasSettings() {

        processQueuedPackets() // send any packets that were queued up
        startMqttClientProxy()

        // broadcast an intent with our new connection state
        serviceBroadcasts.broadcastConnection()
        onNodeDBChanged()
        reportConnection()
    }

    private fun handleConfigComplete(configCompleteId: Int) {
        if (configCompleteId == configNonce) {

            val packetToSave = MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ConfigComplete",
                received_date = System.currentTimeMillis(),
                raw_message = configCompleteId.toString(),
                fromRadio = fromRadio { this.configCompleteId = configCompleteId },
            )
            insertMeshLog(packetToSave)

            // This was our config request
            if (newMyNodeInfo == null || newNodes.isEmpty()) {
                errormsg("Did not receive a valid config")
            } else {
                discardNodeDB()
                debug("Installing new node DB")
                myNodeInfo = newMyNodeInfo

                newNodes.forEach(::installNodeInfo)
                newNodes.clear() // Just to save RAM ;-)

                serviceScope.handledLaunch {
                    radioConfigRepository.installNodeDB(myNodeInfo!!, nodeDBbyNodeNum.values.toList())
                }

                haveNodeDB = true // we now have nodes from real hardware

                sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket {
                    setTimeOnly = currentSecond()
                })
                sendAnalytics()

                if (deviceVersion < minDeviceVersion || appVersion < minAppVersion) {
                    info("Device firmware or app is too old, faking config so firmware update can occur")
                    clearLocalConfig()
                    setLocalConfig(config { security = security.copy { isManaged = true } })
                }
                onHasSettings()
            }
        } else {
            warn("Ignoring stale config complete")
        }
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

    /**
     * Send a position (typically from our built in GPS) into the mesh.
     */
    private fun sendPosition(
        position: MeshProtos.Position,
        destNum: Int? = null,
        wantResponse: Boolean = false
    ) {
        try {
            val mi = myNodeInfo
            if (mi != null) {
                val idNum = destNum ?: mi.myNodeNum // when null we just send to the local node
                debug("Sending our position/time to=$idNum ${Position(position)}")

                // Also update our own map for our nodeNum, by handling the packet just like packets from other users
                if (!localConfig.position.fixedPosition) {
                    handleReceivedPosition(mi.myNodeNum, position)
                }

                sendToRadio(newMeshPacketTo(idNum).buildMeshPacket(
                    channel = if (destNum == null) 0 else nodeDBbyNodeNum[destNum]?.channel ?: 0,
                    priority = MeshPacket.Priority.BACKGROUND,
                ) {
                    portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                    payload = position.toByteString()
                    this.wantResponse = wantResponse
                })
            }
        } catch (ex: BLEException) {
            warn("Ignoring disconnected radio during gps location update")
        }
    }

    /**
     * Send setOwner admin packet with [MeshProtos.User] protobuf
     */
    private fun setOwner(packetId: Int, user: MeshProtos.User) = with(user) {
        val dest = nodeDBbyID[id]
            ?: throw Exception("Can't set user without a NodeInfo") // this shouldn't happen
        val old = dest.user
        if (longName == old.longName && shortName == old.shortName && isLicensed == old.isLicensed) {
            debug("Ignoring nop owner change")
        } else {
            debug("setOwner Id: $id longName: ${longName.anonymize} shortName: $shortName isLicensed: $isLicensed")

            // Also update our own map for our nodeNum, by handling the packet just like packets from other users
            handleReceivedUser(dest.num, user)

            // encapsulate our payload in the proper protobuf and fire it off
            sendToRadio(newMeshPacketTo(dest.num).buildAdminPacket(id = packetId) {
                setOwner = user
            })
        }
    }

    // Do not use directly, instead call generatePacketId()
    private var currentPacketId = Random(System.currentTimeMillis()).nextLong().absoluteValue

    /**
     * Generate a unique packet ID (if we know enough to do so - otherwise return 0 so the device will do it)
     */
    @Synchronized
    private fun generatePacketId(): Int {
        val numPacketIds =
            ((1L shl 32) - 1) // A mask for only the valid packet ID bits, either 255 or maxint

        currentPacketId++

        currentPacketId = currentPacketId and 0xffffffff // keep from exceeding 32 bits

        // Use modulus and +1 to ensure we skip 0 on any values we return
        return ((currentPacketId % numPacketIds) + 1L).toInt()
    }

    private fun enqueueForSending(p: DataPacket) {
        if (p.dataType in rememberDataType) {
            offlineSentPackets.add(p)
        }
    }

    private val binder = object : IMeshService.Stub() {

        override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
            debug("Passing through device change to radio service: ${deviceAddr.anonymize}")

            val res = radioInterfaceService.setDeviceAddress(deviceAddr)
            if (res) {
                discardNodeDB()
            } else {
                serviceBroadcasts.broadcastConnection()
            }
            res
        }

        // Note: bound methods don't get properly exception caught/logged, so do that with a wrapper
        // per https://blog.classycode.com/dealing-with-exceptions-in-aidl-9ba904c6d63
        override fun subscribeReceiver(packageName: String, receiverName: String) =
            toRemoteExceptions {
                clientPackages[receiverName] = packageName
            }

        override fun getUpdateStatus(): Int = -4 // ProgressNotStarted

        override fun startFirmwareUpdate() = toRemoteExceptions {
            // TODO reimplement this after we have a new firmware update mechanism
        }

        override fun getMyNodeInfo(): MyNodeInfo? = this@MeshService.myNodeInfo?.toMyNodeInfo()

        override fun getMyId() = toRemoteExceptions { myNodeID }

        override fun getPacketId() = toRemoteExceptions { generatePacketId() }

        override fun setOwner(user: MeshUser) = toRemoteExceptions {
            setOwner(generatePacketId(), user {
                id = user.id
                longName = user.longName
                shortName = user.shortName
                isLicensed = user.isLicensed
            })
        }

        override fun setRemoteOwner(id: Int, payload: ByteArray) = toRemoteExceptions {
            val parsed = MeshProtos.User.parseFrom(payload)
            setOwner(id, parsed)
        }

        override fun getRemoteOwner(id: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                getOwnerRequest = true
            })
        }

        override fun send(p: DataPacket) {
            toRemoteExceptions {
                if (p.id == 0) p.id = generatePacketId()

                info("sendData dest=${p.to}, id=${p.id} <- ${p.bytes!!.size} bytes (connectionState=$connectionState)")

                if (p.dataType == 0) {
                    throw Exception("Port numbers must be non-zero!") // we are now more strict
                }

                if (p.bytes.size >= MeshProtos.Constants.DATA_PAYLOAD_LEN.number) {
                    p.status = MessageStatus.ERROR
                    throw RemoteException("Message too long")
                } else {
                    p.status = MessageStatus.QUEUED
                }

                if (connectionState == ConnectionState.CONNECTED) try {
                    sendNow(p)
                } catch (ex: Exception) {
                    errormsg("Error sending message, so enqueueing", ex)
                    enqueueForSending(p)
                } else {
                    enqueueForSending(p)
                }
                serviceBroadcasts.broadcastMessageStatus(p)

                // Keep a record of DataPackets, so GUIs can show proper chat history
                rememberDataPacket(p, false)

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

        override fun getConfig(): ByteArray = toRemoteExceptions {
            this@MeshService.localConfig.toByteArray() ?: throw NoDeviceConfigException()
        }

        /** Send our current radio config to the device
         */
        override fun setConfig(payload: ByteArray) = toRemoteExceptions {
            setRemoteConfig(generatePacketId(), myNodeNum, payload)
        }

        override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
            debug("Setting new radio config!")
            val config = ConfigProtos.Config.parseFrom(payload)
            sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setConfig = config })
            if (num == myNodeNum) setLocalConfig(config) // Update our local copy
        }

        override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                if (config == AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE) {
                    getDeviceMetadataRequest = true
                } else {
                    getConfigRequestValue = config
                }
            })
        }

        /** Send our current module config to the device
         */
        override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
            debug("Setting new module config!")
            val config = ModuleConfigProtos.ModuleConfig.parseFrom(payload)
            sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setModuleConfig = config })
            if (num == myNodeNum) setLocalModuleConfig(config) // Update our local copy
        }

        override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                getModuleConfigRequestValue = config
            })
        }

        override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket {
                setRingtoneMessage = ringtone
            })
        }

        override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                getRingtoneRequest = true
            })
        }

        override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket {
                setCannedMessageModuleMessages = messages
            })
        }

        override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                getCannedMessageModuleMessagesRequest = true
            })
        }

        override fun setChannel(payload: ByteArray?) = toRemoteExceptions {
            setRemoteChannel(generatePacketId(), myNodeNum, payload)
        }

        override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray?) = toRemoteExceptions {
            val channel = ChannelProtos.Channel.parseFrom(payload)
            sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setChannel = channel })
        }

        override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                getChannelRequest = index + 1
            })
        }

        override fun beginEditSettings() = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket {
                beginEditSettings = true
            })
        }

        override fun commitEditSettings() = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket {
                commitEditSettings = true
            })
        }

        override fun getChannelSet(): ByteArray = toRemoteExceptions {
            this@MeshService.channelSet.toByteArray()
        }

        override fun getNodes(): MutableList<NodeInfo> = toRemoteExceptions {
            val r = nodeDBbyNodeNum.values.map { it.toNodeInfo() }.toMutableList()
            info("in getOnline, count=${r.size}")
            // return arrayOf("+16508675309")
            r
        }

        override fun connectionState(): String = toRemoteExceptions {
            val r = this@MeshService.connectionState
            info("in connectionState=$r")
            r.toString()
        }

        override fun startProvideLocation() = toRemoteExceptions {
            startLocationRequests()
        }

        override fun stopProvideLocation() = toRemoteExceptions {
            stopLocationRequests()
        }

        override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
            nodeDBbyNodeNum.remove(nodeNum)
            sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket {
                removeByNodenum = nodeNum
            })
        }
        override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
            if (destNum != myNodeNum) {
                sendToRadio(newMeshPacketTo(destNum
                ).buildMeshPacket(
                    channel = nodeDBbyNodeNum[destNum]?.channel ?: 0
                ) {
                    portnumValue = Portnums.PortNum.NODEINFO_APP_VALUE
                    wantResponse = true
                    payload = nodeDBbyNodeNum[myNodeNum]!!.user.toByteString()
                })
            }
        }
        override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildMeshPacket(
                channel = nodeDBbyNodeNum[destNum]?.channel ?: 0,
                priority = MeshPacket.Priority.BACKGROUND,
            ) {
                portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                wantResponse = true
            })
        }

        override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
            val pos = position {
                latitudeI = Position.degI(position.latitude)
                longitudeI = Position.degI(position.longitude)
                altitude = position.altitude
            }
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket {
                if (position != Position(0.0, 0.0, 0)) {
                    setFixedPosition = pos
                } else {
                    removeFixedPosition = true
                }
            })
            updateNodeInfo(destNum) {
                it.setPosition(pos, currentSecond())
            }
        }

        override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildMeshPacket(
                wantAck = true,
                id = requestId,
                channel = nodeDBbyNodeNum[destNum]?.channel ?: 0,
            ) {
                portnumValue = Portnums.PortNum.TRACEROUTE_APP_VALUE
                wantResponse = true
            })
        }

        override fun requestShutdown(requestId: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) {
                shutdownSeconds = 5
            })
        }

        override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) {
                rebootSeconds = 5
            })
        }

        override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) {
                factoryResetDevice = 1
            })
        }

        override fun requestNodedbReset(requestId: Int, destNum: Int) = toRemoteExceptions {
            sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) {
                nodedbReset = 1
            })
        }
    }
}
