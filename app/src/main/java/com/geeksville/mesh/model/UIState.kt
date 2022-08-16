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
import androidx.lifecycle.viewModelScope
import com.geeksville.android.Logging
import com.geeksville.mesh.*
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.repository.datastore.LocalConfigRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.util.positionToMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
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
    private val packetRepository: PacketRepository,
    private val localConfigRepository: LocalConfigRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val preferences: SharedPreferences
) : ViewModel(), Logging {

    private val _allPacketState = MutableStateFlow<List<Packet>>(emptyList())
    val allPackets: StateFlow<List<Packet>> = _allPacketState

    private val _localConfig = MutableLiveData<LocalOnlyProtos.LocalConfig?>()
    val localConfig: LiveData<LocalOnlyProtos.LocalConfig?> get() = _localConfig

    private val _quickChatActions =
        MutableStateFlow<List<com.geeksville.mesh.database.entity.QuickChatAction>>(
            emptyList()
        )
    val quickChatActions: StateFlow<List<QuickChatAction>> = _quickChatActions

    init {
        viewModelScope.launch {
            packetRepository.getAllPackets().collect { packets ->
                _allPacketState.value = packets
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
        debug("ViewModel created")
    }

    fun deleteAllPacket() = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteAll()
    }

    companion object {
        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    var actionBarMenu: Menu? = null

    var meshService: IMeshService? = null

    val nodeDB = NodeDB(this)
    val messagesState = MessagesState(this)

    /// Connection state to our radio device
    private val _connectionState = MutableLiveData(MeshService.ConnectionState.DISCONNECTED)
    val connectionState: LiveData<MeshService.ConnectionState> get() = _connectionState

    fun isConnected() = _connectionState.value == MeshService.ConnectionState.CONNECTED

    fun setConnectionState(connectionState: MeshService.ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _channels = MutableLiveData<ChannelSet?>()
    val channels: LiveData<ChannelSet?> get() = _channels

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

    var positionBroadcastSecs: Int?
        get() {
            _localConfig.value?.position?.positionBroadcastSecs?.let {
                return if (it > 0) it else defaultPositionBroadcastSecs
            }
            return null
        }
        set(value) {
            val config = _localConfig.value
            if (value != null && config != null) {
                val builder = config.position.toBuilder()
                builder.positionBroadcastSecs =
                    if (value == defaultPositionBroadcastSecs) 0 else value
                val newConfig = ConfigProtos.Config.newBuilder()
                newConfig.position = builder.build()
                setDeviceConfig(newConfig.build())
            }
        }

    var lsSleepSecs: Int?
        get() {
            _localConfig.value?.power?.lsSecs?.let {
                return if (it > 0) it else defaultLsSecs
            }
            return null
        }
        set(value) {
            val config = _localConfig.value
            if (value != null && config != null) {
                val builder = config.power.toBuilder()
                builder.lsSecs = if (value == defaultLsSecs) 0 else value
                val newConfig = ConfigProtos.Config.newBuilder()
                newConfig.power = builder.build()
                setDeviceConfig(newConfig.build())
            }
        }

    var gpsDisabled: Boolean
        get() = _localConfig.value?.position?.gpsDisabled ?: false
        set(value) {
            val config = _localConfig.value
            if (config != null) {
                val builder = config.position.toBuilder()
                builder.gpsDisabled = value
                val newConfig = ConfigProtos.Config.newBuilder()
                newConfig.position = builder.build()
                setDeviceConfig(newConfig.build())
            }
        }

    var isPowerSaving: Boolean?
        get() = _localConfig.value?.power?.isPowerSaving
        set(value) {
            val config = _localConfig.value
            if (value != null && config != null) {
                val builder = config.power.toBuilder()
                builder.isPowerSaving = value
                val newConfig = ConfigProtos.Config.newBuilder()
                newConfig.power = builder.build()
                setDeviceConfig(newConfig.build())
            }
        }

    var region: ConfigProtos.Config.LoRaConfig.RegionCode
        get() = localConfig.value?.lora?.region ?: ConfigProtos.Config.LoRaConfig.RegionCode.Unset
        set(value) {
            val config = _localConfig.value
            if (config != null) {
                val builder = config.lora.toBuilder()
                builder.region = value
                val newConfig = ConfigProtos.Config.newBuilder()
                newConfig.lora = builder.build()
                setDeviceConfig(newConfig.build())
            }
        }

    @Suppress("MemberVisibilityCanBePrivate")
    val isRouter: Boolean =
        localConfig.value?.device?.role == ConfigProtos.Config.DeviceConfig.Role.Router

    // These default values are borrowed from the device code.
    private val defaultPositionBroadcastSecs = if (isRouter) 12 * 60 * 60 else 15 * 60
    private val defaultLsSecs = if (isRouter) 24 * 60 * 60 else 5 * 60

    // val isESP32: Boolean = _localConfig.value?.hasWifi() == true (not working)
    fun isESP32(): Boolean {
        // mesh.proto 'HardwareModel' enums for ESP32 devices
        val hwModelESP32 = listOf(1, 2, 3, 4, 5, 6, 8, 10, 11, 32, 35, 39, 40, 41, 43, 44)
        return hwModelESP32.contains(nodeDB.ourNodeInfo?.user?.hwModel?.number)
    }

    fun hasAXP(): Boolean {
        val hasAXP = listOf(4, 7, 9) // mesh.proto 'HardwareModel' enums with AXP192 chip
        return hasAXP.contains(nodeDB.ourNodeInfo?.user?.hwModel?.number)
    }

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

    /**
     * Return the primary channel info
     */
    val primaryChannel: Channel? get() = _channels.value?.primaryChannel

    // Set the radio config (also updates our saved copy in preferences)
    private fun setDeviceConfig(config: ConfigProtos.Config) {
        meshService?.deviceConfig = config.toByteArray()
    }

    fun setLocalConfig(localConfig: LocalOnlyProtos.LocalConfig) {
        if (_localConfig.value == localConfig) return
        _localConfig.value = localConfig
    }

    /// Set the radio config (also updates our saved copy in preferences)
    fun setChannels(c: ChannelSet) {
        if (_channels.value == c) return
        debug("Setting new channels!")
        meshService?.channels = c.protobuf.toByteArray()
        _channels.value =
            c // Must be done after calling the service, so we will will properly throw if the service failed (and therefore not cache invalid new settings)

        preferences.edit {
            this.putString("channel-url", c.getChannelUrl().toString())
        }
    }

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    // lateinit var googleSignInClient: GoogleSignInClient

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

    fun requestShutdown() {
        meshService?.requestShutdown(DataPacket.ID_LOCAL)
    }

    fun requestReboot() {
        meshService?.requestReboot(DataPacket.ID_LOCAL)
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
                packetRepository.getAllPacketsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
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
                        if (proto.rxSnr > 0.0f) {
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
            for (position in 0..actions.size) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }
}

