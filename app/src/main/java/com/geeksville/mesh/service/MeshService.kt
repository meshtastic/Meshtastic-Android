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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.core.location.LocationCompat
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.DeviceUIProtos
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.StoreAndForwardProtos
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.TelemetryProtos.LocalStats
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.copy
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.ReactionEntity
import com.geeksville.mesh.fromRadio
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.getTracerouteResponse
import com.geeksville.mesh.position
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.repository.network.MQTTRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.telemetry
import com.geeksville.mesh.user
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.ignoreException
import com.geeksville.mesh.util.toOneLineString
import com.geeksville.mesh.util.toPIIString
import com.geeksville.mesh.util.toRemoteExceptions
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.absoluteValue

sealed class ServiceAction {
    data class GetDeviceMetadata(val destNum: Int) : ServiceAction()

    data class Favorite(val node: Node) : ServiceAction()

    data class Ignore(val node: Node) : ServiceAction()

    data class Reaction(val emoji: String, val replyId: Int, val contactKey: String) : ServiceAction()

    data class AddSharedContact(val contact: AdminProtos.SharedContact) : ServiceAction()
}

/**
 * Handles all communication with android apps and the Meshtastic device. It maintains an internal model of the network
 * state, manages device configurations, and processes incoming/outgoing packets.
 *
 * Note: This service will go away once all clients are unbound from it. Warning: Do not override toString, it causes
 * infinite recursion on some Android versions (because contextWrapper.getResources calls toString).
 */
@Suppress("MagicNumber")
@AndroidEntryPoint
class MeshService :
    Service(),
    Logging {
    @Inject lateinit var dispatchers: CoroutineDispatchers

    @Inject lateinit var packetRepository: Lazy<PacketRepository>

    @Inject lateinit var meshLogRepository: Lazy<MeshLogRepository>

    @Inject lateinit var radioInterfaceService: RadioInterfaceService

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var radioConfigRepository: RadioConfigRepository

    @Inject lateinit var mqttRepository: MQTTRepository

    @Inject lateinit var serviceNotifications: MeshServiceNotifications

    @Inject lateinit var connectionRouter: ConnectionRouter

    companion object : Logging {
        private const val MESH_PREFS_NAME = "mesh-prefs"
        private const val DEVICE_ADDRESS_KEY = "device_address"
        private const val ADMIN_CHANNEL_NAME = "admin"

        // Intents broadcast by MeshService
        private fun actionReceived(portNum: String) = "$prefix.RECEIVED.$portNum"

        /** Generates a RECEIVED action filter string for a given port number. */
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

        /** Initiates a device address change and starts the service. */
        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context) // Ensure service is started/foregrounded if needed
        }

        fun createIntent(context: Context): Intent = Intent(context, MeshService::class.java)

        val minDeviceVersion = DeviceVersion(BuildConfig.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(BuildConfig.ABS_MIN_FW_VERSION)

        private const val CONFIG_ONLY_NONCE = 69420
        private const val NODE_INFO_ONLY_NONCE = 69421
    }

    private var previousSummary: String? = null
    private var previousStats: LocalStats? = null

    private val clientPackages = ConcurrentHashMap<String, String>()
    private val serviceBroadcasts by lazy {
        MeshServiceBroadcasts(this, clientPackages) {
            connectionRouter.connectionState.value.also { radioConfigRepository.setConnectionState(it) }
        }
    }
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var locationFlow: Job? = null
    private var mqttMessageFlow: Job? = null

    // Battery thresholds and cooldowns
    private val batteryPercentUnsupported = 0.0
    private val batteryPercentLowThreshold = 20
    private val batteryPercentLowDivisor = 5
    private val batteryPercentCriticalThreshold = 5
    private val batteryPercentCooldownSeconds = 1500L
    private val batteryPercentCooldowns = ConcurrentHashMap<Int, Long>()

    private fun getSenderName(packet: DataPacket?): String {
        val nodeId = packet?.from ?: return getString(R.string.unknown_username)
        return nodeDBbyID[nodeId]?.user?.longName ?: getString(R.string.unknown_username)
    }

    private val notificationSummary: String
        get() =
            when (connectionRouter.connectionState.value) {
                ConnectionState.CONNECTED -> getString(R.string.connected_count, numOnlineNodes.toString())
                ConnectionState.DISCONNECTED -> getString(R.string.disconnected)
                ConnectionState.DEVICE_SLEEP -> getString(R.string.device_sleeping)
                ConnectionState.CONNECTING -> getString(R.string.connecting_to_device)
            }

    private var localStatsTelemetry: TelemetryProtos.Telemetry? = null
    private val localStats: LocalStats?
        get() = localStatsTelemetry?.localStats

    private val localStatsUpdatedAtMillis: Long?
        get() = localStatsTelemetry?.time?.let { it * 1000L }

    /** Starts location requests if permissions are granted and not already active. */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationRequests() {
        if (locationFlow?.isActive == true) return

        if (hasLocationPermission()) {
            locationFlow =
                locationRepository
                    .getLocations()
                    .onEach { location ->
                        val positionBuilder = position {
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
                        sendPosition(positionBuilder)
                    }
                    .launchIn(serviceScope)
        }
    }

    private fun stopLocationRequests() {
        locationFlow
            ?.takeIf { it.isActive }
            ?.let {
                info("Stopping location requests")
                it.cancel()
                locationFlow = null
            }
    }

    private fun sendToRadio(toRadioBuilder: ToRadio.Builder) {
        val builtProto = toRadioBuilder.build()
        debug("Sending to radio: ${builtProto.toPIIString()}")
        radioInterfaceService.sendToRadio(builtProto.toByteArray())

        if (toRadioBuilder.hasPacket()) {
            val packet = toRadioBuilder.packet
            changeStatus(packet.id, MessageStatus.ENROUTE)
            if (packet.hasDecoded()) {
                insertMeshLog(
                    MeshLog(
                        uuid = UUID.randomUUID().toString(),
                        message_type = "PacketSent", // Clarified type
                        received_date = System.currentTimeMillis(),
                        raw_message = packet.toString(),
                        fromNum = myNodeNum, // Correctly use myNodeNum for sent packets
                        portNum = packet.decoded.portnumValue,
                        fromRadio = fromRadio { this.packet = packet },
                    ),
                )
            }
        }
    }

    private fun sendToRadio(packet: MeshPacket) {
        queuedPackets.add(packet)
        startPacketQueue()
    }

    private fun showAlertNotification(contactKey: String, dataPacket: DataPacket) {
        serviceNotifications.showAlertNotification(
            contactKey,
            getSenderName(dataPacket),
            dataPacket.alert ?: getString(R.string.critical_alert),
        )
    }

    private fun updateMessageNotification(contactKey: String, dataPacket: DataPacket) {
        val message: String =
            when (dataPacket.dataType) {
                Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> dataPacket.text ?: return
                Portnums.PortNum.WAYPOINT_APP_VALUE ->
                    getString(R.string.waypoint_received, dataPacket.waypoint?.name ?: "")

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
        sharedPreferences = getSharedPreferences(MESH_PREFS_NAME, Context.MODE_PRIVATE)
        _lastAddress.value = sharedPreferences.getString(DEVICE_ADDRESS_KEY, null) ?: NO_DEVICE_SELECTED

        info("Creating mesh service")
        serviceNotifications.initChannels()
        connectionRouter.start()

        serviceScope.handledLaunch { radioInterfaceService.connect() }

        connectionRouter.connectionState
            .onEach { state ->
                when (state) {
                    ConnectionState.CONNECTED -> startConnect()
                    ConnectionState.DEVICE_SLEEP -> startDeviceSleep()
                    ConnectionState.DISCONNECTED -> startDisconnect()
                    else -> Unit
                }
            }
            .launchIn(serviceScope)

        radioInterfaceService.receivedData.onEach(::onReceiveFromRadio).launchIn(serviceScope)
        radioConfigRepository.localConfigFlow.onEach { localConfig = it }.launchIn(serviceScope)
        radioConfigRepository.moduleConfigFlow.onEach { moduleConfig = it }.launchIn(serviceScope)
        radioConfigRepository.channelSetFlow.onEach { channelSet = it }.launchIn(serviceScope)
        radioConfigRepository.serviceAction.onEach(::onServiceAction).launchIn(serviceScope)

        loadSettings()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = radioInterfaceService.getBondedDeviceAddress()
        val wantForeground = deviceAddress != null && deviceAddress != NO_DEVICE_SELECTED

        info("Requesting foreground service: $wantForeground")

        val notification = serviceNotifications.createServiceStateNotification(notificationSummary)
        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasLocationPermission()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
            } else {
                0
            }

        try {
            ServiceCompat.startForeground(this, serviceNotifications.notifyId, notification, foregroundServiceType)
        } catch (ex: SecurityException) {
            val errorMessage =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "startForeground failed, likely due to missing POST_NOTIFICATIONS permission on Android 13+"
                } else {
                    "startForeground failed"
                }
            errormsg(errorMessage, ex)
            return START_NOT_STICKY // Prevent service becoming sticky in a broken state
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
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        serviceJob.cancel()
        connectionRouter.stop()
    }

    // Node Database and Model Management
    private fun loadSettings() = serviceScope.handledLaunch {
        resetState() // Clear previous state
        myNodeInfo = radioConfigRepository.myNodeInfo.value
        val nodesFromDb = radioConfigRepository.getNodeDBbyNum()
        nodeDBbyNodeNum.putAll(nodesFromDb)
        nodesFromDb.values.forEach { nodeEntity ->
            if (nodeEntity.user.id.isNotEmpty()) {
                _nodeDBbyID[nodeEntity.user.id] = nodeEntity
            }
        }
    }

    /**
     * Resets all relevant service state variables to their defaults or clears collections. This is crucial when
     * switching to a new device connection to prevent state from a previous session from affecting the new one. It
     * ensures a clean slate for node information, configurations, pending operations, and cached data.
     */
    private fun resetState() = serviceScope.handledLaunch {
        debug("Discarding NodeDB and resetting all service state for new device connection")
        clearDatabases()
        // Core Node and Config data
        myNodeInfo = null
        rawMyNodeInfo = null

        nodeDBbyNodeNum.clear()
        _nodeDBbyID.clear()

        localStatsTelemetry = null
        sessionPasskey = ByteString.EMPTY

        currentPacketId = Random(System.currentTimeMillis()).nextLong().absoluteValue
        packetIdGenerator.set(Random(System.currentTimeMillis()).nextLong().absoluteValue)

        offlineSentPackets.clear()
        stopPacketQueue()

        connectTimeMsec = 0L

        stopLocationRequests()
        stopMqttClientProxy()

        previousSummary = null
        previousStats = null

        batteryPercentCooldowns.clear()

        radioConfigRepository.clearChannelSet()
        radioConfigRepository.clearLocalConfig()
        radioConfigRepository.clearLocalModuleConfig()

        info("MeshService state has been reset for a new device session.")
    }

    private var myNodeInfo: MyNodeEntity? = null
    private var rawMyNodeInfo: MeshProtos.MyNodeInfo? = null
    private var currentPacketId = Random(System.currentTimeMillis()).nextLong().absoluteValue
    private val configTotal by lazy { ConfigProtos.Config.getDescriptor().fields.size }
    private val moduleTotal by lazy { ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size }
    private var sessionPasskey: ByteString = ByteString.EMPTY
    private var localConfig: LocalConfig = LocalConfig.getDefaultInstance()
    private var moduleConfig: LocalModuleConfig = LocalModuleConfig.getDefaultInstance()
    private var channelSet: AppOnlyProtos.ChannelSet = AppOnlyProtos.ChannelSet.getDefaultInstance()

    private val nodeDBbyNodeNum = ConcurrentHashMap<Int, NodeEntity>()
    private val _nodeDBbyID = ConcurrentHashMap<String, NodeEntity>() // Cached map for ID lookups
    val nodeDBbyID: Map<String, NodeEntity>
        get() = _nodeDBbyID // Expose immutable view if needed externally

    private fun toNodeInfo(nodeNum: Int): NodeEntity =
        nodeDBbyNodeNum[nodeNum] ?: throw NodeNumNotFoundException(nodeNum)

    private fun toNodeID(nodeNum: Int): String = when (nodeNum) {
        DataPacket.NODENUM_BROADCAST -> DataPacket.ID_BROADCAST
        else -> nodeDBbyNodeNum[nodeNum]?.user?.id ?: DataPacket.nodeNumToDefaultId(nodeNum)
    }

    private fun getOrCreateNodeInfo(nodeNum: Int, channel: Int = 0): NodeEntity = nodeDBbyNodeNum.getOrPut(nodeNum) {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val defaultUser = user {
            id = userId
            longName = "Meshtastic ${userId.takeLast(4)}"
            shortName = userId.takeLast(4)
            hwModel = MeshProtos.HardwareModel.UNSET
        }
        NodeEntity(
            num = nodeNum,
            user = defaultUser,
            longName = defaultUser.longName,
            channel = channel,
        ).also { newEntity ->
            if (newEntity.user.id.isNotEmpty()) {
                _nodeDBbyID[newEntity.user.id] = newEntity
            }
        }
    }

    private val hexIdRegex = """\!([0-9A-Fa-f]+)""".toRegex()

    private fun toNodeInfo(id: String): NodeEntity = _nodeDBbyID[id]
        ?: run {
            val hexStr = hexIdRegex.matchEntire(id)?.groups?.get(1)?.value
            when {
                id == DataPacket.ID_LOCAL -> toNodeInfo(myNodeNum)
                hexStr != null -> {
                    val nodeNum = hexStr.toLong(16).toInt()
                    nodeDBbyNodeNum[nodeNum] ?: throw IdNotFoundException(id)
                }

                else -> throw InvalidNodeIdException(id)
            }
        }

    private fun getUserName(num: Int): String =
        radioConfigRepository.getUser(num).let { "${it.longName} (${it.shortName})" }

    private val numNodes: Int
        get() = nodeDBbyNodeNum.size

    private val numOnlineNodes: Int
        get() = nodeDBbyNodeNum.values.count { it.isOnline }

    private fun toNodeNum(id: String): Int = when (id) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        DataPacket.ID_LOCAL -> myNodeNum
        else -> toNodeInfo(id).num
    }

    private inline fun updateNodeInfo(
        nodeNum: Int,
        withBroadcast: Boolean = true,
        channel: Int = 0,
        crossinline updateFn: (NodeEntity) -> Unit,
    ) {
        val info = getOrCreateNodeInfo(nodeNum, channel)
        val oldUserId = info.user.id

        updateFn(info)

        val newUserId = info.user.id
        if (oldUserId.isNotEmpty() && oldUserId != newUserId) {
            _nodeDBbyID.remove(oldUserId)
        }
        if (newUserId.isNotEmpty()) {
            _nodeDBbyID[newUserId] = info
        }

        if (info.user.id.isNotEmpty()) {
            serviceScope.handledLaunch { radioConfigRepository.upsert(info) }
        }

        if (withBroadcast) {
            serviceBroadcasts.broadcastNodeChange(info.toNodeInfo())
        }
    }

    private val myNodeNum: Int
        get() = myNodeInfo?.myNodeNum ?: throw RadioNotConnectedException("Local node information not yet available")

    private val myNodeID: String
        get() = toNodeID(myNodeNum)

    private val MeshPacket.Builder.adminChannelIndex: Int
        get() =
            when {
                myNodeNum == to -> 0 // Admin channel to self is 0
                nodeDBbyNodeNum[myNodeNum]?.hasPKC == true && nodeDBbyNodeNum[to]?.hasPKC == true ->
                    DataPacket.PKC_CHANNEL_INDEX

                else ->
                    channelSet.settingsList
                        .indexOfFirst { it.name.equals(ADMIN_CHANNEL_NAME, ignoreCase = true) }
                        .coerceAtLeast(0)
            }

    private fun newMeshPacketTo(nodeNum: Int): MeshPacket.Builder = MeshPacket.newBuilder().apply {
        from = 0 // Device sets this to myNodeNum
        to = nodeNum
    }

    private fun newMeshPacketTo(id: String): MeshPacket.Builder = newMeshPacketTo(toNodeNum(id))

    private fun MeshPacket.Builder.buildMeshPacket(
        wantAck: Boolean = false,
        id: Int = generatePacketId(),
        hopLimit: Int = localConfig.lora.hopLimit,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        initFn: MeshProtos.Data.Builder.() -> Unit,
    ): MeshPacket {
        this.wantAck = wantAck
        this.id = id
        this.hopLimit = hopLimit
        this.priority = priority
        this.decoded = MeshProtos.Data.newBuilder().apply(initFn).build()
        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            pkiEncrypted = true
            nodeDBbyNodeNum[to]?.user?.publicKey?.let { this.publicKey = it }
        } else {
            this.channel = channel
        }
        return build()
    }

    private fun MeshPacket.Builder.buildAdminPacket(
        id: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit,
    ): MeshPacket =
        buildMeshPacket(id = id, wantAck = true, channel = adminChannelIndex, priority = MeshPacket.Priority.RELIABLE) {
            this.wantResponse = wantResponse
            this.portnumValue = Portnums.PortNum.ADMIN_APP_VALUE
            this.payload =
                AdminProtos.AdminMessage.newBuilder()
                    .apply {
                        initFn(this)
                        this.sessionPasskey = this@MeshService.sessionPasskey
                    }
                    .build()
                    .toByteString()
        }

    private fun toDataPacket(packet: MeshPacket): DataPacket? {
        if (!packet.hasDecoded()) return null
        val data = packet.decoded
        return DataPacket(
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
        )
    }

    private fun toMeshPacket(dataPacket: DataPacket): MeshPacket = newMeshPacketTo(dataPacket.to!!).buildMeshPacket(
        id = dataPacket.id,
        wantAck = dataPacket.wantAck,
        hopLimit = dataPacket.hopLimit,
        channel = dataPacket.channel,
    ) {
        portnumValue = dataPacket.dataType
        payload = ByteString.copyFrom(dataPacket.bytes)
        dataPacket.replyId?.takeIf { it != 0 }?.let { this.replyId = it }
    }

    private val rememberableDataTypes =
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
        if (dataPacket.dataType !in rememberableDataTypes) return

        val fromLocal = dataPacket.from == DataPacket.ID_LOCAL
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from
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

    // region Received Data Handlers
    private fun handleReceivedData(packet: MeshPacket) {
        val currentMyNodeInfo = myNodeInfo ?: return // Early exit if no local node info

        val decodedData = packet.decoded
        val fromNodeId = toNodeID(packet.from)
        val appDataPacket = toDataPacket(packet) ?: return // Not a processable data packet

        val fromThisDevice = currentMyNodeInfo.myNodeNum == packet.from
        debug("Received data from $fromNodeId, portnum=${decodedData.portnum} ${decodedData.payload.size()} bytes")
        appDataPacket.status = MessageStatus.RECEIVED

        var shouldBroadcastToClients = !fromThisDevice

        when (decodedData.portnumValue) {
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> handleReceivedText(packet, appDataPacket, fromNodeId)

            Portnums.PortNum.ALERT_APP_VALUE -> handleReceivedAlert(appDataPacket, fromNodeId)
            Portnums.PortNum.WAYPOINT_APP_VALUE -> handleReceivedWaypoint(packet, appDataPacket)
            Portnums.PortNum.POSITION_APP_VALUE -> handleReceivedPositionApp(packet, decodedData, appDataPacket)

            Portnums.PortNum.NODEINFO_APP_VALUE -> if (!fromThisDevice) handleReceivedNodeInfoApp(packet, decodedData)

            Portnums.PortNum.TELEMETRY_APP_VALUE -> handleReceivedTelemetryApp(packet, decodedData, appDataPacket)

            Portnums.PortNum.ROUTING_APP_VALUE -> {
                shouldBroadcastToClients = true
                handleReceivedRoutingApp(decodedData, fromNodeId)
            }

            Portnums.PortNum.ADMIN_APP_VALUE -> {
                handleReceivedAdmin(packet.from, AdminProtos.AdminMessage.parseFrom(decodedData.payload))
                shouldBroadcastToClients = false
            }

            Portnums.PortNum.PAXCOUNTER_APP_VALUE -> {
                handleReceivedPaxcounter(packet.from, PaxcountProtos.Paxcount.parseFrom(decodedData.payload))
                shouldBroadcastToClients = false
            }

            Portnums.PortNum.STORE_FORWARD_APP_VALUE -> {
                handleReceivedStoreAndForward(
                    appDataPacket,
                    StoreAndForwardProtos.StoreAndForward.parseFrom(decodedData.payload),
                )
                shouldBroadcastToClients = false
            }

            Portnums.PortNum.RANGE_TEST_APP_VALUE -> handleReceivedRangeTest(appDataPacket)
            Portnums.PortNum.DETECTION_SENSOR_APP_VALUE -> handleReceivedDetectionSensor(appDataPacket)

            Portnums.PortNum.TRACEROUTE_APP_VALUE ->
                radioConfigRepository.setTracerouteResponse(packet.getTracerouteResponse(::getUserName))

            else -> debug("No custom processing needed for ${decodedData.portnumValue}")
        }

        if (shouldBroadcastToClients) {
            serviceBroadcasts.broadcastReceivedData(appDataPacket)
        }
        trackDataReceptionAnalytics(decodedData.portnumValue, decodedData.payload.size())
    }

    private fun handleReceivedText(meshPacket: MeshPacket, dataPacket: DataPacket, fromId: String) {
        val decodedPayload = meshPacket.decoded
        when {
            decodedPayload.replyId != 0 && decodedPayload.emoji == 0 -> { // Text reply
                debug("Received REPLY from $fromId")
                rememberDataPacket(dataPacket)
            }

            decodedPayload.replyId != 0 && decodedPayload.emoji != 0 -> { // Emoji reaction
                debug("Received EMOJI from $fromId")
                rememberReaction(meshPacket)
            }

            else -> { // Standard text message
                debug("Received CLEAR_TEXT from $fromId")
                rememberDataPacket(dataPacket)
            }
        }
    }

    private fun handleReceivedAlert(dataPacket: DataPacket, fromId: String) {
        debug("Received ALERT_APP from $fromId")
        rememberDataPacket(dataPacket)
    }

    private fun handleReceivedWaypoint(meshPacket: MeshPacket, dataPacket: DataPacket) {
        val waypointProto = MeshProtos.Waypoint.parseFrom(meshPacket.decoded.payload)
        // Validate locked Waypoints from the original sender
        if (waypointProto.lockedTo != 0 && waypointProto.lockedTo != meshPacket.from) return
        rememberDataPacket(dataPacket, waypointProto.expire > currentSecond())
    }

    private fun handleReceivedPositionApp(
        meshPacket: MeshPacket,
        decodedData: MeshProtos.Data,
        dataPacket: DataPacket,
    ) {
        val positionProto = MeshProtos.Position.parseFrom(decodedData.payload)
        if (decodedData.wantResponse && positionProto.latitudeI == 0 && positionProto.longitudeI == 0) {
            debug("Ignoring nop position update from position request")
        } else {
            handleReceivedPosition(meshPacket.from, positionProto, dataPacket.time)
        }
    }

    private fun handleReceivedNodeInfoApp(meshPacket: MeshPacket, decodedData: MeshProtos.Data) {
        val userProto =
            MeshProtos.User.parseFrom(decodedData.payload).copy {
                if (isLicensed) clearPublicKey()
                if (meshPacket.viaMqtt) longName = "$longName (MQTT)"
            }
        handleReceivedUser(meshPacket.from, userProto, meshPacket.channel)
    }

    private fun handleReceivedTelemetryApp(
        meshPacket: MeshPacket,
        decodedData: MeshProtos.Data,
        dataPacket: DataPacket,
    ) {
        val telemetryProto =
            TelemetryProtos.Telemetry.parseFrom(decodedData.payload).copy {
                if (time == 0) time = (dataPacket.time / 1000L).toInt()
            }
        handleReceivedTelemetry(meshPacket.from, telemetryProto)
    }

    private fun handleReceivedRoutingApp(decodedData: MeshProtos.Data, fromId: String) {
        val routingProto = MeshProtos.Routing.parseFrom(decodedData.payload)
        if (routingProto.errorReason == MeshProtos.Routing.Error.DUTY_CYCLE_LIMIT) {
            radioConfigRepository.setErrorMessage(getString(R.string.error_duty_cycle))
        }
        handleAckNak(decodedData.requestId, fromId, routingProto.errorReasonValue)
        queueResponse.remove(decodedData.requestId)?.complete(true)
    }

    private fun handleReceivedRangeTest(dataPacket: DataPacket) {
        if (!moduleConfig.rangeTest.enabled) return
        val textDataPacket = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
        rememberDataPacket(textDataPacket)
    }

    private fun handleReceivedDetectionSensor(dataPacket: DataPacket) {
        val textDataPacket = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
        rememberDataPacket(textDataPacket)
    }

    private fun trackDataReceptionAnalytics(portNum: Int, bytesSize: Int) {
        GeeksvilleApplication.analytics.track("num_data_receive", DataPair(1))
        GeeksvilleApplication.analytics.track(
            "data_receive",
            DataPair("num_bytes", bytesSize),
            DataPair("type", portNum),
        )
    }

    // endregion

    @Suppress("NestedBlockDepth")
    private fun handleReceivedAdmin(fromNodeNum: Int, adminMessage: AdminProtos.AdminMessage) {
        when (adminMessage.payloadVariantCase) {
            AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                if (fromNodeNum == myNodeNum) {
                    val response = adminMessage.getConfigResponse
                    debug("Admin: received config ${response.payloadVariantCase}")
                    setLocalConfig(response)
                }
            }

            AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                if (fromNodeNum == myNodeNum) {
                    myNodeInfo?.let {
                        val ch = adminMessage.getChannelResponse
                        debug("Admin: Received channel ${ch.index}")
                        if (ch.index + 1 < it.maxChannels) {
                            handleChannel(ch)
                        }
                    }
                }
            }

            AdminProtos.AdminMessage.PayloadVariantCase.GET_DEVICE_METADATA_RESPONSE -> {
                debug("Admin: received DeviceMetadata from $fromNodeNum")
                serviceScope.handledLaunch {
                    radioConfigRepository.insertMetadata(fromNodeNum, adminMessage.getDeviceMetadataResponse)
                }
            }

            AdminProtos.AdminMessage.PayloadVariantCase.PAYLOADVARIANT_NOT_SET,
            null,
            -> warn("Received admin message with no payload variant set.")

            else -> warn("No special processing needed for admin payload ${adminMessage.payloadVariantCase}")
        }
        debug("Admin: Received session_passkey from $fromNodeNum")
        sessionPasskey = adminMessage.sessionPasskey
    }

    private fun handleReceivedUser(fromNum: Int, userProto: MeshProtos.User, channel: Int = 0) {
        updateNodeInfo(fromNum, channel = channel) { nodeEntity ->
            val isNewNode = (nodeEntity.isUnknownUser && userProto.hwModel != MeshProtos.HardwareModel.UNSET)
            val keyMatch = !nodeEntity.hasPKC || nodeEntity.user.publicKey == userProto.publicKey

            nodeEntity.user =
                if (keyMatch) {
                    userProto
                } else {
                    userProto.copy {
                        warn("Public key mismatch from ${userProto.longName} (${userProto.shortName})")
                        publicKey = NodeEntity.ERROR_BYTE_STRING
                    }
                }
            nodeEntity.longName = userProto.longName
            nodeEntity.shortName = userProto.shortName
            if (isNewNode) {
                serviceNotifications.showNewNodeSeenNotification(nodeEntity)
            }
        }
    }

    private fun handleReceivedPosition(
        fromNum: Int,
        positionProto: MeshProtos.Position,
        defaultTimeMillis: Long = System.currentTimeMillis(),
    ) {
        if (myNodeNum == fromNum && positionProto.latitudeI == 0 && positionProto.longitudeI == 0) {
            debug("Ignoring nop position update for the local node")
            return
        }
        updateNodeInfo(fromNum) {
            debug("update position: ${it.longName?.toPIIString()} with ${positionProto.toPIIString()}")
            it.setPosition(positionProto, (defaultTimeMillis / 1000L).toInt())
        }
    }

    private fun handleReceivedTelemetry(fromNum: Int, telemetryProto: TelemetryProtos.Telemetry) {
        val isRemote = (fromNum != myNodeNum)
        if (!isRemote && telemetryProto.hasLocalStats()) {
            localStatsTelemetry = telemetryProto
            maybeUpdateServiceStatusNotification()
        }
        updateNodeInfo(fromNum) { nodeEntity ->
            when {
                telemetryProto.hasDeviceMetrics() -> {
                    nodeEntity.deviceTelemetry = telemetryProto
                    if (fromNum == myNodeNum || (isRemote && nodeEntity.isFavorite)) {
                        val metrics = telemetryProto.deviceMetrics
                        if (
                            metrics.voltage > batteryPercentUnsupported &&
                            metrics.batteryLevel <= batteryPercentLowThreshold
                        ) {
                            if (shouldBatteryNotificationShow(fromNum, telemetryProto)) {
                                serviceNotifications.showOrUpdateLowBatteryNotification(nodeEntity, isRemote)
                            }
                        } else {
                            batteryPercentCooldowns.remove(fromNum)
                            serviceNotifications.cancelLowBatteryNotification(nodeEntity)
                        }
                    }
                }

                telemetryProto.hasEnvironmentMetrics() -> nodeEntity.environmentTelemetry = telemetryProto

                telemetryProto.hasPowerMetrics() -> nodeEntity.powerTelemetry = telemetryProto
            }
        }
    }

    private fun shouldBatteryNotificationShow(fromNum: Int, telemetry: TelemetryProtos.Telemetry): Boolean {
        val isRemote = (fromNum != myNodeNum)
        val batteryLevel = telemetry.deviceMetrics.batteryLevel
        var shouldDisplay = false
        var forceDisplay = false

        when {
            batteryLevel <= batteryPercentCriticalThreshold -> {
                shouldDisplay = true
                forceDisplay = true
            }

            batteryLevel == batteryPercentLowThreshold -> shouldDisplay = true
            batteryLevel % batteryPercentLowDivisor == 0 && !isRemote -> shouldDisplay = true
            isRemote -> shouldDisplay = true // For remote favorites, show if low
        }

        if (shouldDisplay) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val lastNotificationTime = batteryPercentCooldowns[fromNum] ?: 0L
            if ((nowSeconds - lastNotificationTime) >= batteryPercentCooldownSeconds || forceDisplay) {
                batteryPercentCooldowns[fromNum] = nowSeconds
                return true
            }
        }
        return false
    }

    private fun handleReceivedPaxcounter(fromNum: Int, paxcountProto: PaxcountProtos.Paxcount) {
        updateNodeInfo(fromNum) { it.paxcounter = paxcountProto }
    }

    private fun handleReceivedStoreAndForward(
        dataPacket: DataPacket,
        storeAndForwardProto: StoreAndForwardProtos.StoreAndForward,
    ) {
        debug("StoreAndForward: ${storeAndForwardProto.variantCase} ${storeAndForwardProto.rr} from ${dataPacket.from}")
        when (storeAndForwardProto.variantCase) {
            StoreAndForwardProtos.StoreAndForward.VariantCase.STATS -> {
                val textPacket =
                    dataPacket.copy(
                        bytes = storeAndForwardProto.stats.toString().encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(textPacket)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY -> {
                val text =
                    """
                    Total messages: ${storeAndForwardProto.history.historyMessages}
                    History window: ${storeAndForwardProto.history.window / 60000} min
                    Last request: ${storeAndForwardProto.history.lastRequest}
                """
                        .trimIndent()
                val textPacket =
                    dataPacket.copy(
                        bytes = text.encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(textPacket)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.TEXT -> {
                var actualTo = dataPacket.to
                if (
                    storeAndForwardProto.rr ==
                    StoreAndForwardProtos.StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST
                ) {
                    actualTo = DataPacket.ID_BROADCAST
                }
                val textPacket =
                    dataPacket.copy(
                        to = actualTo,
                        bytes = storeAndForwardProto.text.toByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(textPacket)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.VARIANT_NOT_SET,
            null,
            -> Unit

            StoreAndForwardProtos.StoreAndForward.VariantCase.HEARTBEAT -> {}
        }
    }

    private val offlineSentPackets = mutableListOf<DataPacket>()

    private fun handleReceivedMeshPacket(packet: MeshPacket) {
        val processedPacket =
            packet
                .toBuilder()
                .apply {
                    if (rxTime == 0) setRxTime(currentSecond()) // Ensure rxTime is set
                }
                .build()
        processReceivedMeshPacketInternal(processedPacket)
        onNodeDBChanged()
    }

    private val queuedPackets = ConcurrentLinkedQueue<MeshPacket>()
    private val queueResponse = ConcurrentHashMap<Int, CompletableFuture<Boolean>>()
    private var queueJob: Job? = null

    private fun sendPacket(packet: MeshPacket): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        queueResponse[packet.id] = future
        try {
            if (connectionRouter.connectionState.value != ConnectionState.CONNECTED) {
                throw RadioNotConnectedException("Cannot send packet, radio not connected.")
            }
            sendToRadio(ToRadio.newBuilder().setPacket(packet))
        } catch (ex: Exception) {
            errormsg("sendToRadio error:", ex)
            queueResponse.remove(packet.id) // Clean up if send failed immediately
            future.completeExceptionally(ex) // Complete with exception
        }
        return future
    }

    private fun startPacketQueue() {
        if (queueJob?.isActive == true) return
        queueJob =
            serviceScope.handledLaunch {
                debug("Packet queueJob started")
                while (
                    connectionRouter.connectionState.value == ConnectionState.CONNECTED && queuedPackets.isNotEmpty()
                ) {
                    val packet = queuedPackets.poll() ?: break // Should not be null if loop condition met
                    try {
                        debug("Queue: Sending packet id=${packet.id.toUInt()}")
                        val success = sendPacket(packet).get(2, TimeUnit.MINUTES)
                        debug("Queue: Packet id=${packet.id.toUInt()} sent, success=$success")
                    } catch (e: TimeoutException) {
                        debug("Queue: Packet id=${packet.id.toUInt()} timed out: ${e.message}")
                        queueResponse.remove(packet.id)?.complete(false)
                    } catch (e: Exception) {
                        debug("Queue: Packet id=${packet.id.toUInt()} failed: ${e.message}")
                        queueResponse.remove(packet.id)?.complete(false)
                    }
                }
                debug("Packet queueJob finished or radio disconnected")
            }
    }

    private fun stopPacketQueue() {
        queueJob
            ?.takeIf { it.isActive }
            ?.let {
                info("Stopping packet queueJob")
                it.cancel()
                queueJob = null
                queuedPackets.clear()
                queueResponse.values.forEach { future -> if (!future.isDone) future.complete(false) }
                queueResponse.clear()
            }
    }

    private fun sendNow(dataPacket: DataPacket) {
        val meshPacket = toMeshPacket(dataPacket)
        dataPacket.time = System.currentTimeMillis() // Update time to actual send time
        sendToRadio(meshPacket)
    }

    private fun processQueuedPackets() {
        val packetsToSend = ArrayList(offlineSentPackets) // Avoid ConcurrentModificationException
        offlineSentPackets.clear()

        packetsToSend.forEach { p ->
            try {
                sendNow(p)
            } catch (ex: Exception) {
                errormsg("Error sending queued message, re-queuing:", ex)
                offlineSentPackets.add(p) // Re-queue if sending failed
            }
        }
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1000L) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null && isActive) { // check coroutine isActive
            dataPacket = packetRepository.get().getPacketById(packetId)?.data
            if (dataPacket == null) delay(100L)
        }
        dataPacket
    }

    private fun changeStatus(packetId: Int, status: MessageStatus) = serviceScope.handledLaunch {
        if (packetId == 0) return@handledLaunch // Ignore packets with no ID

        getDataPacketById(packetId)?.let { p ->
            if (p.status == status) return@handledLaunch
            packetRepository.get().updateMessageStatus(p, status)
            serviceBroadcasts.broadcastMessageStatus(packetId, status)
        }
    }

    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int) {
        serviceScope.handledLaunch {
            val isAck = routingError == MeshProtos.Routing.Error.NONE_VALUE
            val packetEntity = packetRepository.get().getPacketById(requestId)

            packetEntity?.data?.let { dataPacket ->
                // Distinguish real ACKs coming from the intended receiver
                val newStatus =
                    when {
                        isAck && fromId == dataPacket.to -> MessageStatus.RECEIVED
                        isAck -> MessageStatus.DELIVERED
                        else -> MessageStatus.ERROR
                    }
                if (dataPacket.status != MessageStatus.RECEIVED) { // Don't override final RECEIVED
                    dataPacket.status = newStatus
                    packetRepository.get().update(packetEntity.copy(routingError = routingError, data = dataPacket))
                }
                serviceBroadcasts.broadcastMessageStatus(requestId, newStatus)
            }
        }
    }

    private fun processReceivedMeshPacketInternal(packet: MeshPacket) {
        if (!packet.hasDecoded()) return

        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "PacketReceived", // Clarified type
                received_date = System.currentTimeMillis(),
                raw_message = packet.toString(),
                fromNum = packet.from,
                portNum = packet.decoded.portnumValue,
                fromRadio = fromRadio { this.packet = packet },
            ),
        )
        serviceScope.handledLaunch { radioConfigRepository.emitMeshPacket(packet) }

        val isOtherNode = myNodeNum != packet.from
        // Update our own node's lastHeard as we are clearly active to receive this
        updateNodeInfo(myNodeNum, withBroadcast = isOtherNode) { it.lastHeard = currentSecond() }

        updateNodeInfo(packet.from, withBroadcast = false, channel = packet.channel) {
            it.lastHeard = packet.rxTime
            it.snr = packet.rxSnr
            it.rssi = packet.rxRssi
            it.hopsAway =
                if (packet.hopStart == 0 || packet.hopLimit > packet.hopStart) {
                    -1 // Unknown or direct
                } else {
                    packet.hopStart - packet.hopLimit
                }
        }
        handleReceivedData(packet)
    }

    private fun insertMeshLog(meshLog: MeshLog) {
        serviceScope.handledLaunch { meshLogRepository.get().insert(meshLog) }
    }

    private fun setLocalConfig(config: ConfigProtos.Config) {
        serviceScope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
    }

    private fun setLocalModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        serviceScope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
    }

    private fun updateChannelSettings(channel: ChannelProtos.Channel) =
        serviceScope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }

    private fun currentSecond() = (System.currentTimeMillis() / 1000).toInt()

    private fun onNodeDBChanged() {
        maybeUpdateServiceStatusNotification()
    }

    private fun reportConnection() {
        val radioModel = DataPair("radio_model", myNodeInfo?.model ?: "unknown")
        GeeksvilleApplication.analytics.track(
            "mesh_connect",
            DataPair("num_nodes", numNodes),
            DataPair("num_online", numOnlineNodes),
            radioModel,
        )
        GeeksvilleApplication.analytics.setUserInfo(DataPair("num_nodes", numNodes), radioModel)
    }

    private var connectTimeMsec = 0L

    private fun startConnect() {
        try {
            connectTimeMsec = System.currentTimeMillis()
            sendConfigOnlyRequest()
        } catch (ex: Exception) {
            when (ex) {
                is InvalidProtocolBufferException,
                is RadioNotConnectedException,
                is RemoteException,
                -> {
                    errormsg("Failed to start connection sequence: ${ex.message}", ex)
                }

                else -> throw ex
            }
        }
    }

    private fun startDeviceSleep() {
        stopPacketQueue()
        stopLocationRequests()
        stopMqttClientProxy()

        if (connectTimeMsec != 0L) {
            val now = System.currentTimeMillis()
            GeeksvilleApplication.analytics.track("connected_seconds", DataPair((now - connectTimeMsec) / 1000.0))
            connectTimeMsec = 0L
        }
        serviceBroadcasts.broadcastConnection()
    }

    private fun startDisconnect() {
        stopPacketQueue()
        stopLocationRequests()
        stopMqttClientProxy()

        GeeksvilleApplication.analytics.track(
            "mesh_disconnect",
            DataPair("num_nodes", numNodes),
            DataPair("num_online", numOnlineNodes),
        )
        GeeksvilleApplication.analytics.track("num_nodes", DataPair(numNodes))
        serviceBroadcasts.broadcastConnection()
    }

    private fun maybeUpdateServiceStatusNotification() {
        val currentSummary = notificationSummary
        val currentStats = localStats
        val currentStatsUpdatedAtMillis = localStatsUpdatedAtMillis

        val summaryChanged = currentSummary.isNotBlank() && previousSummary != currentSummary
        val statsChanged = currentStats != null && previousStats != currentStats

        if (summaryChanged || statsChanged) {
            previousSummary = currentSummary
            previousStats = currentStats
            serviceNotifications.updateServiceStateNotification(
                summaryString = currentSummary,
                localStats = currentStats,
                currentStatsUpdatedAtMillis = currentStatsUpdatedAtMillis,
            )
        }
    }

    @SuppressLint("CheckResult")
    @Suppress("CyclomaticComplexMethod")
    private fun onReceiveFromRadio(bytes: ByteArray) {
        try {
            val proto = MeshProtos.FromRadio.parseFrom(bytes)
            when (proto.payloadVariantCase) {
                MeshProtos.FromRadio.PayloadVariantCase.PACKET -> handleReceivedMeshPacket(proto.packet)
                MeshProtos.FromRadio.PayloadVariantCase.CONFIG_COMPLETE_ID ->
                    handleConfigComplete(proto.configCompleteId)

                MeshProtos.FromRadio.PayloadVariantCase.MY_INFO -> handleMyInfo(proto.myInfo)
                MeshProtos.FromRadio.PayloadVariantCase.NODE_INFO -> handleNodeInfo(proto.nodeInfo)
                MeshProtos.FromRadio.PayloadVariantCase.CHANNEL -> handleChannel(proto.channel)
                MeshProtos.FromRadio.PayloadVariantCase.CONFIG -> handleDeviceConfig(proto.config)
                MeshProtos.FromRadio.PayloadVariantCase.MODULECONFIG -> handleModuleConfig(proto.moduleConfig)
                MeshProtos.FromRadio.PayloadVariantCase.QUEUESTATUS -> handleQueueStatus(proto.queueStatus)
                MeshProtos.FromRadio.PayloadVariantCase.METADATA -> handleMetadata(proto.metadata)
                MeshProtos.FromRadio.PayloadVariantCase.MQTTCLIENTPROXYMESSAGE ->
                    handleMqttProxyMessage(proto.mqttClientProxyMessage)

                MeshProtos.FromRadio.PayloadVariantCase.DEVICEUICONFIG -> handleDeviceUiConfig(proto.deviceuiConfig)

                MeshProtos.FromRadio.PayloadVariantCase.FILEINFO -> handleFileInfo(proto.fileInfo)

                MeshProtos.FromRadio.PayloadVariantCase.CLIENTNOTIFICATION ->
                    handleClientNotification(proto.clientNotification)

                MeshProtos.FromRadio.PayloadVariantCase.LOG_RECORD -> {}
                MeshProtos.FromRadio.PayloadVariantCase.REBOOTED -> {}
                MeshProtos.FromRadio.PayloadVariantCase.XMODEMPACKET -> {}
                MeshProtos.FromRadio.PayloadVariantCase.PAYLOADVARIANT_NOT_SET,
                null,
                -> errormsg("Unexpected FromRadio variant")
            }
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("Invalid Protobuf from radio, len=${bytes.size}", ex)
        }
    }

    private fun handleDeviceConfig(config: ConfigProtos.Config) {
        debug("Received config ${config.toOneLineString()}")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Config ${config.payloadVariantCase}",
                received_date = System.currentTimeMillis(),
                raw_message = config.toString(),
                fromRadio = fromRadio { this.config = config },
            ),
        )
        setLocalConfig(config)
        val configCount = localConfig.allFields.size
        radioConfigRepository.setStatusMessage("Device config ($configCount / $configTotal)")
    }

    private fun handleModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        debug("Received moduleConfig ${config.toOneLineString()}")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ModuleConfig ${config.payloadVariantCase}",
                received_date = System.currentTimeMillis(),
                raw_message = config.toString(),
                fromRadio = fromRadio { this.moduleConfig = config },
            ),
        )
        setLocalModuleConfig(config)
        val moduleCount = moduleConfig.allFields.size
        radioConfigRepository.setStatusMessage("Module config ($moduleCount / $moduleTotal)")
    }

    private fun handleQueueStatus(queueStatus: MeshProtos.QueueStatus) {
        debug("queueStatus ${queueStatus.toOneLineString()}")
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, meshPacketId) }
        if (success && isFull) return // Queue is full, wait for next update

        val future =
            if (requestId != 0) {
                queueResponse.remove(requestId)
            } else {
                // This is a bit of a guess, but for now we assume it's for the last request that isn't done.
                // A more robust solution would involve matching something other than packetId.
                queueResponse.entries.lastOrNull { !it.value.isDone }?.also { queueResponse.remove(it.key) }?.value
            }
        future?.complete(success)
    }

    private fun handleChannel(ch: ChannelProtos.Channel) {
        debug("Received channel ${ch.index}")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Channel",
                received_date = System.currentTimeMillis(),
                raw_message = ch.toString(),
                fromRadio = fromRadio { channel = ch },
            ),
        )
        if (ch.role != ChannelProtos.Channel.Role.DISABLED) updateChannelSettings(ch)
        val maxChannels = myNodeInfo?.maxChannels ?: 8
        radioConfigRepository.setStatusMessage("Channels (${ch.index + 1} / $maxChannels)")
    }

    private fun installNodeInfo(info: MeshProtos.NodeInfo) {
        updateNodeInfo(info.num) {
            if (info.hasUser()) {
                it.user =
                    info.user.copy {
                        if (isLicensed) clearPublicKey()
                        if (info.viaMqtt) longName = "$longName (MQTT)"
                    }
                it.longName = it.user.longName
                it.shortName = it.user.shortName
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
            it.hopsAway = if (info.hasHopsAway()) info.hopsAway else -1
            it.isFavorite = info.isFavorite
            it.isIgnored = info.isIgnored
        }
    }

    private fun handleNodeInfo(info: MeshProtos.NodeInfo) {
        debug(
            "Received nodeinfo num=${info.num}, hasUser=${info.hasUser()}, " +
                "hasPosition=${info.hasPosition()}, hasDeviceMetrics=${info.hasDeviceMetrics()}",
        )
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "NodeInfo",
                received_date = System.currentTimeMillis(),
                raw_message = info.toString(),
                fromRadio = fromRadio { nodeInfo = info },
            ),
        )

        installNodeInfo(info)
        onNodeDBChanged()
        radioConfigRepository.setStatusMessage("Nodes ($numNodes)")
    }

    private fun regenMyNodeInfo(metadata: MeshProtos.DeviceMetadata) {
        val myInfo = rawMyNodeInfo
        if (myInfo != null) {
            val mi =
                with(myInfo) {
                    MyNodeEntity(
                        myNodeNum = myNodeNum,
                        model =
                        when (val hwModel = metadata.hwModel) {
                            null,
                            MeshProtos.HardwareModel.UNSET,
                            -> null

                            else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                        },
                        firmwareVersion = metadata.firmwareVersion,
                        couldUpdate = false,
                        shouldUpdate = false, // TODO add check after re-implementing firmware updates
                        currentPacketId = currentPacketId and 0xffffffffL,
                        messageTimeoutMsec = 5 * 60 * 1000, // constants from current firmware code
                        minAppVersion = minAppVersion,
                        maxChannels = 8,
                        hasWifi = metadata.hasWifi,
                        deviceId = deviceId.toStringUtf8(),
                    )
                }
            serviceScope.handledLaunch {
                radioConfigRepository.installMyNodeInfo(mi)
                radioConfigRepository.insertMetadata(mi.myNodeNum, metadata)
            }
            myNodeInfo = mi
            onConnected()
        }
    }

    private fun sendAnalytics() {
        myNodeInfo?.let {
            GeeksvilleApplication.analytics.setUserInfo(
                DataPair("firmware", it.firmwareVersion),
                DataPair("hw_model", it.model),
            )
        }
    }

    private fun handleMyInfo(myInfo: MeshProtos.MyNodeInfo) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "MyNodeInfo",
                received_date = System.currentTimeMillis(),
                raw_message = myInfo.toString(),
                fromRadio = fromRadio { this.myInfo = myInfo },
            ),
        )
        rawMyNodeInfo = myInfo
    }

    private fun handleDeviceUiConfig(deviceuiConfig: DeviceUIProtos.DeviceUIConfig) {
        debug("Received DeviceUIConfig ${deviceuiConfig.toOneLineString()}")
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

    private fun handleFileInfo(fileInfo: MeshProtos.FileInfo) {
        debug("Received FileInfo ${fileInfo.toOneLineString()}")
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

    private fun handleMetadata(metadata: MeshProtos.DeviceMetadata) {
        debug("Received deviceMetadata ${metadata.toOneLineString()}")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "DeviceMetadata",
                received_date = System.currentTimeMillis(),
                raw_message = metadata.toString(),
                fromRadio = fromRadio { this.metadata = metadata },
            ),
        )
        regenMyNodeInfo(metadata)
    }

    private fun handleMqttProxyMessage(message: MeshProtos.MqttClientProxyMessage) {
        with(message) {
            when (payloadVariantCase) {
                MeshProtos.MqttClientProxyMessage.PayloadVariantCase.TEXT ->
                    mqttRepository.publish(topic, text.encodeToByteArray(), retained)

                MeshProtos.MqttClientProxyMessage.PayloadVariantCase.DATA ->
                    mqttRepository.publish(topic, data.toByteArray(), retained)

                else -> Unit
            }
        }
    }

    private fun handleClientNotification(notification: MeshProtos.ClientNotification) {
        debug("Received clientNotification ${notification.toOneLineString()}")
        radioConfigRepository.setClientNotification(notification)
        serviceNotifications.showClientNotification(notification)
        queueResponse.remove(notification.replyId)?.complete(false)
    }

    private fun startMqttClientProxy() {
        if (mqttMessageFlow?.isActive == true) return
        if (moduleConfig.mqtt.enabled && moduleConfig.mqtt.proxyToClientEnabled) {
            mqttMessageFlow =
                mqttRepository.proxyMessageFlow
                    .onEach { message -> sendToRadio(ToRadio.newBuilder().setMqttClientProxyMessage(message)) }
                    .catch { throwable -> radioConfigRepository.setErrorMessage("MqttClientProxy failed: $throwable") }
                    .launchIn(serviceScope)
        }
    }

    private fun stopMqttClientProxy() {
        mqttMessageFlow
            ?.takeIf { it.isActive }
            ?.let {
                info("Stopping MqttClientProxy")
                it.cancel()
                mqttMessageFlow = null
            }
    }

    private fun onConnected() {
        // Start sending queued packets and other tasks
        processQueuedPackets()
        startMqttClientProxy()
        onNodeDBChanged()
        serviceBroadcasts.broadcastConnection()
        sendAnalytics()
        reportConnection()
        sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { setTimeOnly = currentSecond() })
    }

    private fun handleConfigComplete(configCompleteId: Int) {
        when (configCompleteId) {
            CONFIG_ONLY_NONCE -> handleConfigOnlyNonceResponse()
            NODE_INFO_ONLY_NONCE -> handleNodeInfoNonceResponse()
            else -> warn("Received unexpected config complete id $configCompleteId")
        }
    }

    private fun handleConfigOnlyNonceResponse() {
        debug("Received config only complete for nonce $CONFIG_ONLY_NONCE")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "ConfigOnlyComplete",
                received_date = System.currentTimeMillis(),
                raw_message = CONFIG_ONLY_NONCE.toString(),
                fromRadio = fromRadio { this.configCompleteId = CONFIG_ONLY_NONCE },
            ),
        )
        // we have recieved the response to our ConfigOnly request
        // send a heartbeat, then request NodeInfoOnly to get the nodeDb from the radio
        serviceScope.handledLaunch { radioInterfaceService.keepAlive() }
        sendNodeInfoOnlyRequest()
    }

    private fun handleNodeInfoNonceResponse() {
        debug("Received node info complete for nonce $NODE_INFO_ONLY_NONCE")
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "NodeInfoComplete",
                received_date = System.currentTimeMillis(),
                raw_message = NODE_INFO_ONLY_NONCE.toString(),
                fromRadio = fromRadio { this.configCompleteId = NODE_INFO_ONLY_NONCE },
            ),
        )
    }

    private fun sendConfigOnlyRequest() {
        resetState()
        debug("Starting config only with nonce=$CONFIG_ONLY_NONCE")
        sendToRadio(ToRadio.newBuilder().setWantConfigId(CONFIG_ONLY_NONCE))
    }

    private fun sendNodeInfoOnlyRequest() {
        debug("Starting node info with nonce=$NODE_INFO_ONLY_NONCE")
        sendToRadio(ToRadio.newBuilder().setWantConfigId(NODE_INFO_ONLY_NONCE))
    }

    private fun sendPosition(position: MeshProtos.Position, destNum: Int? = null, wantResponse: Boolean = false) {
        try {
            myNodeInfo?.let { mi ->
                val targetNodeNum = destNum ?: mi.myNodeNum
                debug("Sending our position/time to=$targetNodeNum ${Position(position)}")

                if (!localConfig.position.fixedPosition) {
                    handleReceivedPosition(mi.myNodeNum, position)
                }

                sendToRadio(
                    newMeshPacketTo(targetNodeNum).buildMeshPacket(
                        channel = if (destNum == null) 0 else (nodeDBbyNodeNum[destNum]?.channel ?: 0),
                        priority = MeshPacket.Priority.BACKGROUND,
                    ) {
                        portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                        payload = position.toByteString()
                        this.wantResponse = wantResponse
                    },
                )
            }
        } catch (ex: BLEException) {
            warn("Ignoring disconnected radio during gps location update: ${ex.message}")
        }
    }

    private fun setOwner(packetId: Int, user: MeshProtos.User) {
        val dest = nodeDBbyID[user.id] ?: throw Exception("Can't set user without a NodeInfo")
        if (user == dest.user) {
            debug("Ignoring nop owner change")
            return
        }

        debug("setOwner Id: ${user.id} longName: ${user.longName.anonymize} shortName: ${user.shortName}")
        handleReceivedUser(dest.num, user)
        sendToRadio(newMeshPacketTo(dest.num).buildAdminPacket(id = packetId) { setOwner = user })
    }

    private val packetIdGenerator = AtomicLong(Random().nextLong())

    private fun generatePacketId(): Int {
        // We need a 32 bit unsigned integer, but since Java doesn't have unsigned,
        // we can use a long and mask it. To ensure it's never 0, we add 1 after masking.
        return (packetIdGenerator.incrementAndGet() and 0xFFFFFFFFL).toInt().let { if (it == 0) 1 else it }
    }

    private fun enqueueForSending(p: DataPacket) {
        if (p.dataType in rememberableDataTypes) {
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
                is ServiceAction.AddSharedContact -> importContact(action.contact)
            }
        }
    }

    private fun importContact(contact: AdminProtos.SharedContact) {
        sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { addContact = contact })
        handleReceivedUser(contact.nodeNum, contact.user)
    }

    private fun getDeviceMetadata(destNum: Int) = toRemoteExceptions {
        sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(wantResponse = true) { getDeviceMetadataRequest = true })
    }

    private fun favoriteNode(node: Node) = toRemoteExceptions {
        sendToRadio(
            newMeshPacketTo(myNodeNum).buildAdminPacket {
                if (node.isFavorite) {
                    debug("removing node ${node.num} from favorite list")
                    removeFavoriteNode = node.num
                } else {
                    debug("adding node ${node.num} to favorite list")
                    setFavoriteNode = node.num
                }
            },
        )
        updateNodeInfo(node.num) { it.isFavorite = !node.isFavorite }
    }

    private fun ignoreNode(node: Node) = toRemoteExceptions {
        sendToRadio(
            newMeshPacketTo(myNodeNum).buildAdminPacket {
                if (node.isIgnored) {
                    debug("removing node ${node.num} from ignore list")
                    removeIgnoredNode = node.num
                } else {
                    debug("adding node ${node.num} to ignore list")
                    setIgnoredNode = node.num
                }
            },
        )
        updateNodeInfo(node.num) { it.isIgnored = !node.isIgnored }
    }

    private fun sendReaction(reaction: ServiceAction.Reaction) = toRemoteExceptions {
        val channel = reaction.contactKey[0].digitToInt()
        val destId = reaction.contactKey.substring(1)

        val packet =
            newMeshPacketTo(destId).buildMeshPacket(channel = channel, priority = MeshPacket.Priority.BACKGROUND) {
                emoji = 1
                replyId = reaction.replyId
                portnumValue = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                payload = ByteString.copyFrom(reaction.emoji.encodeToByteArray())
            }
        sendToRadio(packet)
        rememberReaction(packet.toBuilder().setFrom(myNodeNum).build())
    }

    private val _lastAddress: MutableStateFlow<String?> = MutableStateFlow(null)
    val lastAddress: StateFlow<String?>
        get() = _lastAddress.asStateFlow()

    lateinit var sharedPreferences: SharedPreferences

    fun clearDatabases() = serviceScope.handledLaunch {
        debug("Clearing nodeDB")
        radioConfigRepository.clearNodeDB()
    }

    private fun updateLastAddress(deviceAddr: String?) {
        val currentAddr = lastAddress.value
        debug("setDeviceAddress: New: ${deviceAddr.anonymize}, Old: ${currentAddr.anonymize}")

        if (deviceAddr != currentAddr) {
            _lastAddress.value = deviceAddr ?: NO_DEVICE_SELECTED
            sharedPreferences.edit { putString(DEVICE_ADDRESS_KEY, deviceAddr) }
            clearNotifications()
            clearDatabases()
            resetState()
        }
    }

    private fun clearNotifications() {
        serviceNotifications.clearNotifications()
    }

    private val binder =
        object : IMeshService.Stub() {
            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                debug("Passing through device change to radio service: ${deviceAddr.anonymize}")
                updateLastAddress(deviceAddr)
                sharedPreferences.edit { putString("device_address", deviceAddr) }
                connectionRouter.setDeviceAddress(deviceAddr)
            }

            override fun subscribeReceiver(packageName: String, receiverName: String) = toRemoteExceptions {
                clientPackages[receiverName] = packageName
            }

            override fun getUpdateStatus(): Int = -4 // ProgressNotStarted (DEPRECATED)

            override fun startFirmwareUpdate() = toRemoteExceptions {}

            override fun getMyNodeInfo(): MyNodeInfo? = this@MeshService.myNodeInfo?.toMyNodeInfo()

            override fun getMyId(): String = toRemoteExceptions { myNodeID }

            override fun getPacketId(): Int = toRemoteExceptions { generatePacketId() }

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
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) { getOwnerRequest = true },
                )
            }

            override fun send(p: DataPacket) {
                toRemoteExceptions {
                    if (p.id == 0) p.id = generatePacketId()
                    info(
                        "sendData dest=${p.to}, id=${p.id} <- ${p.bytes?.size} bytes " +
                            "(connectionState=${connectionRouter.connectionState.value})",
                    )

                    if (p.dataType == 0) throw InvalidProtocolBufferException("Port numbers must be non-zero")
                    if ((p.bytes?.size ?: 0) >= MeshProtos.Constants.DATA_PAYLOAD_LEN_VALUE) {
                        p.status = MessageStatus.ERROR
                        throw RemoteException("Message too long")
                    } else {
                        p.status = MessageStatus.QUEUED
                    }

                    if (connectionRouter.connectionState.value == ConnectionState.CONNECTED) {
                        try {
                            sendNow(p)
                        } catch (ex: Exception) {
                            errormsg("Error sending message, so enqueueing", ex)
                            enqueueForSending(p)
                        }
                    } else {
                        enqueueForSending(p)
                    }
                    serviceBroadcasts.broadcastMessageStatus(p)
                    rememberDataPacket(p, false)

                    GeeksvilleApplication.analytics.track(
                        "data_send",
                        DataPair("num_bytes", p.bytes?.size),
                        DataPair("type", p.dataType),
                    )
                    GeeksvilleApplication.analytics.track("num_data_sent", DataPair(1))
                }
            }

            override fun getConfig(): ByteArray = toRemoteExceptions {
                this@MeshService.localConfig.toByteArray() ?: throw NoDeviceConfigException()
            }

            override fun setConfig(payload: ByteArray) = toRemoteExceptions {
                setRemoteConfig(generatePacketId(), myNodeNum, payload)
            }

            override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                debug("Setting new radio config!")
                val config = ConfigProtos.Config.parseFrom(payload)
                sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setConfig = config })
                if (num == myNodeNum) setLocalConfig(config)
            }

            override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getConfigRequestValue = config
                    },
                )
            }

            override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                debug("Setting new module config!")
                val config = ModuleConfigProtos.ModuleConfig.parseFrom(payload)
                sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setModuleConfig = config })
                if (num == myNodeNum) setLocalModuleConfig(config)
            }

            override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getModuleConfigRequestValue = config
                    },
                )
            }

            override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket { setRingtoneMessage = ringtone })
            }

            override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getRingtoneRequest = true
                    },
                )
            }

            override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket { setCannedMessageModuleMessages = messages })
            }

            override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getCannedMessageModuleMessagesRequest = true
                    },
                )
            }

            override fun setChannel(payload: ByteArray) = toRemoteExceptions {
                setRemoteChannel(generatePacketId(), myNodeNum, payload)
            }

            override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                val channel = ChannelProtos.Channel.parseFrom(payload)
                sendToRadio(newMeshPacketTo(num).buildAdminPacket(id = id) { setChannel = channel })
            }

            override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket(id = id, wantResponse = true) {
                        getChannelRequest = index + 1 // API is 1-based
                    },
                )
            }

            override fun beginEditSettings() = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { beginEditSettings = true })
            }

            override fun commitEditSettings() = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { commitEditSettings = true })
            }

            override fun getChannelSet(): ByteArray = toRemoteExceptions { this@MeshService.channelSet.toByteArray() }

            override fun getNodes(): MutableList<NodeInfo> = toRemoteExceptions {
                nodeDBbyNodeNum.values.map { it.toNodeInfo() }.toMutableList()
            }

            override fun connectionState(): String = toRemoteExceptions {
                this@MeshService.connectionRouter.connectionState.value.toString()
            }

            override fun startProvideLocation() = toRemoteExceptions {
                @SuppressLint("MissingPermission")
                startLocationRequests()
            }

            override fun stopProvideLocation() = toRemoteExceptions { stopLocationRequests() }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                nodeDBbyNodeNum.remove(nodeNum)?.let { removedNode ->
                    if (removedNode.user.id.isNotEmpty()) {
                        _nodeDBbyID.remove(removedNode.user.id)
                    }
                }
                sendToRadio(newMeshPacketTo(myNodeNum).buildAdminPacket { this.removeByNodenum = nodeNum })
            }

            override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    sendToRadio(
                        newMeshPacketTo(destNum).buildMeshPacket(channel = nodeDBbyNodeNum[destNum]?.channel ?: 0) {
                            portnumValue = Portnums.PortNum.NODEINFO_APP_VALUE
                            wantResponse = true
                            payload = nodeDBbyNodeNum[myNodeNum]?.user?.toByteString() ?: ByteString.EMPTY
                        },
                    )
                }
            }

            override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
                if (destNum == myNodeNum) return@toRemoteExceptions

                val provideLocation = sharedPreferences.getBoolean("provide-location-$myNodeNum", false)
                val currentPosition =
                    when {
                        provideLocation && position.isValid() -> position
                        else -> nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
                    }

                if (currentPosition == null) {
                    debug("Position request skipped - no valid position available")
                    return@toRemoteExceptions
                }

                val meshPosition = position {
                    latitudeI = Position.degI(currentPosition.latitude)
                    longitudeI = Position.degI(currentPosition.longitude)
                    altitude = currentPosition.altitude
                    time = currentSecond()
                }

                sendToRadio(
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

            override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
                val pos = position {
                    latitudeI = Position.degI(position.latitude)
                    longitudeI = Position.degI(position.longitude)
                    altitude = position.altitude
                }
                sendToRadio(
                    newMeshPacketTo(destNum).buildAdminPacket {
                        if (position.latitude != 0.0 || position.longitude != 0.0 || position.altitude != 0) {
                            setFixedPosition = pos
                        } else {
                            removeFixedPosition = true
                        }
                    },
                )
                updateNodeInfo(destNum) { it.setPosition(pos, currentSecond()) }
            }

            override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(
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
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { shutdownSeconds = 5 })
            }

            override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { rebootSeconds = 5 })
            }

            override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { factoryResetDevice = 1 })
            }

            override fun requestNodedbReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                sendToRadio(newMeshPacketTo(destNum).buildAdminPacket(id = requestId) { nodedbReset = 1 })
            }
        }
}
