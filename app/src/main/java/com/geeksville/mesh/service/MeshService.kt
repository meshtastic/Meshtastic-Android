/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import androidx.core.location.LocationCompat
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import com.geeksville.mesh.repository.network.MQTTRepository
import com.geeksville.mesh.repository.radio.InterfaceId
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.util.ignoreException
import com.geeksville.mesh.util.toRemoteExceptions
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.meshtastic.core.strings.getString
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.data.repository.LocationRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.getFullTracerouteResponse
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.SERVICE_NOTIFY_ID
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connected_count
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.critical_alert
import org.meshtastic.core.strings.device_sleeping
import org.meshtastic.core.strings.disconnected
import org.meshtastic.core.strings.error_duty_cycle
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.core.strings.waypoint_received
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.AppOnlyProtos
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.DeviceUIProtos
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.LocalOnlyProtos.LocalModuleConfig
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.FromRadio.PayloadVariantCase
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.MeshProtos.ToRadio
import org.meshtastic.proto.ModuleConfigProtos
import org.meshtastic.proto.PaxcountProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.StoreAndForwardProtos
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.XmodemProtos
import org.meshtastic.proto.copy
import org.meshtastic.proto.fromRadio
import org.meshtastic.proto.position
import org.meshtastic.proto.telemetry
import org.meshtastic.proto.user
import timber.log.Timber
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * Handles all the communication with android apps. Also keeps an internal model of the network state.
 *
 * Note: this service will go away once all clients are unbound from it. Warning: do not override toString, it causes
 * infinite recursion on some androids (because contextWrapper.getResources calls to string
 */
@AndroidEntryPoint
class MeshService : Service() {
    @Inject lateinit var dispatchers: CoroutineDispatchers

    @Inject lateinit var packetRepository: Lazy<PacketRepository>

    @Inject lateinit var meshLogRepository: Lazy<MeshLogRepository>

    @Inject lateinit var radioInterfaceService: RadioInterfaceService

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var radioConfigRepository: RadioConfigRepository

    @Inject lateinit var serviceRepository: ServiceRepository

    @Inject lateinit var nodeRepository: NodeRepository

    @Inject lateinit var databaseManager: DatabaseManager

    @Inject lateinit var mqttRepository: MQTTRepository

    @Inject lateinit var serviceNotifications: MeshServiceNotifications

    @Inject lateinit var meshPrefs: MeshPrefs

    @Inject lateinit var uiPrefs: UiPrefs

    @Inject lateinit var connectionStateHolder: MeshServiceConnectionStateHolder

    @Inject lateinit var packetHandler: PacketHandler

    @Inject lateinit var serviceBroadcasts: MeshServiceBroadcasts

    @Inject lateinit var analytics: PlatformAnalytics

    private val tracerouteStartTimes = ConcurrentHashMap<Int, Long>()

    companion object {

        // Intents broadcast by MeshService

        private fun actionReceived(portNum: String) = "$prefix.RECEIVED.$portNum"

        // generate a RECEIVED action filter string that includes either the portnumber as an int,
        // or preferably a
        // symbolic name from portnums.proto
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
         * Talk to our running service and try to set a new device address. And then immediately call start on the
         * service to possibly promote our service to be a foreground service.
         */
        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        /**
         * The minimum firmware version we know how to talk to. We'll still be able to talk to 2.0 firmwares but only
         * well enough to ask them to firmware update.
         */
        val minDeviceVersion = DeviceVersion(BuildConfig.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(BuildConfig.ABS_MIN_FW_VERSION)

        // Two-stage config flow nonces to avoid stale BLE packets, mirroring Meshtastic-Apple
        private const val DEFAULT_CONFIG_ONLY_NONCE = 69420
        private const val DEFAULT_NODE_INFO_NONCE = 69421

        private const val WANT_CONFIG_DELAY = 100L
        private const val HISTORY_TAG = "HistoryReplay"
        private const val DEFAULT_HISTORY_RETURN_WINDOW_MINUTES = 60 * 24
        private const val DEFAULT_HISTORY_RETURN_MAX_MESSAGES = 100
        private const val MAX_EARLY_PACKET_BUFFER = 128

        @VisibleForTesting
        internal fun buildStoreForwardHistoryRequest(
            lastRequest: Int,
            historyReturnWindow: Int,
            historyReturnMax: Int,
        ): StoreAndForwardProtos.StoreAndForward {
            val historyBuilder = StoreAndForwardProtos.StoreAndForward.History.newBuilder()
            if (lastRequest > 0) historyBuilder.lastRequest = lastRequest
            if (historyReturnWindow > 0) historyBuilder.window = historyReturnWindow
            if (historyReturnMax > 0) historyBuilder.historyMessages = historyReturnMax
            return StoreAndForwardProtos.StoreAndForward.newBuilder()
                .setRr(StoreAndForwardProtos.StoreAndForward.RequestResponse.CLIENT_HISTORY)
                .setHistory(historyBuilder)
                .build()
        }

        @VisibleForTesting
        internal fun resolveHistoryRequestParameters(window: Int, max: Int): Pair<Int, Int> {
            val resolvedWindow = if (window > 0) window else DEFAULT_HISTORY_RETURN_WINDOW_MINUTES
            val resolvedMax = if (max > 0) max else DEFAULT_HISTORY_RETURN_MAX_MESSAGES
            return resolvedWindow to resolvedMax
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private inline fun historyLog(
        priority: Int = Log.INFO,
        throwable: Throwable? = null,
        crossinline message: () -> String,
    ) {
        if (!BuildConfig.DEBUG) return
        val timber = Timber.tag(HISTORY_TAG)
        val msg = message()
        if (throwable != null) {
            timber.log(priority, throwable, msg)
        } else {
            timber.log(priority, msg)
        }
    }

    private fun activeDeviceAddress(): String? =
        meshPrefs.deviceAddress?.takeIf { !it.equals(NO_DEVICE_SELECTED, ignoreCase = true) && it.isNotBlank() }

    private fun currentTransport(address: String? = meshPrefs.deviceAddress): String = when (address?.firstOrNull()) {
        InterfaceId.BLUETOOTH.id -> "BLE"
        InterfaceId.TCP.id -> "TCP"
        InterfaceId.SERIAL.id -> "Serial"
        InterfaceId.MOCK.id -> "Mock"
        InterfaceId.NOP.id -> "NOP"
        else -> "Unknown"
    }

    private var locationFlow: Job? = null
    private var mqttMessageFlow: Job? = null

    private val batteryPercentUnsupported = 0.0
    private val batteryPercentLowThreshold = 20
    private val batteryPercentLowDivisor = 5
    private val batteryPercentCriticalThreshold = 5
    private val batteryPercentCooldownSeconds = 1500
    private val batteryPercentCooldowns: HashMap<Int, Long> = HashMap()

    private fun getSenderName(packet: DataPacket?): String {
        val name = nodeDBbyID[packet?.from]?.user?.longName
        return name ?: getString(Res.string.unknown_username)
    }

    /** start our location requests (if they weren't already running) */
    private fun startLocationRequests() {
        // If we're already observing updates, don't register again
        if (locationFlow?.isActive == true) return

        @SuppressLint("MissingPermission")
        if (hasLocationPermission()) {
            locationFlow =
                locationRepository
                    .getLocations()
                    .onEach { location ->
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
                            },
                        )
                    }
                    .launchIn(serviceScope)
        }
    }

    private fun stopLocationRequests() {
        if (locationFlow?.isActive == true) {
            Timber.i("Stopping location requests")
            locationFlow?.cancel()
            locationFlow = null
        }
    }

    private fun showAlertNotification(contactKey: String, dataPacket: DataPacket) {
        serviceNotifications.showAlertNotification(
            contactKey,
            getSenderName(dataPacket),
            dataPacket.alert ?: getString(Res.string.critical_alert),
        )
    }

    private fun updateMessageNotification(contactKey: String, dataPacket: DataPacket) {
        val message: String =
            when (dataPacket.dataType) {
                Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> dataPacket.text!!
                Portnums.PortNum.WAYPOINT_APP_VALUE -> {
                    getString(Res.string.waypoint_received, dataPacket.waypoint!!.name)
                }

                else -> return
            }
        serviceNotifications.updateMessageNotification(
            contactKey,
            getSenderName(dataPacket),
            message,
            isBroadcast = dataPacket.to == DataPacket.ID_BROADCAST,
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("Creating mesh service")
        serviceNotifications.initChannels()
        // Switch to the IO thread
        serviceScope.handledLaunch { radioInterfaceService.connect() }
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(serviceScope)
        radioInterfaceService.receivedData
            .onStart {
                historyLog { "rxCollector START transport=${currentTransport()} scope=${serviceScope.hashCode()}" }
            }
            .onCompletion { cause ->
                historyLog(Log.WARN) {
                    "rxCollector STOP transport=${currentTransport()} cause=${cause?.message ?: "completed"}"
                }
            }
            .onEach(::onReceiveFromRadio)
            .launchIn(serviceScope)
        radioInterfaceService.connectionError
            .onEach { error -> Timber.e("BLE Connection Error: ${error.message}") }
            .launchIn(serviceScope)
        radioConfigRepository.localConfigFlow.onEach { localConfig = it }.launchIn(serviceScope)
        radioConfigRepository.moduleConfigFlow.onEach { moduleConfig = it }.launchIn(serviceScope)
        radioConfigRepository.channelSetFlow.onEach { channelSet = it }.launchIn(serviceScope)
        serviceRepository.serviceAction.onEach(::onServiceAction).launchIn(serviceScope)
        nodeRepository.myNodeInfo
            .onEach { myNodeInfo = it }
            .flatMapLatest { myNodeEntity ->
                // When myNodeInfo changes, set up emissions for the "provide-location-nodeNum" pref.
                if (myNodeEntity == null) {
                    flowOf(false)
                } else {
                    uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                }
            }
            .onEach { shouldProvideNodeLocation ->
                if (shouldProvideNodeLocation) {
                    startLocationRequests()
                } else {
                    stopLocationRequests()
                }
            }
            .launchIn(serviceScope)

        loadCachedNodeDB() // Load our last known node DB

        // the rest of our init will happen once we are in radioConnection.onServiceConnected
    }

    /** If someone binds to us, this will be called after on create */
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Called when the service is started or restarted. This method manages the foreground state of the service.
     *
     * It attempts to start the service in the foreground with a notification. If `startForeground` fails, for example,
     * due to a `SecurityException` on Android 13+ because the `POST_NOTIFICATIONS` permission is missing, it logs an
     * error* and returns `START_NOT_STICKY` to prevent the service from becoming sticky in a broken state.
     *
     * If the service is not intended to be in the foreground (e.g., no device is connected), it stops the foreground
     * state and returns `START_NOT_STICKY`. Otherwise, it returns `START_STICKY`.
     *
     * @param intent The Intent supplied to `startService(Intent)`, as modified by the system.
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to start.
     * @return The return value indicates what semantics the system should use for the service's current started state.
     *   See [Service.onStartCommand] for details.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = radioInterfaceService.getBondedDeviceAddress()
        val wantForeground = a != null && a != NO_DEVICE_SELECTED

        Timber.i("Requesting foreground service=$wantForeground")
        val notification = updateServiceStatusNotification()

        try {
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFY_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (hasLocationPermission()) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    }
                } else {
                    0 // No specific type needed for older Android versions
                },
            )
        } catch (ex: Exception) {
            Timber.e(ex, "Error starting foreground service")
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
        Timber.i("Destroying mesh service")

        // Make sure we aren't using the notification first
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        super.onDestroy()
        serviceJob.cancel()
    }

    //
    // BEGINNING OF MODEL - FIXME, move elsewhere
    //

    private fun loadCachedNodeDB() =
        serviceScope.handledLaunch { nodeDBbyNodeNum.putAll(nodeRepository.getNodeDBbyNum().first()) }

    /** discard entire node db & message state - used when downloading a new db from the device */
    private fun discardNodeDB() {
        Timber.d("Discarding NodeDB")
        myNodeInfo = null
        nodeDBbyNodeNum.clear()
        isNodeDbReady = false
        allowNodeDbWrites = false
        earlyReceivedPackets.clear()
    }

    private var myNodeInfo: MyNodeEntity? = null

    private val configTotal by lazy { ConfigProtos.Config.getDescriptor().fields.size }
    private val moduleTotal by lazy { ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size }
    private var sessionPasskey: ByteString = ByteString.EMPTY

    private var localConfig: LocalConfig = LocalConfig.getDefaultInstance()
    private var moduleConfig: LocalModuleConfig = LocalModuleConfig.getDefaultInstance()
    private var channelSet: AppOnlyProtos.ChannelSet = AppOnlyProtos.ChannelSet.getDefaultInstance()

    // True after we've done our initial node db init, signaling we can process packets immediately
    @Volatile private var isNodeDbReady = false

    // True when we are allowed to write node updates to the persistent database
    @Volatile private var allowNodeDbWrites = false

    // The database of active nodes, index is the node number
    private val nodeDBbyNodeNum = ConcurrentHashMap<Int, NodeEntity>()

    // The database of active nodes, index is the node user ID string
    // NOTE: some NodeInfos might be in only nodeDBbyNodeNum (because we don't yet know an ID).
    private val nodeDBbyID
        get() = nodeDBbyNodeNum.mapKeys { it.value.user.id }

    //
    // END OF MODEL
    //

    @Suppress("UnusedPrivateMember")
    private val deviceVersion
        get() = DeviceVersion(myNodeInfo?.firmwareVersion ?: "")

    @Suppress("UnusedPrivateMember")
    private val appVersion
        get() = BuildConfig.VERSION_CODE

    private val minAppVersion
        get() = myNodeInfo?.minAppVersion ?: 0

    // Map a nodenum to a node, or throw an exception if not found
    private fun toNodeInfo(n: Int) = nodeDBbyNodeNum[n] ?: throw NodeNumNotFoundException(n)

    /**
     * Map a nodeNum to the nodeId string If we have a NodeInfo for this ID we prefer to return the string ID inside the
     * user record. but some nodes might not have a user record at all (because not yet received), in that case, we
     * return a hex version of the ID just based on the number
     */
    private fun toNodeID(n: Int): String = if (n == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        nodeDBbyNodeNum[n]?.user?.id ?: DataPacket.nodeNumToDefaultId(n)
    }

    // given a nodeNum, return a db entry - creating if necessary
    private fun getOrCreateNodeInfo(n: Int, channel: Int = 0) = nodeDBbyNodeNum.getOrPut(n) {
        val userId = DataPacket.nodeNumToDefaultId(n)
        val defaultUser = user {
            id = userId
            longName = "Meshtastic ${userId.takeLast(n = 4)}"
            shortName = userId.takeLast(n = 4)
            hwModel = MeshProtos.HardwareModel.UNSET
        }

        NodeEntity(num = n, user = defaultUser, longName = defaultUser.longName, channel = channel)
    }

    private val hexIdRegex = """!([0-9A-Fa-f]+)""".toRegex()

    // Map a userid to a node/ node num, or throw an exception if not found
    // We prefer to find nodes based on their assigned IDs, but if no ID has been assigned to a
    // node, we can also find
    // it based on node number
    private fun toNodeInfo(id: String): NodeEntity {
        // If this is a valid hexaddr will be !null
        val hexStr = hexIdRegex.matchEntire(id)?.groups?.get(1)?.value

        return nodeDBbyID[id]
            ?: when {
                id == DataPacket.ID_LOCAL -> toNodeInfo(myNodeNum)
                hexStr != null -> {
                    val n = hexStr.toLong(16).toInt()
                    nodeDBbyNodeNum[n] ?: throw IdNotFoundException(id)
                }

                else -> throw InvalidNodeIdException(id)
            }
    }

    private fun getUserName(num: Int): String = with(nodeRepository.getUser(num)) { "$longName ($shortName)" }

    private val numNodes
        get() = nodeDBbyNodeNum.size

    /** How many nodes are currently online (including our local node) */
    private val numOnlineNodes
        get() = nodeDBbyNodeNum.values.count { it.isOnline }

    private fun toNodeNum(id: String): Int = when (id) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        DataPacket.ID_LOCAL -> myNodeNum
        else -> toNodeInfo(id).num
    }

    // A helper function that makes it easy to update node info objects
    private inline fun updateNodeInfo(
        nodeNum: Int,
        withBroadcast: Boolean = true,
        channel: Int = 0,
        crossinline updateFn: (NodeEntity) -> Unit,
    ) {
        val info = getOrCreateNodeInfo(nodeNum, channel)
        updateFn(info)

        if (info.user.id.isNotEmpty() && isNodeDbReady) {
            serviceScope.handledLaunch { nodeRepository.upsert(info) }
        }

        if (withBroadcast) {
            serviceBroadcasts.broadcastNodeChange(info.toNodeInfo())
        }
    }

    // My node num
    private val myNodeNum
        get() = myNodeInfo?.myNodeNum ?: throw RadioNotConnectedException("We don't yet have our myNodeInfo")

    // My node ID string
    private val myNodeID
        get() = toNodeID(myNodeNum)

    // Admin channel index
    private val MeshPacket.Builder.adminChannelIndex: Int
        get() =
            when {
                myNodeNum == to -> 0
                nodeDBbyNodeNum[myNodeNum]?.hasPKC == true && nodeDBbyNodeNum[to]?.hasPKC == true ->
                    DataPacket.PKC_CHANNEL_INDEX

                else ->
                    channelSet.settingsList.indexOfFirst { it.name.equals("admin", ignoreCase = true) }.coerceAtLeast(0)
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

    /** Helper to make it easy to build a subpacket in the proper protobufs */
    private fun MeshPacket.Builder.buildMeshPacket(
        wantAck: Boolean = false,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        hopLimit: Int = localConfig.lora.hopLimit,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        initFn: MeshProtos.Data.Builder.() -> Unit,
    ): MeshPacket {
        this.wantAck = wantAck
        this.id = id
        this.hopLimit = hopLimit
        this.priority = priority
        decoded = MeshProtos.Data.newBuilder().also { initFn(it) }.build()
        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            pkiEncrypted = true
            nodeDBbyNodeNum[to]?.user?.publicKey?.let { publicKey -> this.publicKey = publicKey }
        } else {
            this.channel = channel
        }

        return build()
    }

    /** Helper to make it easy to build a subpacket in the proper protobufs */
    private fun MeshPacket.Builder.buildAdminPacket(
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        wantResponse: Boolean = false,
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit,
    ): MeshPacket =
        buildMeshPacket(id = id, wantAck = true, channel = adminChannelIndex, priority = MeshPacket.Priority.RELIABLE) {
            this.wantResponse = wantResponse
            portnumValue = Portnums.PortNum.ADMIN_APP_VALUE
            payload =
                AdminProtos.AdminMessage.newBuilder()
                    .also {
                        initFn(it)
                        it.sessionPasskey = sessionPasskey
                    }
                    .build()
                    .toByteString()
        }

    // Generate a DataPacket from a MeshPacket, or null if we didn't have enough data to do so
    private fun toDataPacket(packet: MeshPacket): DataPacket? = if (!packet.hasDecoded()) {
        // We never convert packets that are not DataPackets
        null
    } else {
        val data = packet.decoded

        DataPacket(
            from = toNodeID(packet.from),
            to = toNodeID(packet.to),
            time = packet.rxTime * 1000L,
            id = packet.id,
            dataType = data.portnumValue,
            bytes = data.payload.toByteArray(),
            hopLimit = packet.hopLimit,
            channel = if (packet.pkiEncrypted) DataPacket.PKC_CHANNEL_INDEX else packet.channel,
            wantAck = packet.wantAck,
            hopStart = packet.hopStart,
            snr = packet.rxSnr,
            rssi = packet.rxRssi,
            replyId = data.replyId,
            relayNode = packet.relayNode,
            viaMqtt = packet.viaMqtt,
        )
    }

    private fun toMeshPacket(p: DataPacket): MeshPacket = newMeshPacketTo(p.to!!).buildMeshPacket(
        id = p.id,
        wantAck = p.wantAck,
        hopLimit = p.hopLimit,
        channel = p.channel,
    ) {
        portnumValue = p.dataType
        payload = ByteString.copyFrom(p.bytes)
        if (p.replyId != null && p.replyId != 0) {
            this.replyId = p.replyId!!
        }
    }

    private val rememberDataType =
        setOf(
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            Portnums.PortNum.ALERT_APP_VALUE,
            Portnums.PortNum.WAYPOINT_APP_VALUE,
        )

    private fun rememberReaction(packet: MeshPacket) = serviceScope.handledLaunch {
        val reaction =
            ReactionEntity(
                replyId = packet.decoded.replyId,
                userId = toNodeID(packet.from),
                emoji = packet.decoded.payload.toByteArray().decodeToString(),
                timestamp = System.currentTimeMillis(),
            )
        packetRepository.get().insertReaction(reaction)
    }

    private fun rememberDataPacket(dataPacket: DataPacket, updateNotification: Boolean = true) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal = dataPacket.from == DataPacket.ID_LOCAL
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from

        // contactKey: unique contact key filter (channel)+(nodeId)
        val contactKey = "${dataPacket.channel}$contactId"

        val packetToSave =
            Packet(
                uuid = 0L, // autoGenerated
                myNodeNum = myNodeNum,
                packetId = dataPacket.id,
                port_num = dataPacket.dataType,
                contact_key = contactKey,
                received_time = System.currentTimeMillis(),
                read = fromLocal,
                data = dataPacket,
                snr = dataPacket.snr,
                rssi = dataPacket.rssi,
                hopsAway = dataPacket.hopsAway,
                replyId = dataPacket.replyId ?: 0,
            )
        serviceScope.handledLaunch {
            packetRepository.get().apply {
                insert(packetToSave)
                val isMuted = getContactSettings(contactKey).isMuted
                if (packetToSave.port_num == Portnums.PortNum.ALERT_APP_VALUE && !isMuted) {
                    showAlertNotification(contactKey, dataPacket)
                } else if (updateNotification && !isMuted) {
                    updateMessageNotification(contactKey, dataPacket)
                }
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

                Timber.d("Received data from $fromId, portnum=${data.portnum} ${bytes.size} bytes")

                dataPacket.status = MessageStatus.RECEIVED

                // if (p.hasUser()) handleReceivedUser(fromNum, p.user)

                // We tell other apps about most message types, but some may have sensitive data, so
                // that is not shared'
                var shouldBroadcast = !fromUs

                when (data.portnumValue) {
                    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> {
                        if (data.replyId != 0 && data.emoji == 0) {
                            Timber.d("Received REPLY from $fromId")
                            rememberDataPacket(dataPacket)
                        } else if (data.replyId != 0 && data.emoji != 0) {
                            Timber.d("Received EMOJI from $fromId")
                            rememberReaction(packet)
                        } else {
                            Timber.d("Received CLEAR_TEXT from $fromId")
                            rememberDataPacket(dataPacket)
                        }
                    }

                    Portnums.PortNum.ALERT_APP_VALUE -> {
                        Timber.d("Received ALERT_APP from $fromId")
                        rememberDataPacket(dataPacket)
                    }

                    Portnums.PortNum.WAYPOINT_APP_VALUE -> {
                        Timber.d("Received WAYPOINT_APP from $fromId")
                        val u = MeshProtos.Waypoint.parseFrom(data.payload)
                        // Validate locked Waypoints from the original sender
                        if (u.lockedTo != 0 && u.lockedTo != packet.from) return
                        rememberDataPacket(dataPacket, u.expire > currentSecond())
                    }

                    Portnums.PortNum.POSITION_APP_VALUE -> {
                        Timber.d("Received POSITION_APP from $fromId")
                        val u = MeshProtos.Position.parseFrom(data.payload)
                        if (data.wantResponse && u.latitudeI == 0 && u.longitudeI == 0) {
                            Timber.d("Ignoring nop position update from position request")
                        } else {
                            handleReceivedPosition(packet.from, u, dataPacket.time)
                        }
                    }

                    Portnums.PortNum.NODEINFO_APP_VALUE ->
                        if (!fromUs) {
                            Timber.d("Received NODEINFO_APP from $fromId")
                            val u =
                                MeshProtos.User.parseFrom(data.payload).copy {
                                    if (isLicensed) clearPublicKey()
                                    if (packet.viaMqtt) longName = "$longName (MQTT)"
                                }
                            handleReceivedUser(packet.from, u, packet.channel)
                        }

                    // Handle new telemetry info
                    Portnums.PortNum.TELEMETRY_APP_VALUE -> {
                        Timber.d("Received TELEMETRY_APP from $fromId")
                        val u =
                            TelemetryProtos.Telemetry.parseFrom(data.payload).copy {
                                if (time == 0) time = (dataPacket.time / 1000L).toInt()
                            }
                        handleReceivedTelemetry(packet.from, u)
                    }

                    Portnums.PortNum.ROUTING_APP_VALUE -> {
                        Timber.d("Received ROUTING_APP from $fromId")
                        // We always send ACKs to other apps, because they might care about the
                        // messages they sent
                        shouldBroadcast = true
                        val u = MeshProtos.Routing.parseFrom(data.payload)

                        if (u.errorReason == MeshProtos.Routing.Error.DUTY_CYCLE_LIMIT) {
                            serviceRepository.setErrorMessage(getString(Res.string.error_duty_cycle))
                        }

                        handleAckNak(data.requestId, fromId, u.errorReasonValue, dataPacket.relayNode)
                        packetHandler.removeResponse(data.requestId, complete = true)
                    }

                    Portnums.PortNum.ADMIN_APP_VALUE -> {
                        Timber.d("Received ADMIN_APP from $fromId")
                        val u = AdminProtos.AdminMessage.parseFrom(data.payload)
                        handleReceivedAdmin(packet.from, u)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.PAXCOUNTER_APP_VALUE -> {
                        Timber.d("Received PAXCOUNTER_APP from $fromId")
                        val p = PaxcountProtos.Paxcount.parseFrom(data.payload)
                        handleReceivedPaxcounter(packet.from, p)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.STORE_FORWARD_APP_VALUE -> {
                        Timber.d("Received STORE_FORWARD_APP from $fromId")
                        val u = StoreAndForwardProtos.StoreAndForward.parseFrom(data.payload)
                        handleReceivedStoreAndForward(dataPacket, u)
                        shouldBroadcast = false
                    }

                    Portnums.PortNum.RANGE_TEST_APP_VALUE -> {
                        Timber.d("Received RANGE_TEST_APP from $fromId")
                        if (!moduleConfig.rangeTest.enabled) return
                        val u = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                        rememberDataPacket(u)
                    }

                    Portnums.PortNum.DETECTION_SENSOR_APP_VALUE -> {
                        Timber.d("Received DETECTION_SENSOR_APP from $fromId")
                        val u = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                        rememberDataPacket(u)
                    }

                    Portnums.PortNum.TRACEROUTE_APP_VALUE -> {
                        Timber.d("Received TRACEROUTE_APP from $fromId")
                        val full = packet.getFullTracerouteResponse(::getUserName)
                        if (full != null) {
                            val requestId = packet.decoded.requestId
                            val start = tracerouteStartTimes.remove(requestId)
                            val response =
                                if (start != null) {
                                    val elapsedMs = System.currentTimeMillis() - start
                                    val seconds = elapsedMs / 1000.0
                                    Timber.i("Traceroute $requestId complete in $seconds s")
                                    "$full\n\nDuration: ${"%.1f".format(seconds)} s"
                                } else {
                                    full
                                }
                            serviceRepository.setTracerouteResponse(response)
                        }
                    }

                    else -> Timber.d("No custom processing needed for ${data.portnumValue} from $fromId")
                }

                // We always tell other apps when new data packets arrive
                if (shouldBroadcast) {
                    serviceBroadcasts.broadcastReceivedData(dataPacket)
                }

                analytics.track("num_data_receive", DataPair("num_data_receive", 1))

                analytics.track("data_receive", DataPair("num_bytes", bytes.size), DataPair("type", data.portnumValue))
            }
        }
    }

    private fun handleReceivedAdmin(fromNodeNum: Int, a: AdminProtos.AdminMessage) {
        when (a.payloadVariantCase) {
            AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                if (fromNodeNum == myNodeNum) {
                    val response = a.getConfigResponse
                    Timber.d("Admin: received config ${response.payloadVariantCase}")
                    setLocalConfig(response)
                }
            }

            AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                if (fromNodeNum == myNodeNum) {
                    val mi = myNodeInfo
                    if (mi != null) {
                        val ch = a.getChannelResponse
                        Timber.d("Admin: Received channel ${ch.index}")

                        if (ch.index + 1 < mi.maxChannels) {
                            handleChannel(ch)
                        }
                    }
                }
            }

            AdminProtos.AdminMessage.PayloadVariantCase.GET_DEVICE_METADATA_RESPONSE -> {
                Timber.d("Admin: received DeviceMetadata from $fromNodeNum")
                serviceScope.handledLaunch {
                    nodeRepository.insertMetadata(MetadataEntity(fromNodeNum, a.getDeviceMetadataResponse))
                }
            }

            else -> Timber.w("No special processing needed for ${a.payloadVariantCase}")
        }
        Timber.d("Admin: Received session_passkey from $fromNodeNum")
        sessionPasskey = a.sessionPasskey
    }

    /**
     * Check if a User is a default/placeholder from firmware (node was evicted and re-created) and whether we should
     * preserve existing user data instead of overwriting it.
     */
    private fun shouldPreserveExistingUser(existing: MeshProtos.User, incoming: MeshProtos.User): Boolean {
        val isDefaultName = incoming.longName.matches(Regex("^Meshtastic [0-9a-fA-F]{4}$"))
        val isDefaultHwModel = incoming.hwModel == MeshProtos.HardwareModel.UNSET
        val hasExistingUser = existing.id.isNotEmpty() && existing.hwModel != MeshProtos.HardwareModel.UNSET
        return hasExistingUser && isDefaultName && isDefaultHwModel
    }

    private fun handleSharedContactImport(contact: AdminProtos.SharedContact) {
        handleReceivedUser(contact.nodeNum, contact.user, manuallyVerified = true)
    }

    // Update our DB of users based on someone sending out a User subpacket
    private fun handleReceivedUser(
        fromNum: Int,
        p: MeshProtos.User,
        channel: Int = 0,
        manuallyVerified: Boolean = false,
    ) {
        updateNodeInfo(fromNum) {
            val newNode = (it.isUnknownUser && p.hwModel != MeshProtos.HardwareModel.UNSET)

            // Check if this is a default/unknown user from firmware (node was evicted and re-created)
            val shouldPreserve = shouldPreserveExistingUser(it.user, p)

            if (shouldPreserve) {
                // Firmware sent us a placeholder - keep all our existing user data
                Timber.d(
                    "Preserving existing user data for node $fromNum: " +
                        "kept='${it.user.longName}' (hwModel=${it.user.hwModel}), " +
                        "skipped default='${p.longName}' (hwModel=UNSET)",
                )
                // Still update channel and verification status
                it.channel = channel
                it.manuallyVerified = manuallyVerified
            } else {
                val keyMatch = !it.hasPKC || it.user.publicKey == p.publicKey
                it.user =
                    if (keyMatch) {
                        p
                    } else {
                        p.copy {
                            Timber.w("Public key mismatch from $longName ($shortName)")
                            publicKey = NodeEntity.ERROR_BYTE_STRING
                        }
                    }
                it.longName = p.longName
                it.shortName = p.shortName
                it.channel = channel
                it.manuallyVerified = manuallyVerified
                if (newNode) {
                    serviceNotifications.showNewNodeSeenNotification(it)
                }
            }
        }
    }

    /**
     * Update our DB of users based on someone sending out a Position subpacket
     *
     * @param defaultTime in msecs since 1970
     */
    private fun handleReceivedPosition(
        fromNum: Int,
        p: MeshProtos.Position,
        defaultTime: Long = System.currentTimeMillis(),
    ) {
        // Nodes periodically send out position updates, but those updates might not contain a lat &
        // lon (because no GPS
        // lock)
        // We like to look at the local node to see if it has been sending out valid lat/lon, so for
        // the LOCAL node
        // (only)
        // we don't record these nop position updates
        if (myNodeNum == fromNum && p.latitudeI == 0 && p.longitudeI == 0) {
            Timber.d("Ignoring nop position update for the local node")
        } else {
            updateNodeInfo(fromNum) { it.setPosition(p, (defaultTime / 1000L).toInt()) }
        }
    }

    // Update our DB of users based on someone sending out a Telemetry subpacket
    private fun handleReceivedTelemetry(fromNum: Int, telemetry: TelemetryProtos.Telemetry) {
        val isRemote = (fromNum != myNodeNum)
        if (!isRemote) {
            updateServiceStatusNotification(telemetry = telemetry)
        }
        updateNodeInfo(fromNum) { nodeEntity ->
            when {
                telemetry.hasDeviceMetrics() -> {
                    nodeEntity.deviceTelemetry = telemetry
                    if (fromNum == myNodeNum || (isRemote && nodeEntity.isFavorite)) {
                        if (
                            telemetry.deviceMetrics.voltage > batteryPercentUnsupported &&
                            telemetry.deviceMetrics.batteryLevel <= batteryPercentLowThreshold
                        ) {
                            if (shouldBatteryNotificationShow(fromNum, telemetry)) {
                                serviceNotifications.showOrUpdateLowBatteryNotification(nodeEntity, isRemote)
                            }
                        } else {
                            if (batteryPercentCooldowns.containsKey(fromNum)) {
                                batteryPercentCooldowns.remove(fromNum)
                            }
                            serviceNotifications.cancelLowBatteryNotification(nodeEntity)
                        }
                    }
                }

                telemetry.hasEnvironmentMetrics() -> nodeEntity.environmentTelemetry = telemetry
                telemetry.hasPowerMetrics() -> nodeEntity.powerTelemetry = telemetry
            }
        }
    }

    private fun shouldBatteryNotificationShow(fromNum: Int, t: TelemetryProtos.Telemetry): Boolean {
        val isRemote = (fromNum != myNodeNum)
        var shouldDisplay = false
        var forceDisplay = false
        when {
            t.deviceMetrics.batteryLevel <= batteryPercentCriticalThreshold -> {
                shouldDisplay = true
                forceDisplay = true
            }

            t.deviceMetrics.batteryLevel == batteryPercentLowThreshold -> shouldDisplay = true
            t.deviceMetrics.batteryLevel.mod(batteryPercentLowDivisor) == 0 && !isRemote -> shouldDisplay = true

            isRemote -> shouldDisplay = true
        }
        if (shouldDisplay) {
            val now = System.currentTimeMillis() / 1000
            if (!batteryPercentCooldowns.containsKey(fromNum)) batteryPercentCooldowns[fromNum] = 0
            if ((now - batteryPercentCooldowns[fromNum]!!) >= batteryPercentCooldownSeconds || forceDisplay) {
                batteryPercentCooldowns[fromNum] = now
                return true
            }
        }
        return false
    }

    private fun handleReceivedPaxcounter(fromNum: Int, p: PaxcountProtos.Paxcount) {
        updateNodeInfo(fromNum) { it.paxcounter = p }
    }

    /**
     * Ask the connected radio to replay any packets it buffered while the client was offline.
     *
     * Radios deliver history via the Store & Forward protocol regardless of transport, so we piggyback on that
     * mechanism after BLE/Wi‑Fi reconnects.
     */
    private fun requestHistoryReplay(trigger: String) {
        val address = activeDeviceAddress()
        val failure =
            when {
                address == null -> "no_active_address"
                myNodeNum == null -> "no_my_node"
                else -> null
            }
        if (failure != null) {
            historyLog { "requestHistory skipped trigger=$trigger reason=$failure" }
            return
        }

        val safeAddress = address!!
        val myNum = myNodeNum!!
        val storeForwardConfig = moduleConfig.storeForward
        val lastRequest = meshPrefs.getStoreForwardLastRequest(safeAddress)
        val (window, max) =
            resolveHistoryRequestParameters(storeForwardConfig.historyReturnWindow, storeForwardConfig.historyReturnMax)
        val windowSource = if (storeForwardConfig.historyReturnWindow > 0) "config" else "default"
        val maxSource = if (storeForwardConfig.historyReturnMax > 0) "config" else "default"
        val sourceSummary = "window=$window($windowSource) max=$max($maxSource)"
        val request =
            buildStoreForwardHistoryRequest(
                lastRequest = lastRequest,
                historyReturnWindow = window,
                historyReturnMax = max,
            )
        val logContext = "trigger=$trigger transport=${currentTransport(safeAddress)} addr=$safeAddress"
        historyLog { "requestHistory $logContext lastRequest=$lastRequest $sourceSummary" }

        runCatching {
            packetHandler.sendToRadio(
                newMeshPacketTo(myNum).buildMeshPacket(priority = MeshPacket.Priority.BACKGROUND) {
                    portnumValue = Portnums.PortNum.STORE_FORWARD_APP_VALUE
                    payload = ByteString.copyFrom(request.toByteArray())
                },
            )
        }
            .onFailure { ex -> historyLog(Log.WARN, ex) { "requestHistory failed $logContext" } }
    }

    private fun updateStoreForwardLastRequest(source: String, lastRequest: Int) {
        if (lastRequest <= 0) return
        val address = activeDeviceAddress() ?: return
        val current = meshPrefs.getStoreForwardLastRequest(address)
        val transport = currentTransport(address)
        val logContext = "source=$source transport=$transport address=$address"
        if (lastRequest != current) {
            meshPrefs.setStoreForwardLastRequest(address, lastRequest)
            historyLog { "historyMarker updated $logContext from=$current to=$lastRequest" }
        } else {
            historyLog(Log.DEBUG) { "historyMarker unchanged $logContext value=$lastRequest" }
        }
    }

    private fun handleReceivedStoreAndForward(dataPacket: DataPacket, s: StoreAndForwardProtos.StoreAndForward) {
        Timber.d("StoreAndForward: ${s.variantCase} ${s.rr} from ${dataPacket.from}")
        val transport = currentTransport()
        val lastRequest =
            if (s.variantCase == StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY) {
                s.history.lastRequest
            } else {
                0
            }
        val baseContext = "transport=$transport from=${dataPacket.from}"
        historyLog { "rxStoreForward $baseContext variant=${s.variantCase} rr=${s.rr} lastRequest=$lastRequest" }
        when (s.variantCase) {
            StoreAndForwardProtos.StoreAndForward.VariantCase.STATS -> {
                val u =
                    dataPacket.copy(
                        bytes = s.stats.toString().encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(u)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY -> {
                val history = s.history
                val historySummary =
                    "routerHistory $baseContext messages=${history.historyMessages} " +
                        "window=${history.window} lastRequest=${history.lastRequest}"
                historyLog(Log.DEBUG) { historySummary }
                val text =
                    """
                    Total messages: ${s.history.historyMessages}
                    History window: ${s.history.window / 60000} min
                    Last request: ${s.history.lastRequest}
                """
                        .trimIndent()
                val u =
                    dataPacket.copy(
                        bytes = text.encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(u)
                updateStoreForwardLastRequest("router_history", s.history.lastRequest)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.TEXT -> {
                if (s.rr == StoreAndForwardProtos.StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                    dataPacket.to = DataPacket.ID_BROADCAST
                }
                val textLog =
                    "rxText $baseContext id=${dataPacket.id} ts=${dataPacket.time} " +
                        "to=${dataPacket.to} decision=remember"
                historyLog(Log.DEBUG) { textLog }
                val u =
                    dataPacket.copy(bytes = s.text.toByteArray(), dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                rememberDataPacket(u)
            }

            else -> {}
        }
    }

    private val earlyReceivedPackets = ArrayDeque<MeshPacket>()

    // If apps try to send packets when our radio is sleeping, we queue them here instead
    private val offlineSentPackets = mutableListOf<DataPacket>()

    // Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        val preparedPacket =
            packet
                .toBuilder()
                .apply {
                    // If the rxTime was not set by the device, update with current time
                    if (packet.rxTime == 0) setRxTime(currentSecond())
                }
                .build()
        Timber.d("[packet]: ${packet.toOneLineString()}")
        if (isNodeDbReady) {
            processReceivedMeshPacket(preparedPacket)
            return
        }

        val queueSize = earlyReceivedPackets.size
        if (queueSize >= MAX_EARLY_PACKET_BUFFER) {
            val dropped = earlyReceivedPackets.removeFirst()
            historyLog(Log.WARN) {
                val portLabel =
                    if (dropped.hasDecoded()) {
                        Portnums.PortNum.forNumber(dropped.decoded.portnumValue)?.name
                            ?: dropped.decoded.portnumValue.toString()
                    } else {
                        "unknown"
                    }
                "dropEarlyPacket bufferFull size=$queueSize id=${dropped.id} port=$portLabel"
            }
        }

        earlyReceivedPackets.addLast(preparedPacket)
        val portLabel =
            if (preparedPacket.hasDecoded()) {
                Portnums.PortNum.forNumber(preparedPacket.decoded.portnumValue)?.name
                    ?: preparedPacket.decoded.portnumValue.toString()
            } else {
                "unknown"
            }
        historyLog { "queueEarlyPacket size=${earlyReceivedPackets.size} id=${preparedPacket.id} port=$portLabel" }
    }

    private fun flushEarlyReceivedPackets(reason: String) {
        if (earlyReceivedPackets.isEmpty()) return
        val packets = earlyReceivedPackets.toList()
        earlyReceivedPackets.clear()
        historyLog { "replayEarlyPackets reason=$reason count=${packets.size}" }
        packets.forEach(::processReceivedMeshPacket)
    }

    private fun sendNow(p: DataPacket) {
        val packet = toMeshPacket(p)
        p.time = System.currentTimeMillis() // update time to the actual time we started sending
        // Timber.d("Sending to radio: ${packet.toPIIString()}")
        packetHandler.sendToRadio(packet)
    }

    private fun processQueuedPackets() {
        val sentPackets = mutableListOf<DataPacket>()
        offlineSentPackets.forEach { p ->
            try {
                sendNow(p)
                sentPackets.add(p)
            } catch (ex: Exception) {
                Timber.e(ex, "Error sending queued message:")
            }
        }
        offlineSentPackets.removeAll(sentPackets)
    }

    /** Handle an ack/nak packet by updating sent message status */
    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int, relayNode: Int? = null) {
        serviceScope.handledLaunch {
            val isAck = routingError == MeshProtos.Routing.Error.NONE_VALUE
            val p = packetRepository.get().getPacketById(requestId)
            // distinguish real ACKs coming from the intended receiver
            val m =
                when {
                    isAck && fromId == p?.data?.to -> MessageStatus.RECEIVED
                    isAck -> MessageStatus.DELIVERED
                    else -> MessageStatus.ERROR
                }
            if (p != null && p.data.status != MessageStatus.RECEIVED) {
                p.data.status = m
                p.routingError = routingError
                p.data.relayNode = relayNode
                if (isAck) {
                    p.data.relays += 1
                }
                packetRepository.get().update(p)
            }
            serviceBroadcasts.broadcastMessageStatus(requestId, m)
        }
    }

    // Update our model and resend as needed for a MeshPacket we just received from the radio
    private fun processReceivedMeshPacket(packet: MeshPacket) {
        val fromNum = packet.from
        if (packet.hasDecoded()) {
            val packetToSave =
                MeshLog(
                    uuid = UUID.randomUUID().toString(),
                    message_type = "Packet",
                    received_date = System.currentTimeMillis(),
                    raw_message = packet.toString(),
                    fromNum = packet.from,
                    portNum = packet.decoded.portnumValue,
                    fromRadio = fromRadio { this.packet = packet },
                )
            insertMeshLog(packetToSave)

            serviceScope.handledLaunch { serviceRepository.emitMeshPacket(packet) }

            // Update last seen for the node that sent the packet, but also for _our node_ because
            // anytime a packet
            // passes
            // through our node on the way to the phone that means that local node is also alive in
            // the mesh

            val isOtherNode = myNodeNum != fromNum
            updateNodeInfo(myNodeNum, withBroadcast = isOtherNode) { it.lastHeard = currentSecond() }

            // Do not generate redundant broadcasts of node change for this bookkeeping
            // updateNodeInfo call
            // because apps really only care about important updates of node state - which
            // handledReceivedData will give
            // them
            updateNodeInfo(fromNum, withBroadcast = false, channel = packet.channel) {
                // Update our last seen based on any valid timestamps.  If the device didn't provide
                // a timestamp make
                // one
                it.lastHeard = packet.rxTime
                it.snr = packet.rxSnr
                it.rssi = packet.rxRssi

                // Generate our own hopsAway, comparing hopStart to hopLimit.
                it.hopsAway =
                    if (packet.decoded.portnumValue == Portnums.PortNum.RANGE_TEST_APP_VALUE) {
                        0 // These don't come with the .hop params, but do not propogate, so they must be 0
                    } else if (packet.hopStart == 0 || packet.hopLimit > packet.hopStart) {
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
            // info("insert: ${packetToSave.message_type} =
            // ${packetToSave.raw_message.toOneLineString()}")
            meshLogRepository.get().insert(packetToSave)
        }
    }

    private fun setLocalConfig(config: ConfigProtos.Config) {
        serviceScope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
    }

    private fun setLocalModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        serviceScope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
    }

    private fun updateChannelSettings(ch: ChannelProtos.Channel) =
        serviceScope.handledLaunch { radioConfigRepository.updateChannelSettings(ch) }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()

    /** Send in analytics about mesh connection */
    private fun reportConnection() {
        val radioModel = DataPair("radio_model", myNodeInfo?.model ?: "unknown")
        analytics.track(
            "mesh_connect",
            DataPair("num_nodes", numNodes),
            DataPair("num_online", numOnlineNodes),
            radioModel,
        )
    }

    private var sleepTimeout: Job? = null

    // msecs since 1970 we started this connection
    private var connectTimeMsec = 0L

    // Called when we gain/lose connection to our radio
    @Suppress("CyclomaticComplexMethod")
    private fun onConnectionChanged(c: ConnectionState) {
        if (connectionStateHolder.connectionState.value == c && c !is ConnectionState.Connected) return
        Timber.d("onConnectionChanged: ${connectionStateHolder.connectionState.value} -> $c")

        // Cancel any existing timeouts
        sleepTimeout?.cancel()
        sleepTimeout = null

        when (c) {
            is ConnectionState.Connecting -> {
                connectionStateHolder.setState(ConnectionState.Connecting)
            }

            is ConnectionState.Connected -> {
                handleConnected()
            }

            is ConnectionState.DeviceSleep -> {
                handleDeviceSleep()
            }

            is ConnectionState.Disconnected -> {
                handleDisconnected()
            }
        }
        updateServiceStatusNotification()
    }

    private fun handleDisconnected() {
        connectionStateHolder.setState(ConnectionState.Disconnected)
        Timber.d("Starting disconnect")
        packetHandler.stopPacketQueue()
        stopLocationRequests()
        stopMqttClientProxy()

        analytics.track("mesh_disconnect", DataPair("num_nodes", numNodes), DataPair("num_online", numOnlineNodes))
        analytics.track("num_nodes", DataPair("num_nodes", numNodes))

        // broadcast an intent with our new connection state
        serviceBroadcasts.broadcastConnection()
    }

    private fun handleDeviceSleep() {
        connectionStateHolder.setState(ConnectionState.DeviceSleep)
        packetHandler.stopPacketQueue()
        stopLocationRequests()
        stopMqttClientProxy()

        if (connectTimeMsec != 0L) {
            val now = System.currentTimeMillis()
            connectTimeMsec = 0L

            analytics.track("connected_seconds", DataPair("connected_seconds", (now - connectTimeMsec) / 1000.0))
        }

        // Have our timeout fire in the appropriate number of seconds
        sleepTimeout =
            serviceScope.handledLaunch {
                try {
                    // If we have a valid timeout, wait that long (+30 seconds) otherwise, just
                    // wait 30 seconds
                    val timeout = (localConfig.power?.lsSecs ?: 0) + 30

                    Timber.d("Waiting for sleeping device, timeout=$timeout secs")
                    delay(timeout * 1000L)
                    Timber.w("Device timeout out, setting disconnected")
                    onConnectionChanged(ConnectionState.Disconnected)
                } catch (_: CancellationException) {
                    Timber.d("device sleep timeout cancelled")
                }
            }

        // broadcast an intent with our new connection state
        serviceBroadcasts.broadcastConnection()
    }

    private fun handleConnected() {
        connectionStateHolder.setState(ConnectionState.Connecting)
        serviceBroadcasts.broadcastConnection()
        Timber.d("Starting connect")
        historyLog {
            val address = meshPrefs.deviceAddress ?: "null"
            "onReconnect transport=${currentTransport()} node=$address"
        }
        try {
            connectTimeMsec = System.currentTimeMillis()
            startConfigOnly()
        } catch (ex: InvalidProtocolBufferException) {
            Timber.e(ex, "Invalid protocol buffer sent by device - update device software and try again")
        } catch (ex: RadioNotConnectedException) {
            Timber.e("Lost connection to radio during init - waiting for reconnect ${ex.message}")
        } catch (ex: RemoteException) {
            onConnectionChanged(ConnectionState.DeviceSleep)
            throw ex
        }
    }

    private fun updateServiceStatusNotification(telemetry: TelemetryProtos.Telemetry? = null): Notification {
        val notificationSummary =
            when (connectionStateHolder.connectionState.value) {
                is ConnectionState.Connected -> getString(Res.string.connected_count).format(numOnlineNodes)

                is ConnectionState.Disconnected -> getString(Res.string.disconnected)
                is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)
                is ConnectionState.Connecting -> getString(Res.string.connecting)
            }
        return serviceNotifications.updateServiceStateNotification(
            summaryString = notificationSummary,
            telemetry = telemetry,
        )
    }

    private fun onRadioConnectionState(newState: ConnectionState) {
        // Respect light sleep (lsEnabled) setting: if device reports sleep
        // but lsEnabled is false, treat as disconnected.
        val isRouter = localConfig.device.role == ConfigProtos.Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power.isPowerSaving || isRouter

        val effectiveState =
            when (newState) {
                is ConnectionState.Connected -> ConnectionState.Connected
                is ConnectionState.DeviceSleep ->
                    if (lsEnabled) {
                        ConnectionState.DeviceSleep
                    } else {
                        ConnectionState.Disconnected
                    }

                is ConnectionState.Connecting -> ConnectionState.Connecting
                is ConnectionState.Disconnected -> ConnectionState.Disconnected
            }
        onConnectionChanged(effectiveState)
    }

    private val packetHandlers: Map<PayloadVariantCase, ((MeshProtos.FromRadio) -> Unit)> by lazy {
        PayloadVariantCase.entries.associateWith { variant: PayloadVariantCase ->
            when (variant) {
                PayloadVariantCase.PACKET -> { proto: MeshProtos.FromRadio -> handleReceivedMeshPacket(proto.packet) }

                PayloadVariantCase.CONFIG_COMPLETE_ID -> { proto: MeshProtos.FromRadio ->
                    handleConfigComplete(proto.configCompleteId)
                }

                PayloadVariantCase.MY_INFO -> { proto: MeshProtos.FromRadio -> handleMyInfo(proto.myInfo) }

                PayloadVariantCase.NODE_INFO -> { proto: MeshProtos.FromRadio -> handleNodeInfo(proto.nodeInfo) }

                PayloadVariantCase.CHANNEL -> { proto: MeshProtos.FromRadio -> handleChannel(proto.channel) }

                PayloadVariantCase.CONFIG -> { proto: MeshProtos.FromRadio -> handleDeviceConfig(proto.config) }

                PayloadVariantCase.MODULECONFIG -> { proto: MeshProtos.FromRadio ->
                    handleModuleConfig(proto.moduleConfig)
                }

                PayloadVariantCase.QUEUESTATUS -> { proto: MeshProtos.FromRadio ->
                    packetHandler.handleQueueStatus((proto.queueStatus))
                }

                PayloadVariantCase.METADATA -> { proto: MeshProtos.FromRadio -> handleMetadata(proto.metadata) }

                PayloadVariantCase.MQTTCLIENTPROXYMESSAGE -> { proto: MeshProtos.FromRadio ->
                    handleMqttProxyMessage(proto.mqttClientProxyMessage)
                }

                PayloadVariantCase.DEVICEUICONFIG -> { proto: MeshProtos.FromRadio ->
                    handleDeviceUiConfig(proto.deviceuiConfig)
                }

                PayloadVariantCase.FILEINFO -> { proto: MeshProtos.FromRadio -> handleFileInfo(proto.fileInfo) }

                PayloadVariantCase.CLIENTNOTIFICATION -> { proto: MeshProtos.FromRadio ->
                    handleClientNotification(proto.clientNotification)
                }

                PayloadVariantCase.LOG_RECORD -> { proto: MeshProtos.FromRadio -> handleLogRecord(proto.logRecord) }

                PayloadVariantCase.REBOOTED -> { proto: MeshProtos.FromRadio -> handleRebooted(proto.rebooted) }

                PayloadVariantCase.XMODEMPACKET -> { proto: MeshProtos.FromRadio ->
                    handleXmodemPacket(proto.xmodemPacket)
                }

                // Explicitly handle default/unwanted cases to satisfy the exhaustive `when`
                PayloadVariantCase.PAYLOADVARIANT_NOT_SET -> { proto ->
                    Timber.d("Received variant PayloadVariantUnset: Full FromRadio proto: ${proto.toPIIString()}")
                }
            }
        }
    }

    private fun MeshProtos.FromRadio.route() {
        packetHandlers[this.payloadVariantCase]?.invoke(this)
    }

    /**
     * Parses and routes incoming data from the radio.
     *
     * This function first attempts to parse the data as a `FromRadio` protobuf message. If that fails, it then tries to
     * parse it as a `LogRecord` for debugging purposes.
     */
    private fun onReceiveFromRadio(bytes: ByteArray) {
        runCatching { MeshProtos.FromRadio.parseFrom(bytes) }
            .onSuccess { proto ->
                if (proto.payloadVariantCase == PayloadVariantCase.PAYLOADVARIANT_NOT_SET) {
                    Timber.w(
                        "Received FromRadio with PAYLOADVARIANT_NOT_SET. rawBytes=${bytes.toHexString()} proto=$proto",
                    )
                }
                proto.route()
            }
            .onFailure { primaryException ->
                runCatching {
                    val logRecord = MeshProtos.LogRecord.parseFrom(bytes)
                    handleLogRecord(logRecord)
                }
                    .onFailure { _ ->
                        Timber.e(
                            primaryException,
                            "Failed to parse radio packet (len=${bytes.size} contents=${bytes.toHexString()}). " +
                                "Not a valid FromRadio or LogRecord.",
                        )
                    }
            }
    }

    /** Extension function to convert a ByteArray to a hex string for logging. Example output: "0x0a,0x1f,0x..." */
    private fun ByteArray.toHexString(): String =
        this.joinToString(",") { byte -> String.format(Locale.US, "0x%02x", byte) }

    // A provisional MyNodeInfo that we will install if all of our node config downloads go okay
    private var newMyNodeInfo: MyNodeEntity? = null

    // provisional NodeInfos we will install if all goes well
    private val newNodes = mutableListOf<MeshProtos.NodeInfo>()

    // Nonces for two-stage config flow (match Meshtastic-Apple)
    private var configOnlyNonce: Int = DEFAULT_CONFIG_ONLY_NONCE
    private var nodeInfoNonce: Int = DEFAULT_NODE_INFO_NONCE

    private fun handleDeviceConfig(config: ConfigProtos.Config) {
        Timber.d("[deviceConfig] ${config.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Config ${config.payloadVariantCase}",
                received_date = System.currentTimeMillis(),
                raw_message = config.toString(),
                fromRadio = fromRadio { this.config = config },
            )
        insertMeshLog(packetToSave)
        setLocalConfig(config)
        val configCount = localConfig.allFields.size
        serviceRepository.setStatusMessage("Device config ($configCount / $configTotal)")
    }

    private fun handleModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        Timber.d("[moduleConfig] ${config.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ModuleConfig ${config.payloadVariantCase}",
                received_date = System.currentTimeMillis(),
                raw_message = config.toString(),
                fromRadio = fromRadio { moduleConfig = config },
            )
        insertMeshLog(packetToSave)
        setLocalModuleConfig(config)
        val moduleCount = moduleConfig.allFields.size
        serviceRepository.setStatusMessage("Module config ($moduleCount / $moduleTotal)")
    }

    private fun handleChannel(ch: ChannelProtos.Channel) {
        Timber.d("[channel] ${ch.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Channel",
                received_date = System.currentTimeMillis(),
                raw_message = ch.toString(),
                fromRadio = fromRadio { channel = ch },
            )
        insertMeshLog(packetToSave)
        if (ch.role != ChannelProtos.Channel.Role.DISABLED) updateChannelSettings(ch)
        val maxChannels = myNodeInfo?.maxChannels ?: 8
        serviceRepository.setStatusMessage("Channels (${ch.index + 1} / $maxChannels)")
    }

    /** Convert a protobuf NodeInfo into our model objects and update our node DB */
    private fun installNodeInfo(info: MeshProtos.NodeInfo, withBroadcast: Boolean = true) {
        // Just replace/add any entry
        updateNodeInfo(info.num, withBroadcast = withBroadcast) {
            if (info.hasUser()) {
                // Check if this is a default/unknown user from firmware (node was evicted and re-created)
                val shouldPreserve = shouldPreserveExistingUser(it.user, info.user)

                if (shouldPreserve) {
                    // Firmware sent us a placeholder - keep all our existing user data
                    Timber.d(
                        "Preserving existing user data for node ${info.num}: " +
                            "kept='${it.user.longName}' (hwModel=${it.user.hwModel}), " +
                            "skipped default='${info.user.longName}' (hwModel=UNSET)",
                    )
                } else {
                    it.user =
                        info.user.copy {
                            if (isLicensed) clearPublicKey()
                            if (info.viaMqtt) longName = "$longName (MQTT)"
                        }
                    it.longName = it.user.longName
                    it.shortName = it.user.shortName
                }
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

            // hopsAway should be nullable/optional from the proto, but explicitly checking it's
            // existence first
            it.hopsAway =
                if (info.hasHopsAway()) {
                    info.hopsAway
                } else {
                    -1
                }
            it.isFavorite = info.isFavorite
            it.isIgnored = info.isIgnored
        }
    }

    private fun handleNodeInfo(info: MeshProtos.NodeInfo) {
        Timber.d("[nodeInfo] ${info.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "NodeInfo",
                received_date = System.currentTimeMillis(),
                raw_message = info.toString(),
                fromRadio = fromRadio { nodeInfo = info },
            )
        insertMeshLog(packetToSave)

        newNodes.add(info)
        serviceRepository.setStatusMessage("Nodes (${newNodes.size})")
    }

    private fun handleNodeInfoComplete() {
        Timber.d("NodeInfo complete for nonce $nodeInfoNonce")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "NodeInfoComplete",
                received_date = System.currentTimeMillis(),
                raw_message = nodeInfoNonce.toString(),
                fromRadio = fromRadio { this.configCompleteId = nodeInfoNonce },
            )
        insertMeshLog(packetToSave)
        if (newNodes.isEmpty()) {
            Timber.e("Did not receive a valid node info")
        } else {
            // Batch update: Update in-memory models first without triggering individual DB writes
            val entities =
                newNodes.mapNotNull { info ->
                    installNodeInfo(info, withBroadcast = false)
                    nodeDBbyNodeNum[info.num]
                }
            newNodes.clear()

            // Perform a single batch DB transaction for all nodes + myNodeInfo
            serviceScope.handledLaunch { myNodeInfo?.let { nodeRepository.installConfig(it, entities) } }

            // Enable DB writes for future individual updates
            allowNodeDbWrites = true
            isNodeDbReady = true
            flushEarlyReceivedPackets("node_info_complete")
            sendAnalytics()
            onHasSettings()
            connectionStateHolder.setState(ConnectionState.Connected)
            serviceBroadcasts.broadcastConnection()
            updateServiceStatusNotification()
        }
    }

    private var rawMyNodeInfo: MeshProtos.MyNodeInfo? = null

    /**
     * Regenerate the myNodeInfo model. We call this twice. Once after we receive myNodeInfo from the device and again
     * after we have the node DB (which might allow us a better notion of our HwModel.
     */
    private fun regenMyNodeInfo(metadata: MeshProtos.DeviceMetadata? = MeshProtos.DeviceMetadata.getDefaultInstance()) {
        val myInfo = rawMyNodeInfo
        if (myInfo != null) {
            val mi =
                with(myInfo) {
                    MyNodeEntity(
                        myNodeNum = myNodeNum,
                        model =
                        when (val hwModel = metadata?.hwModel) {
                            null,
                            MeshProtos.HardwareModel.UNSET,
                            -> null

                            else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                        },
                        firmwareVersion = metadata?.firmwareVersion,
                        couldUpdate = false,
                        shouldUpdate = false, // TODO add check after re-implementing firmware updates
                        currentPacketId = currentPacketId and 0xffffffffL,
                        messageTimeoutMsec = 5 * 60 * 1000, // constants from current firmware code
                        minAppVersion = minAppVersion,
                        maxChannels = 8,
                        hasWifi = metadata?.hasWifi == true,
                        deviceId = deviceId.toStringUtf8(),
                    )
                }
            if (metadata != null && metadata != MeshProtos.DeviceMetadata.getDefaultInstance()) {
                serviceScope.handledLaunch { nodeRepository.insertMetadata(MetadataEntity(mi.myNodeNum, metadata)) }
            }
            newMyNodeInfo = mi
        }
    }

    private fun sendAnalytics() {
        val myInfo = rawMyNodeInfo
        val mi = myNodeInfo
        if (myInfo != null && mi != null) {
            // Track types of devices and firmware versions in use
            analytics.setDeviceAttributes(mi.firmwareVersion ?: "unknown", mi.model ?: "unknown")
        }
    }

    /** Update MyNodeInfo (called from either new API version or the old one) */
    private fun handleMyInfo(myInfo: MeshProtos.MyNodeInfo) {
        Timber.d("[myInfo] ${myInfo.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "MyNodeInfo",
                received_date = System.currentTimeMillis(),
                raw_message = myInfo.toString(),
                fromRadio = fromRadio { this.myInfo = myInfo },
            )
        insertMeshLog(packetToSave)

        rawMyNodeInfo = myInfo
        regenMyNodeInfo()

        // We'll need to get a new set of channels and settings now
        serviceScope.handledLaunch {
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    /** Update our DeviceMetadata */
    private fun handleMetadata(metadata: MeshProtos.DeviceMetadata) {
        Timber.d("[deviceMetadata] ${metadata.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "DeviceMetadata",
                received_date = System.currentTimeMillis(),
                raw_message = metadata.toString(),
                fromRadio = fromRadio { this.metadata = metadata },
            )
        insertMeshLog(packetToSave)

        regenMyNodeInfo(metadata)
    }

    /** Publish MqttClientProxyMessage (fromRadio) */
    private fun handleMqttProxyMessage(message: MeshProtos.MqttClientProxyMessage) {
        Timber.d("[mqttClientProxyMessage] ${message.toPIIString()}")
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
        Timber.d("[clientNotification] ${notification.toPIIString()}")
        serviceRepository.setClientNotification(notification)
        serviceNotifications.showClientNotification(notification)
        // if the future for the originating request is still in the queue, complete as unsuccessful
        // for now
        packetHandler.removeResponse(notification.replyId, complete = false)
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ClientNotification",
                received_date = System.currentTimeMillis(),
                raw_message = notification.toString(),
                fromRadio = fromRadio { this.clientNotification = notification },
            )
        insertMeshLog(packetToSave)
    }

    private fun handleFileInfo(fileInfo: MeshProtos.FileInfo) {
        Timber.d("[fileInfo] ${fileInfo.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "FileInfo",
                received_date = System.currentTimeMillis(),
                raw_message = fileInfo.toString(),
                fromRadio = fromRadio { this.fileInfo = fileInfo },
            )
        insertMeshLog(packetToSave)
    }

    private fun handleLogRecord(logRecord: MeshProtos.LogRecord) {
        Timber.d("[logRecord] ${logRecord.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "LogRecord",
                received_date = System.currentTimeMillis(),
                raw_message = logRecord.toString(),
                fromRadio = fromRadio { this.logRecord = logRecord },
            )
        insertMeshLog(packetToSave)
    }

    private fun handleRebooted(rebooted: Boolean) {
        Timber.d("[rebooted] $rebooted")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Rebooted",
                received_date = System.currentTimeMillis(),
                raw_message = rebooted.toString(),
                fromRadio = fromRadio { this.rebooted = rebooted },
            )
        insertMeshLog(packetToSave)
    }

    private fun handleXmodemPacket(xmodemPacket: XmodemProtos.XModem) {
        Timber.d("[xmodemPacket] ${xmodemPacket.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "XmodemPacket",
                received_date = System.currentTimeMillis(),
                raw_message = xmodemPacket.toString(),
                fromRadio = fromRadio { this.xmodemPacket = xmodemPacket },
            )
        insertMeshLog(packetToSave)
    }

    private fun handleDeviceUiConfig(deviceuiConfig: DeviceUIProtos.DeviceUIConfig) {
        Timber.d("[deviceuiConfig] ${deviceuiConfig.toPIIString()}")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "DeviceUIConfig",
                received_date = System.currentTimeMillis(),
                raw_message = deviceuiConfig.toString(),
                fromRadio = fromRadio { this.deviceuiConfig = deviceuiConfig },
            )
        insertMeshLog(packetToSave)
    }

    /** Connect, subscribe and receive Flow of MqttClientProxyMessage (toRadio) */
    private fun startMqttClientProxy() {
        if (mqttMessageFlow?.isActive == true) return
        if (moduleConfig.mqtt.enabled && moduleConfig.mqtt.proxyToClientEnabled) {
            mqttMessageFlow =
                mqttRepository.proxyMessageFlow
                    .onEach { message ->
                        packetHandler.sendToRadio(ToRadio.newBuilder().apply { mqttClientProxyMessage = message })
                    }
                    .catch { throwable -> serviceRepository.setErrorMessage("MqttClientProxy failed: $throwable") }
                    .launchIn(serviceScope)
        }
    }

    private fun stopMqttClientProxy() {
        if (mqttMessageFlow?.isActive == true) {
            Timber.i("Stopping MqttClientProxy")
            mqttMessageFlow?.cancel()
            mqttMessageFlow = null
        }
    }

    private fun onHasSettings() {
        processQueuedPackets()
        startMqttClientProxy()
        sendAnalytics()
        reportConnection()
        historyLog {
            val ports =
                rememberDataType.joinToString(",") { port -> Portnums.PortNum.forNumber(port)?.name ?: port.toString() }
            "subscribePorts afterReconnect ports=$ports"
        }
        requestHistoryReplay("onHasSettings")
        packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { setTimeOnly = currentSecond() })
    }

    private fun handleConfigComplete(configCompleteId: Int) {
        Timber.d("[configCompleteId]: ${configCompleteId.toPIIString()}")
        when (configCompleteId) {
            configOnlyNonce -> handleConfigOnlyComplete()
            nodeInfoNonce -> handleNodeInfoComplete()
            else ->
                Timber.w(
                    "Config complete id mismatch: received=$configCompleteId expected one of [$configOnlyNonce,$nodeInfoNonce]",
                )
        }
    }

    private fun handleConfigOnlyComplete() {
        Timber.d("Config-only complete for nonce $configOnlyNonce")
        val packetToSave =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ConfigOnlyComplete",
                received_date = System.currentTimeMillis(),
                raw_message = configOnlyNonce.toString(),
                fromRadio = fromRadio { this.configCompleteId = configOnlyNonce },
            )
        insertMeshLog(packetToSave)

        if (newMyNodeInfo == null) {
            Timber.e("Did not receive a valid config")
        } else {
            myNodeInfo = newMyNodeInfo
        }
        // Keep BLE awake and allow the firmware to settle before the node-info stage.
        serviceScope.handledLaunch {
            delay(WANT_CONFIG_DELAY)
            sendHeartbeat()
            delay(WANT_CONFIG_DELAY)
            startNodeInfoOnly()
        }
    }

    /** Send a ToRadio heartbeat to keep the link alive without producing mesh traffic. */
    private fun sendHeartbeat() {
        try {
            packetHandler.sendToRadio(
                ToRadio.newBuilder().apply { heartbeat = MeshProtos.Heartbeat.getDefaultInstance() },
            )
            Timber.d("Heartbeat sent between nonce stages")
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to send heartbeat; proceeding with node-info stage")
        }
    }

    private fun startConfigOnly() {
        newMyNodeInfo = null
        Timber.d("Starting config-only nonce=$configOnlyNonce")
        packetHandler.sendToRadio(ToRadio.newBuilder().apply { this.wantConfigId = configOnlyNonce })
    }

    private fun startNodeInfoOnly() {
        newNodes.clear()
        Timber.d("Starting node-info nonce=$nodeInfoNonce")
        packetHandler.sendToRadio(ToRadio.newBuilder().apply { this.wantConfigId = nodeInfoNonce })
    }

    /** Send a position (typically from our built in GPS) into the mesh. */
    private fun sendPosition(position: MeshProtos.Position, destNum: Int? = null, wantResponse: Boolean = false) {
        try {
            val mi = myNodeInfo
            if (mi != null) {
                val idNum = destNum ?: mi.myNodeNum // when null we just send to the local node
                Timber.d("Sending our position/time to=$idNum ${Position(position)}")

                // Also update our own map for our nodeNum, by handling the packet just like packets from other users
                if (!localConfig.position.fixedPosition) {
                    handleReceivedPosition(mi.myNodeNum, position)
                }

                packetHandler.sendToRadio(
                    newMeshPacketTo(idNum).buildMeshPacket(
                        channel = if (destNum == null) 0 else nodeDBbyNodeNum[destNum]?.channel ?: 0,
                        priority = MeshPacket.Priority.BACKGROUND,
                    ) {
                        portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                        payload = position.toByteString()
                        this.wantResponse = wantResponse
                    },
                )
            }
        } catch (_: BLEException) {
            Timber.w("Ignoring disconnected radio during gps location update")
        }
    }

    /** Send setOwner admin packet with [MeshProtos.User] protobuf */
    private fun setOwner(packetId: Int, user: MeshProtos.User) = with(user) {
        val dest = nodeDBbyID[id] ?: throw Exception("Can't set user without a NodeInfo") // this shouldn't happen
        val old = dest.user

        @Suppress("ComplexCondition")
        if (user == old) {
            Timber.d("Ignoring nop owner change")
        } else {
            Timber.d(
                "setOwner Id: $id longName: ${longName.anonymize} shortName: $shortName isLicensed: $isLicensed isUnmessagable: $isUnmessagable",
            )

            // Also update our own map for our nodeNum, by handling the packet just like packets from other users
            handleReceivedUser(dest.num, user)

            // encapsulate our payload in the proper protobuf and fire it off
            packetHandler.sendToRadio(newMeshPacketTo(dest.num).buildAdminPacket(id = packetId) { setOwner = user })
        }
    }

    // Do not use directly, instead call generatePacketId()
    private var currentPacketId = java.util.Random(System.currentTimeMillis()).nextLong().absoluteValue

    /** Generate a unique packet ID (if we know enough to do so - otherwise return 0 so the device will do it) */
    @Synchronized
    private fun generatePacketId(): Int {
        val numPacketIds = ((1L shl 32) - 1)
        currentPacketId++
        currentPacketId = currentPacketId and 0xffffffff
        return ((currentPacketId % numPacketIds) + 1L).toInt()
    }

    private fun enqueueForSending(p: DataPacket) {
        if (p.dataType in rememberDataType) {
            offlineSentPackets.add(p)
        }
    }

    private fun onServiceAction(action: ServiceAction) {
        ignoreException {
            when (action) {
                is ServiceAction.GetDeviceMetadata -> getDeviceMetadata(action.destNum)
                is ServiceAction.Favorite -> favoriteNode(action.node)
                is ServiceAction.Ignore -> ignoreNode(action.node)
                is ServiceAction.Reaction -> sendReaction(action)
                is ServiceAction.ImportContact -> importContact(action.contact)
                is ServiceAction.SendContact -> sendContact(action.contact)
            }
        }
    }

    /**
     * Imports a manually shared contact.
     *
     * This function takes a [AdminProtos.SharedContact] proto, marks it as manually verified, sends it for further
     * processing, and then handles the import specific logic.
     *
     * @param contact The [AdminProtos.SharedContact] to be imported.
     */
    private fun importContact(contact: AdminProtos.SharedContact) {
        val verifiedContact = contact.copy { manuallyVerified = true }
        sendContact(verifiedContact)
        handleSharedContactImport(contact = verifiedContact)
    }

    /**
     * Sends a shared contact to the radio via [AdminProtos.AdminMessage]
     *
     * @param contact The contact to send.
     */
    private fun sendContact(contact: AdminProtos.SharedContact) {
        packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { addContact = contact })
    }

    private fun getDeviceMetadata(destNum: Int) = toRemoteExceptions {
        packetHandler.sendToRadio(
            newMeshPacketTo(destNum).buildAdminPacket(wantResponse = true) { getDeviceMetadataRequest = true },
        )
    }

    private fun favoriteNode(node: Node) = toRemoteExceptions {
        packetHandler.sendToRadio(
            newMeshPacketTo(myNodeNum).buildAdminPacket {
                if (node.isFavorite) {
                    Timber.d("removing node ${node.num} from favorite list")
                    removeFavoriteNode = node.num
                } else {
                    Timber.d("adding node ${node.num} to favorite list")
                    setFavoriteNode = node.num
                }
            },
        )
        updateNodeInfo(node.num) { it.isFavorite = !node.isFavorite }
    }

    private fun ignoreNode(node: Node) = toRemoteExceptions {
        packetHandler.sendToRadio(
            newMeshPacketTo(myNodeNum).buildAdminPacket {
                if (node.isIgnored) {
                    Timber.d("removing node ${node.num} from ignore list")
                    removeIgnoredNode = node.num
                } else {
                    Timber.d("adding node ${node.num} to ignore list")
                    setIgnoredNode = node.num
                }
            },
        )
        updateNodeInfo(node.num) { it.isIgnored = !node.isIgnored }
    }

    private fun sendReaction(reaction: ServiceAction.Reaction) = toRemoteExceptions {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = reaction.contactKey[0].digitToInt()
        val destNum = reaction.contactKey.substring(1)

        val packet =
            newMeshPacketTo(destNum).buildMeshPacket(channel = channel, priority = MeshPacket.Priority.BACKGROUND) {
                emoji = 1
                replyId = reaction.replyId
                portnumValue = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                payload = ByteString.copyFrom(reaction.emoji.encodeToByteArray())
            }
        packetHandler.sendToRadio(packet)
        rememberReaction(packet.copy { from = myNodeNum })
    }

    private fun updateLastAddress(deviceAddr: String?) {
        val currentAddr = meshPrefs.deviceAddress
        Timber.d("setDeviceAddress: received request to change to: ${deviceAddr.anonymize}")
        if (deviceAddr != currentAddr) {
            Timber.d(
                "SetDeviceAddress: Device address changed from ${currentAddr.anonymize} to ${deviceAddr.anonymize}",
            )
            val currentLabel = currentAddr ?: "null"
            val nextLabel = deviceAddr ?: "null"
            val nextTransport = currentTransport(deviceAddr)
            historyLog { "dbSwitch request current=$currentLabel next=$nextLabel transportNext=$nextTransport" }
            meshPrefs.deviceAddress = deviceAddr
            serviceScope.handledLaunch {
                // Clear only in-memory caches to avoid cross-device bleed
                discardNodeDB()
                // Switch active on-disk DB to device-specific database
                databaseManager.switchActiveDatabase(deviceAddr)
                val activeAddress = databaseManager.currentAddress.value
                val activeLabel = activeAddress ?: "null"
                val transportLabel = currentTransport()
                val meshAddress = meshPrefs.deviceAddress ?: "null"
                val nodeId = myNodeInfo?.myNodeNum?.toString() ?: "unknown"
                val dbSummary =
                    "dbSwitch activeAddress=$activeLabel nodeId=$nodeId transport=$transportLabel addr=$meshAddress"
                historyLog { dbSummary }
                // Do not clear packet DB here; messages are per-device and should persist
                clearNotifications()
                // Reload nodes from the newly switched database
                loadCachedNodeDB()
            }
        } else {
            Timber.d("SetDeviceAddress: Device address is unchanged, ignoring.")
        }
    }

    private fun clearNotifications() {
        serviceNotifications.clearNotifications()
    }

    private val binder =
        object : IMeshService.Stub() {

            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                Timber.d("Passing through device change to radio service: ${deviceAddr.anonymize}")
                updateLastAddress(deviceAddr)
                radioInterfaceService.setDeviceAddress(deviceAddr)
            }

            override fun subscribeReceiver(packageName: String, receiverName: String) = toRemoteExceptions {
                serviceBroadcasts.subscribeReceiver(receiverName, packageName)
            }

            override fun getUpdateStatus(): Int = -4

            override fun startFirmwareUpdate() = toRemoteExceptions {}

            override fun getMyNodeInfo(): MyNodeInfo? = this@MeshService.myNodeInfo?.toMyNodeInfo()

            override fun getMyId() = toRemoteExceptions { myNodeID }

            override fun getPacketId() = toRemoteExceptions { generatePacketId() }

            override fun setOwner(user: MeshUser) = toRemoteExceptions {
                setOwner(
                    generatePacketId(),
                    user {
                        id = user.id
                        longName = user.longName
                        shortName = user.shortName
                        isLicensed = user.isLicensed
                    },
                )
            }

            override fun setRemoteOwner(id: Int, payload: ByteArray) = toRemoteExceptions {
                val parsed = MeshProtos.User.parseFrom(payload)
                setOwner(id, parsed)
            }

            override fun getRemoteOwner(id: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) { getOwnerRequest = true },
                )
            }

            override fun send(p: DataPacket) {
                toRemoteExceptions {
                    if (p.id == 0) p.id = generatePacketId()
                    val bytes = p.bytes!!
                    Timber.i(
                        "sendData dest=${p.to}, id=${p.id} <- ${bytes.size} bytes (connectionState=${connectionStateHolder.connectionState.value})",
                    )
                    if (p.dataType == 0) throw Exception("Port numbers must be non-zero!")
                    if (bytes.size >= MeshProtos.Constants.DATA_PAYLOAD_LEN.number) {
                        p.status = MessageStatus.ERROR
                        throw RemoteException("Message too long")
                    } else {
                        p.status = MessageStatus.QUEUED
                    }
                    if (connectionStateHolder.connectionState.value == ConnectionState.Connected) {
                        try {
                            sendNow(p)
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error sending message, so enqueueing")
                            enqueueForSending(p)
                        }
                    } else {
                        enqueueForSending(p)
                    }
                    serviceBroadcasts.broadcastMessageStatus(p)
                    rememberDataPacket(p, false)
                    analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", p.dataType))
                }
            }

            override fun getConfig(): ByteArray = toRemoteExceptions {
                this@MeshService.localConfig.toByteArray() ?: throw NoDeviceConfigException()
            }

            override fun setConfig(payload: ByteArray) = toRemoteExceptions {
                setRemoteConfig(generatePacketId(), myNodeNum, payload)
            }

            override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                Timber.d("Setting new radio config!")
                val config = ConfigProtos.Config.parseFrom(payload)
                packetHandler.sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setConfig = config })
                if (num == myNodeNum) setLocalConfig(config)
            }

            override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        if (config == AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE) {
                            getDeviceMetadataRequest = true
                        } else {
                            getConfigRequestValue = config
                        }
                    },
                )
            }

            override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                Timber.d("Setting new module config!")
                val config = ModuleConfigProtos.ModuleConfig.parseFrom(payload)
                packetHandler.sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setModuleConfig = config })
                if (num == myNodeNum) setLocalModuleConfig(config)
            }

            override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getModuleConfigRequestValue = config
                    },
                )
            }

            override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
                packetHandler.sendToRadio(newMeshPacketTo(destNum).buildAdminPacket { setRingtoneMessage = ringtone })
            }

            override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getRingtoneRequest = true
                    },
                )
            }

            override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket { setCannedMessageModuleMessages = messages },
                )
            }

            override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getCannedMessageModuleMessagesRequest = true
                    },
                )
            }

            override fun setChannel(payload: ByteArray?) = toRemoteExceptions {
                setRemoteChannel(generatePacketId(), myNodeNum, payload)
            }

            override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray?) = toRemoteExceptions {
                val channel = ChannelProtos.Channel.parseFrom(payload)
                packetHandler.sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setChannel = channel })
            }

            override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getChannelRequest = index + 1
                    },
                )
            }

            override fun beginEditSettings() = toRemoteExceptions {
                packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { beginEditSettings = true })
            }

            override fun commitEditSettings() = toRemoteExceptions {
                packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { commitEditSettings = true })
            }

            override fun getChannelSet(): ByteArray = toRemoteExceptions { this@MeshService.channelSet.toByteArray() }

            override fun getNodes(): MutableList<NodeInfo> = toRemoteExceptions {
                val r = nodeDBbyNodeNum.values.map { it.toNodeInfo() }.toMutableList()
                Timber.i("in getOnline, count=${r.size}")
                r
            }

            override fun connectionState(): String = toRemoteExceptions {
                val r = connectionStateHolder.connectionState.value
                Timber.i("in connectionState=$r")
                r.toString()
            }

            override fun startProvideLocation() = toRemoteExceptions { startLocationRequests() }

            override fun stopProvideLocation() = toRemoteExceptions { stopLocationRequests() }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                nodeDBbyNodeNum.remove(nodeNum)
                packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { removeByNodenum = nodeNum })
            }

            override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    packetHandler.sendToRadio(
                        newMeshPacketTo(destNum).buildMeshPacket(channel = nodeDBbyNodeNum[destNum]?.channel ?: 0) {
                            portnumValue = Portnums.PortNum.NODEINFO_APP_VALUE
                            wantResponse = true
                            payload = nodeDBbyNodeNum[myNodeNum]!!.user.toByteString()
                        },
                    )
                }
            }

            override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    val provideLocation = meshPrefs.shouldProvideNodeLocation(myNodeNum)
                    val currentPosition =
                        when {
                            provideLocation && position.isValid() -> position
                            else -> nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
                        }
                    if (currentPosition == null) {
                        Timber.d("Position request skipped - no valid position available")
                        return@toRemoteExceptions
                    }
                    val meshPosition = position {
                        latitudeI = Position.degI(currentPosition.latitude)
                        longitudeI = Position.degI(currentPosition.longitude)
                        altitude = currentPosition.altitude
                        time = currentSecond()
                    }
                    packetHandler.sendToRadio(
                        newMeshPacketTo(destNum).buildMeshPacket(
                            channel = nodeDBbyNodeNum[destNum]?.channel ?: 0,
                            priority = MeshPacket.Priority.BACKGROUND,
                        ) {
                            portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                            payload = meshPosition.toByteString()
                            wantResponse = true
                        },
                    )
                }
            }

            override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
                val pos = position {
                    latitudeI = Position.degI(position.latitude)
                    longitudeI = Position.degI(position.longitude)
                    altitude = position.altitude
                }
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket {
                        if (position != Position(0.0, 0.0, 0)) {
                            setFixedPosition = pos
                        } else {
                            removeFixedPosition = true
                        }
                    },
                )
                updateNodeInfo(destNum) { it.setPosition(pos, currentSecond()) }
            }

            override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
                tracerouteStartTimes[requestId] = System.currentTimeMillis()
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildMeshPacket(
                        wantAck = true,
                        id = requestId,
                        channel = nodeDBbyNodeNum[destNum]?.channel ?: 0,
                    ) {
                        portnumValue = Portnums.PortNum.TRACEROUTE_APP_VALUE
                        wantResponse = true
                    },
                )
            }

            override fun requestShutdown(requestId: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { shutdownSeconds = 5 },
                )
            }

            override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { rebootSeconds = 5 },
                )
            }

            override fun rebootToDfu() {
                packetHandler.sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { enterDfuModeRequest = true })
            }

            override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { factoryResetDevice = 1 },
                )
            }

            override fun requestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) =
                toRemoteExceptions {
                    packetHandler.sendToRadio(
                        newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { nodedbReset = preserveFavorites },
                    )
                }

            override fun getDeviceConnectionStatus(requestId: Int, destNum: Int) = toRemoteExceptions {
                packetHandler.sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = requestId, wantResponse = true) {
                        getDeviceConnectionStatusRequest = true
                    },
                )
            }
        }
}
