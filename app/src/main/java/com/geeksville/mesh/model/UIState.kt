package com.geeksville.mesh.model

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.RemoteException
import android.view.Menu
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.*
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.repository.datastore.ChannelSetRepository
import com.geeksville.mesh.repository.datastore.LocalConfigRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.util.GPSFormat
import com.geeksville.mesh.util.positionToMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/// Given a human name, strip out the first letter of the first three words and return that as the initials for
/// that user. If the original name is only one word, strip vowels from the original name and if the result is
/// 3 or more characters, use the first three characters. If not, just take the first 3 characters of the
/// original name.
fun getInitials(nameIn: String): String {
    val nchars = 3
    val minchars = 2
    val name = nameIn.trim()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials = when (words.size) {
        in 0 until minchars -> {
            val nm = if (name.isNotEmpty())
                name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
            else
                ""
            if (nm.length >= nchars) nm else name
        }
        else -> words.map { it.first() }.joinToString("")
    }
    return initials.take(nchars)
}

@HiltViewModel
class UIViewModel @Inject constructor(
    private val app: Application,
    private val meshLogRepository: MeshLogRepository,
    private val channelSetRepository: ChannelSetRepository,
    private val packetRepository: PacketRepository,
    private val localConfigRepository: LocalConfigRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val preferences: SharedPreferences
) : ViewModel(), Logging {

    private val _meshLog = MutableStateFlow<List<MeshLog>>(emptyList())
    val meshLog: StateFlow<List<MeshLog>> = _meshLog

    private val _packets = MutableStateFlow<List<Packet>>(emptyList())
    val packets: StateFlow<List<Packet>> = _packets

    private val _localConfig = MutableStateFlow<LocalConfig>(LocalConfig.getDefaultInstance())
    val localConfig: StateFlow<LocalConfig> = _localConfig
    val config get() = _localConfig.value

    private val _channels = MutableStateFlow(ChannelSet())
    val channels: StateFlow<ChannelSet> = _channels

    private val _quickChatActions = MutableStateFlow<List<QuickChatAction>>(emptyList())
    val quickChatActions: StateFlow<List<QuickChatAction>> = _quickChatActions

    init {
        viewModelScope.launch {
            meshLogRepository.getAllLogs().collect { logs ->
                _meshLog.value = logs
            }
        }
        viewModelScope.launch {
            packetRepository.getAllPackets().collect { packets ->
                _packets.value = packets
            }
        }
        viewModelScope.launch {
            localConfigRepository.localConfigFlow.collect { config ->
                _localConfig.value = config
            }
        }
        viewModelScope.launch {
            quickChatActionRepository.getAllActions().collect { actions ->
                _quickChatActions.value = actions
            }
        }
        viewModelScope.launch {
            channelSetRepository.channelSetFlow.collect { channelSet ->
                _channels.value = ChannelSet(channelSet)
            }
        }
        debug("ViewModel created")
    }

    private val contactKey: MutableStateFlow<String> = MutableStateFlow(DataPacket.ID_BROADCAST)
    fun setContactKey(contact: String) {
        contactKey.value = contact
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: LiveData<List<Packet>> = contactKey.flatMapLatest { contactKey ->
        packetRepository.getMessagesFrom(contactKey)
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: LiveData<Map<String, Packet>> = _packets.mapLatest { list ->
        list.associateBy { packet -> packet.contact_key }
            .filter { it.value.port_num == Portnums.PortNum.TEXT_MESSAGE_APP_VALUE }
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val waypoints: LiveData<Map<Int?, Packet>> = _packets.mapLatest { list ->
        list.associateBy { packet -> packet.data.waypoint?.id }
            .filter { it.value.port_num == Portnums.PortNum.WAYPOINT_APP_VALUE }
    }.asLiveData()

    fun sendMessage(str: String, channel: Int = 0, dest: String = DataPacket.ID_BROADCAST) {
        val p = DataPacket(dest, channel, str)
        sendDataPacket(p)
    }

    fun sendDataPacket(p: DataPacket) {
        try {
            meshService?.send(p)
        } catch (ex: RemoteException) {
            errormsg("Send DataPacket error: ${ex.message}")
        }
    }

    fun deleteAllLogs() = viewModelScope.launch(Dispatchers.IO) {
        meshLogRepository.deleteAll()
    }

    fun deleteAllMessages() = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteAllMessages()
    }

    fun deleteMessages(uuidList: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteMessages(uuidList)
    }

    companion object {
        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    var actionBarMenu: Menu? = null

    var meshService: IMeshService? = null

    val nodeDB = NodeDB(this)

    /// Connection state to our radio device
    private val _connectionState = MutableLiveData(MeshService.ConnectionState.DISCONNECTED)
    val connectionState: LiveData<MeshService.ConnectionState> get() = _connectionState

    fun isConnected() = _connectionState.value == MeshService.ConnectionState.CONNECTED

    fun setConnectionState(connectionState: MeshService.ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _requestChannelUrl = MutableLiveData<Uri?>(null)
    val requestChannelUrl: LiveData<Uri?> get() = _requestChannelUrl

    fun setRequestChannelUrl(channelUrl: Uri) {
        _requestChannelUrl.value = channelUrl
    }

    /**
     * Called immediately after activity observes requestChannelUrl
     */
    fun clearRequestChannelUrl() {
        _requestChannelUrl.value = null
    }

    var txEnabled: Boolean
        get() = config.lora.txEnabled
        set(value) {
            updateLoraConfig { it.copy { txEnabled = value } }
        }

    var region: Config.LoRaConfig.RegionCode
        get() = config.lora.region
        set(value) {
            updateLoraConfig { it.copy { region = value } }
        }

    fun gpsString(p: Position): String {
        return when (config.display.gpsFormat) {
            Config.DisplayConfig.GpsCoordinateFormat.DEC -> GPSFormat.DEC(p)
            Config.DisplayConfig.GpsCoordinateFormat.DMS -> GPSFormat.DMS(p)
            Config.DisplayConfig.GpsCoordinateFormat.UTM -> GPSFormat.UTM(p)
            Config.DisplayConfig.GpsCoordinateFormat.MGRS -> GPSFormat.MGRS(p)
            else -> GPSFormat.DEC(p)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isRouter: Boolean = config.device.role == Config.DeviceConfig.Role.ROUTER

    // We consider hasWifi = ESP32
    fun isESP32() = myNodeInfo.value?.hasWifi == true

    /// hardware info about our local device (can be null)
    private val _myNodeInfo = MutableLiveData<MyNodeInfo?>()
    val myNodeInfo: LiveData<MyNodeInfo?> get() = _myNodeInfo

    fun setMyNodeInfo(info: MyNodeInfo?) {
        _myNodeInfo.value = info
    }

    override fun onCleared() {
        super.onCleared()
        debug("ViewModel cleared")
    }

    /// Pull our latest node db from the device
    fun updateNodesFromDevice() {
        meshService?.let { service ->
            // Update our nodeinfos based on data from the device
            val nodes = service.nodes.associateBy { it.user?.id!! }
            nodeDB.setNodes(nodes)

            try {
                // Pull down our real node ID - This must be done AFTER reading the nodedb because we need the DB to find our nodeinof object
                nodeDB.setMyId(service.myId)
                val ownerName = nodeDB.ourNodeInfo?.user?.longName
                _ownerName.value = ownerName
            } catch (ex: Exception) {
                warn("Ignoring failure to get myId, service is probably just uninited... ${ex.message}")
            }
        }
    }

    inline fun updateDeviceConfig(crossinline body: (Config.DeviceConfig) -> Config.DeviceConfig) {
        val data = body(config.device)
        setConfig(config { device = data })
    }

    inline fun updatePositionConfig(crossinline body: (Config.PositionConfig) -> Config.PositionConfig) {
        val data = body(config.position)
        setConfig(config { position = data })
    }

    inline fun updatePowerConfig(crossinline body: (Config.PowerConfig) -> Config.PowerConfig) {
        val data = body(config.power)
        setConfig(config { power = data })
    }

    inline fun updateNetworkConfig(crossinline body: (Config.NetworkConfig) -> Config.NetworkConfig) {
        val data = body(config.network)
        setConfig(config { network = data })
    }

    inline fun updateDisplayConfig(crossinline body: (Config.DisplayConfig) -> Config.DisplayConfig) {
        val data = body(config.display)
        setConfig(config { display = data })
    }

    inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(config.lora)
        setConfig(config { lora = data })
    }

    inline fun updateBluetoothConfig(crossinline body: (Config.BluetoothConfig) -> Config.BluetoothConfig) {
        val data = body(config.bluetooth)
        setConfig(config { bluetooth = data })
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: Config) {
        meshService?.setConfig(config.toByteArray())
    }

    /// Convert the channels array to and from [AppOnlyProtos.ChannelSet]
    private var _channelSet: AppOnlyProtos.ChannelSet
        get() = channels.value.protobuf
        set(value) {
            (0 until max(_channelSet.settingsCount, value.settingsCount)).map { i ->
                channel {
                    role = when (i) {
                        0 -> ChannelProtos.Channel.Role.PRIMARY
                        in 1 until value.settingsCount -> ChannelProtos.Channel.Role.SECONDARY
                        else -> ChannelProtos.Channel.Role.DISABLED
                    }
                    index = i
                    settings = value.settingsList.getOrNull(i) ?: channelSettings { }
                }
            }.forEach {
                meshService?.setChannel(it.toByteArray())
            }

            viewModelScope.launch {
                channelSetRepository.clearSettings()
                channelSetRepository.addAllSettings(value)
            }

            val newConfig = config { lora = value.loraConfig }
            if (config.lora != newConfig.lora) setConfig(newConfig)
        }
    val channelSet get() = _channelSet

    /// Set the radio config (also updates our saved copy in preferences)
    fun setChannels(channelSet: ChannelSet) {
        debug("Setting new channels!")
        this._channelSet = channelSet.protobuf
    }

    /// our name in hte radio
    /// Note, we generate owner initials automatically for now
    /// our activity will read this from prefs or set it to the empty string
    private val _ownerName = MutableLiveData<String?>()
    val ownerName: LiveData<String?> get() = _ownerName

    val provideLocation = object : MutableLiveData<Boolean>(preferences.getBoolean("provide-location", false)) {
        override fun setValue(value: Boolean) {
            super.setValue(value)

            preferences.edit {
                this.putBoolean("provide-location", value)
            }
        }
    }

    // clean up all this nasty owner state management FIXME
    fun setOwner(s: String? = null) {

        if (s != null) {
            _ownerName.value = s

            // note: we allow an empty userstring to be written to prefs
            preferences.edit {
                putString("owner", s)
            }
        }

        // Note: we are careful to not set a new unique ID
        if (_ownerName.value!!.isNotEmpty())
            try {
                meshService?.setOwner(
                    null,
                    _ownerName.value,
                    getInitials(_ownerName.value!!)
                ) // Note: we use ?. here because we might be running in the emulator
            } catch (ex: RemoteException) {
                errormsg("Can't set username on device, is device offline? ${ex.message}")
            }
    }

    val adminChannelIndex: Int
        get() = channelSet.settingsList.map { it.name.lowercase() }.indexOf("admin")

    fun requestShutdown(idNum: Int) {
        try {
            meshService?.requestShutdown(idNum)
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
        }
    }

    fun requestReboot(idNum: Int) {
        try {
            meshService?.requestReboot(idNum)
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
        }
    }

    fun requestFactoryReset(idNum: Int) {
        try {
            meshService?.requestFactoryReset(idNum)
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
        }
    }

    fun requestNodedbReset(idNum: Int) {
        try {
            meshService?.requestNodedbReset(idNum)
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
        }
    }

    /**
     * Write the persisted packet data out to a CSV file in the specified location.
     */
    fun saveMessagesCSV(file_uri: Uri) {
        viewModelScope.launch(Dispatchers.Main) {
            // Extract distances to this device from position messages and put (node,SNR,distance) in
            // the file_uri
            val myNodeNum = myNodeInfo.value?.myNodeNum ?: return@launch

            // Capture the current node value while we're still on main thread
            val nodes = nodeDB.nodes.value ?: emptyMap()

            val positionToPos: (MeshProtos.Position?) -> Position? = { meshPosition ->
                meshPosition?.let { Position(it) }.takeIf {
                    it?.isValid() == true
                }
            }

            writeToUri(file_uri) { writer ->
                // Create a map of nodes keyed by their ID
                val nodesById = nodes.values.associateBy { it.num }.toMutableMap()
                val nodePositions = mutableMapOf<Int, MeshProtos.Position?>()

                writer.appendLine("date,time,from,sender name,sender lat,sender long,rx lat,rx long,rx elevation,rx snr,distance,hop limit,payload")

                // Packets are ordered by time, we keep most recent position of
                // our device in localNodePosition.
                val dateFormat = SimpleDateFormat("yyyy-MM-dd,HH:mm:ss", Locale.getDefault())
                meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
                    // If we get a NodeInfo packet, use it to update our position data (if valid)
                    packet.nodeInfo?.let { nodeInfo ->
                        positionToPos.invoke(nodeInfo.position)?.let { _ ->
                            nodePositions[nodeInfo.num] = nodeInfo.position
                        }
                    }

                    packet.meshPacket?.let { proto ->
                        // If the packet contains position data then use it to update, if valid
                        packet.position?.let { position ->
                            positionToPos.invoke(position)?.let { _ ->
                                nodePositions[proto.from] = position
                            }
                        }

                        // Filter out of our results any packet that doesn't report SNR.  This
                        // is primarily ADMIN_APP.
                        if (proto.rxSnr != 0.0f) {
                            val rxDateTime = dateFormat.format(packet.received_date)
                            val rxFrom = proto.from.toUInt()
                            val senderName = nodesById[proto.from]?.user?.longName ?: ""

                            // sender lat & long
                            val senderPosition = nodePositions[proto.from]
                            val senderPos = positionToPos.invoke(senderPosition)
                            val senderLat = senderPos?.latitude ?: ""
                            val senderLong = senderPos?.longitude ?: ""

                            // rx lat, long, and elevation
                            val rxPosition = nodePositions[myNodeNum]
                            val rxPos = positionToPos.invoke(rxPosition)
                            val rxLat = rxPos?.latitude ?: ""
                            val rxLong = rxPos?.longitude ?: ""
                            val rxAlt = rxPos?.altitude ?: ""
                            val rxSnr = "%f".format(proto.rxSnr)

                            // Calculate the distance if both positions are valid

                            val dist = if (senderPos == null || rxPos == null) {
                                ""
                            } else {
                                positionToMeter(
                                    rxPosition!!, // Use rxPosition but only if rxPos was valid
                                    senderPosition!! // Use senderPosition but only if senderPos was valid
                                ).roundToInt().toString()
                            }

                            val hopLimit = proto.hopLimit

                            val payload = when {
                                proto.decoded.portnumValue != Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> "<${proto.decoded.portnum}>"
                                proto.hasDecoded() -> "\"" + proto.decoded.payload.toStringUtf8()
                                    .replace("\"", "\\\"") + "\""
                                proto.hasEncrypted() -> "${proto.encrypted.size()} encrypted bytes"
                                else -> ""
                            }

                            //  date,time,from,sender name,sender lat,sender long,rx lat,rx long,rx elevation,rx snr,distance,hop limit,payload
                            writer.appendLine("$rxDateTime,$rxFrom,$senderName,$senderLat,$senderLong,$rxLat,$rxLong,$rxAlt,$rxSnr,$dist,$hopLimit,$payload")
                        }
                    }
                }
            }
        }
    }

    private suspend inline fun writeToUri(uri: Uri, crossinline block: suspend (BufferedWriter) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                        BufferedWriter(fileWriter).use { writer ->
                            block.invoke(writer)
                        }
                    }
                }
            } catch (ex: FileNotFoundException) {
                errormsg("Can't write file error: ${ex.message}")
            }
        }
    }


    fun parseUrl(url: String, map: MapView) {
        viewModelScope.launch(Dispatchers.IO) {
            parseIt(url, map)
        }
    }

    // For Future Use
    //  model.parseUrl(
    //                "https://www.google.com/maps/d/kml?forcekml=1&mid=1FmqWhZG3PG3dY92x9yf2RlREcK7kMZs&lid=-ivSjBCePsM",
    //                map
    //            )
    private fun parseIt(url: String, map: MapView) {
        val kmlDoc = KmlDocument()
        try {
            kmlDoc.parseKMLUrl(url)
            val kmlOverlay = kmlDoc.mKmlRoot.buildOverlay(map, null, null, kmlDoc) as FolderOverlay
            kmlDoc.mKmlRoot.mItems
            kmlDoc.mKmlRoot.mName
            map.overlayManager.overlays().add(kmlOverlay)
        } catch (ex: Exception) {
            debug("Failed to download .kml $ex")
        }
    }

    fun addQuickChatAction(name: String, value: String, mode: QuickChatAction.Mode) {
        viewModelScope.launch(Dispatchers.Main) {
            val action = QuickChatAction(0, name, value, mode, _quickChatActions.value.size)
            quickChatActionRepository.insert(action)
        }
    }

    fun deleteQuickChatAction(action: QuickChatAction) {
        viewModelScope.launch(Dispatchers.Main) {
            quickChatActionRepository.delete(action)
        }
    }

    fun updateQuickChatAction(
        action: QuickChatAction,
        name: String?,
        message: String?,
        mode: QuickChatAction.Mode?
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val newAction = QuickChatAction(
                action.uuid,
                name ?: action.name,
                message ?: action.message,
                mode ?: action.mode,
                action.position
            )
            quickChatActionRepository.update(newAction)
        }
    }

    fun updateActionPositions(actions: List<QuickChatAction>) {
        viewModelScope.launch(Dispatchers.Main) {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }
}

