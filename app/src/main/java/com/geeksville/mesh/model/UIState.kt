/*
 * Copyright (c) 2024 Meshtastic LLC
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
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.channel
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.ui.map.MAP_STYLE_ID
import com.geeksville.mesh.util.getShortDate
import com.geeksville.mesh.util.positionToMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

// Given a human name, strip out the first letter of the first three words and return that as the initials for
// that user. If the original name is only one word, strip vowels from the original name and if the result is
// 3 or more characters, use the first three characters. If not, just take the first 3 characters of the
// original name.
fun getInitials(nameIn: String): String {
    val nchars = 4
    val minchars = 2
    val name = nameIn.trim()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials = when (words.size) {
        in 0 until minchars -> {
            val nm = if (name.isNotEmpty()) {
                name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
            } else {
                ""
            }
            if (nm.length >= nchars) nm else name
        }
        else -> words.map { it.first() }.joinToString("")
    }
    return initials.take(nchars)
}

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists.
 * Only changes are included in the resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
internal fun getChannelList(
    new: List<ChannelSettings>,
    old: List<ChannelSettings>,
): List<ChannelProtos.Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) add(channel {
            role = when (i) {
                0 -> ChannelProtos.Channel.Role.PRIMARY
                in 1..new.lastIndex -> ChannelProtos.Channel.Role.SECONDARY
                else -> ChannelProtos.Channel.Role.DISABLED
            }
            index = i
            settings = new.getOrNull(i) ?: channelSettings { }
        })
    }
}

data class NodesUiState(
    val sort: NodeSortOption = NodeSortOption.LAST_HEARD,
    val filter: String = "",
    val includeUnknown: Boolean = false,
    val gpsFormat: Int = 0,
    val distanceUnits: Int = 0,
    val tempInFahrenheit: Boolean = false,
    val ignoreIncomingList: List<Int> = emptyList(),
    val showDetails: Boolean = false,
) {
    companion object {
        val Empty = NodesUiState()
    }
}

data class Contact(
    val contactKey: String,
    val shortName: String,
    val longName: String,
    val lastMessageTime: String?,
    val lastMessageText: String?,
    val unreadCount: Int,
    val messageCount: Int,
    val isMuted: Boolean,
)

@Suppress("LongParameterList")
@HiltViewModel
class UIViewModel @Inject constructor(
    private val app: Application,
    private val nodeDB: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: MeshLogRepository,
    private val packetRepository: PacketRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val preferences: SharedPreferences
) : ViewModel(), Logging {

    var actionBarMenu: Menu? = null
    val meshService: IMeshService? get() = radioConfigRepository.meshService

    val bondedAddress get() = radioInterfaceService.getBondedDeviceAddress()
    val selectedBluetooth get() = radioInterfaceService.getDeviceAddress()?.getOrNull(0) == 'x'

    private val _localConfig = MutableStateFlow<LocalConfig>(LocalConfig.getDefaultInstance())
    val localConfig: StateFlow<LocalConfig> = _localConfig
    val config get() = _localConfig.value

    private val _moduleConfig = MutableStateFlow<LocalModuleConfig>(LocalModuleConfig.getDefaultInstance())
    val moduleConfig: StateFlow<LocalModuleConfig> = _moduleConfig
    val module get() = _moduleConfig.value

    private val _channels = MutableStateFlow(channelSet {})
    val channels: StateFlow<AppOnlyProtos.ChannelSet> get() = _channels

    val quickChatActions get() = quickChatActionRepository.getAllActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val nodeFilterText = MutableStateFlow("")
    private val nodeSortOption = MutableStateFlow(NodeSortOption.LAST_HEARD)
    private val includeUnknown = MutableStateFlow(preferences.getBoolean("include-unknown", false))
    private val showDetails = MutableStateFlow(preferences.getBoolean("show-details", false))

    fun setSortOption(sort: NodeSortOption) {
        nodeSortOption.value = sort
    }

    fun toggleShowDetails() {
        showDetails.value = !showDetails.value
        preferences.edit { putBoolean("show-details", showDetails.value) }
    }

    fun toggleIncludeUnknown() {
        includeUnknown.value = !includeUnknown.value
        preferences.edit { putBoolean("include-unknown", includeUnknown.value) }
    }

    val nodesUiState: StateFlow<NodesUiState> = combine(
        nodeFilterText,
        nodeSortOption,
        includeUnknown,
        showDetails,
        radioConfigRepository.deviceProfileFlow,
    ) { filter, sort, includeUnknown, showDetails, profile ->
        NodesUiState(
            sort = sort,
            filter = filter,
            includeUnknown = includeUnknown,
            gpsFormat = profile.config.display.gpsFormat.number,
            distanceUnits = profile.config.display.units.number,
            tempInFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
            ignoreIncomingList = profile.config.lora.ignoreIncomingList,
            showDetails = showDetails,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NodesUiState.Empty,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val nodeList: StateFlow<List<NodeEntity>> = nodesUiState.flatMapLatest { state ->
        nodeDB.getNodes(state.sort, state.filter, state.includeUnknown)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?> get() = nodeDB.myNodeInfo
    val ourNodeInfo: StateFlow<NodeEntity?> get() = nodeDB.ourNodeInfo

    val nodesWithPosition get() = nodeDB.nodeDBbyNum.value.values.filter { it.validPosition != null }

    var mapStyleId: Int
        get() = preferences.getInt(MAP_STYLE_ID, 0)
        set(value) = preferences.edit { putInt(MAP_STYLE_ID, value) }

    fun getUser(userId: String?) = nodeDB.getUser(userId ?: DataPacket.ID_BROADCAST)

    private val _snackbarText = MutableLiveData<Any?>(null)
    val snackbarText: LiveData<Any?> get() = _snackbarText

    init {
        radioConfigRepository.errorMessage.filterNotNull().onEach {
            _snackbarText.value = it
            radioConfigRepository.clearErrorMessage()
        }.launchIn(viewModelScope)

        radioConfigRepository.localConfigFlow.onEach { config ->
            _localConfig.value = config
        }.launchIn(viewModelScope)
        radioConfigRepository.moduleConfigFlow.onEach { config ->
            _moduleConfig.value = config
        }.launchIn(viewModelScope)
        radioConfigRepository.channelSetFlow.onEach { channelSet ->
            _channels.value = channelSet
        }.launchIn(viewModelScope)

        debug("ViewModel created")
    }

    val contactList = combine(
        nodeDB.myNodeInfo,
        packetRepository.getContacts(),
        channels,
        packetRepository.getContactSettings(),
    ) { myNodeInfo, contacts, channelSet, settings ->
        val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList()
        // Add empty channel placeholders (always show Broadcast contacts, even when empty)
        val placeholder = (0 until channelSet.settingsCount).associate { ch ->
            val contactKey = "$ch${DataPacket.ID_BROADCAST}"
            val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
            contactKey to Packet(0L, myNodeNum, 1, contactKey, 0L, true, data)
        }

        (contacts + (placeholder - contacts.keys)).values.map { packet ->
            val data = packet.data
            val contactKey = packet.contact_key

            // Determine if this is my message (originated on this device)
            val fromLocal = data.from == DataPacket.ID_LOCAL
            val toBroadcast = data.to == DataPacket.ID_BROADCAST

            // grab usernames from NodeInfo
            val user = getUser(if (fromLocal) data.to else data.from)

            val shortName = user.shortName
            val longName = if (toBroadcast) {
                channelSet.getChannel(data.channel)?.name ?: app.getString(R.string.channel_name)
            } else {
                user.longName
            }

            Contact(
                contactKey = contactKey,
                shortName = if (toBroadcast) "${data.channel}" else shortName,
                longName = longName,
                lastMessageTime = getShortDate(data.time),
                lastMessageText = if (fromLocal) data.text else "$shortName: ${data.text}",
                unreadCount = packetRepository.getUnreadCount(contactKey),
                messageCount = packetRepository.getMessageCount(contactKey),
                isMuted = settings[contactKey]?.isMuted == true,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMessagesFrom(contactKey: String) = packetRepository.getMessagesFrom(contactKey)
        .mapLatest { list -> list.map { it.toMessage(::getUser) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val waypoints = packetRepository.getWaypoints().mapLatest { list ->
        list.associateBy { packet -> packet.data.waypoint!!.id }
            .filterValues { it.data.waypoint!!.expire > System.currentTimeMillis() / 1000 }
    }

    fun generatePacketId(): Int? {
        return try {
            meshService?.packetId
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
            return null
        }
    }

    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val p = DataPacket(dest, channel ?: 0, str)
        sendDataPacket(p)
    }

    fun sendWaypoint(wpt: MeshProtos.Waypoint, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val p = DataPacket(dest, channel ?: 0, wpt)
        if (wpt.id != 0) sendDataPacket(p)
    }

    private fun sendDataPacket(p: DataPacket) {
        try {
            meshService?.send(p)
        } catch (ex: RemoteException) {
            errormsg("Send DataPacket error: ${ex.message}")
        }
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) = viewModelScope.launch {
        radioConfigRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey))
    }

    fun requestTraceroute(destNum: Int) {
        info("Requesting traceroute for '$destNum'")
        try {
            val packetId = meshService?.packetId ?: return
            meshService?.requestTraceroute(packetId, destNum)
        } catch (ex: RemoteException) {
            errormsg("Request traceroute error: ${ex.message}")
        }
    }

    fun removeNode(nodeNum: Int) = viewModelScope.launch(Dispatchers.IO) {
        info("Removing node '$nodeNum'")
        try {
            val packetId = meshService?.packetId ?: return@launch
            meshService?.removeByNodenum(packetId, nodeNum)
            nodeDB.deleteNode(nodeNum)
        } catch (ex: RemoteException) {
            errormsg("Remove node error: ${ex.message}")
        }
    }

    fun requestUserInfo(destNum: Int) {
        info("Requesting UserInfo for '$destNum'")
        try {
            meshService?.requestUserInfo(destNum)
        } catch (ex: RemoteException) {
            errormsg("Request NodeInfo error: ${ex.message}")
        }
    }

    fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        info("Requesting position for '$destNum'")
        try {
            meshService?.requestPosition(destNum, position)
        } catch (ex: RemoteException) {
            errormsg("Request position error: ${ex.message}")
        }
    }

    fun setMuteUntil(contacts: List<String>, until: Long) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.setMuteUntil(contacts, until)
    }

    fun deleteContacts(contacts: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteContacts(contacts)
    }

    fun deleteMessages(uuidList: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteMessages(uuidList)
    }

    fun deleteWaypoint(id: Int) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.deleteWaypoint(id)
    }

    fun clearUnreadCount(contact: String, timestamp: Long) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.clearUnreadCount(contact, timestamp)
    }

    companion object {
        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    // Connection state to our radio device
    val connectionState get() = radioConfigRepository.connectionState
    fun isConnected() = connectionState.value != MeshService.ConnectionState.DISCONNECTED

    private val _requestChannelSet = MutableStateFlow<AppOnlyProtos.ChannelSet?>(null)
    val requestChannelSet: StateFlow<AppOnlyProtos.ChannelSet?> get() = _requestChannelSet

    fun requestChannelUrl(url: Uri) = runCatching {
        _requestChannelSet.value = url.toChannelSet()
    }.onFailure { ex ->
        errormsg("Channel url error: ${ex.message}")
        showSnackbar(R.string.channel_invalid)
    }

    /**
     * Called immediately after activity observes requestChannelUrl
     */
    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    fun showSnackbar(resString: Any) {
        _snackbarText.value = resString
    }

    /**
     * Called immediately after activity observes [snackbarText]
     */
    fun clearSnackbarText() {
        _snackbarText.value = null
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

    fun ignoreNode(nodeNum: Int) = updateLoraConfig {
        it.copy {
            val list = ignoreIncoming.toMutableList().apply {
                if (contains(nodeNum)) {
                    debug("removing node $nodeNum from ignore list")
                    remove(nodeNum)
                } else {
                    debug("adding node $nodeNum to ignore list")
                    add(nodeNum)
                }
            }
            ignoreIncoming.clear()
            ignoreIncoming.addAll(list)
        }
    }

    // managed mode disables all access to configuration
    val isManaged: Boolean get() = config.device.isManaged || config.security.isManaged

    val myNodeNum get() = myNodeInfo.value?.myNodeNum
    val maxChannels get() = myNodeInfo.value?.maxChannels ?: 8

    override fun onCleared() {
        super.onCleared()
        debug("ViewModel cleared")
    }

    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(config.lora)
        setConfig(config { lora = data })
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: Config) {
        try {
            meshService?.setConfig(config.toByteArray())
        } catch (ex: RemoteException) {
            errormsg("Set config error:", ex)
        }
    }

    fun setChannel(channel: ChannelProtos.Channel) {
        try {
            meshService?.setChannel(channel.toByteArray())
        } catch (ex: RemoteException) {
            errormsg("Set channel error:", ex)
        }
    }

    /**
     * Set the radio config (also updates our saved copy in preferences).
     */
    fun setChannels(channelSet: AppOnlyProtos.ChannelSet) = viewModelScope.launch {
        getChannelList(channelSet.settingsList, channels.value.settingsList).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settingsList)

        val newConfig = config { lora = channelSet.loraConfig }
        if (config.lora != newConfig.lora) setConfig(newConfig)
    }

    val provideLocation = object : MutableLiveData<Boolean>(preferences.getBoolean("provide-location", false)) {
        override fun setValue(value: Boolean) {
            super.setValue(value)

            preferences.edit {
                this.putBoolean("provide-location", value)
            }
        }
    }

    fun setOwner(name: String) {
        val user = ourNodeInfo.value?.user?.copy {
            longName = name
            shortName = getInitials(name)
        } ?: return

        try {
            // Note: we use ?. here because we might be running in the emulator
            meshService?.setRemoteOwner(myNodeNum ?: return, user.toByteArray())
        } catch (ex: RemoteException) {
            errormsg("Can't set username on device, is device offline? ${ex.message}")
        }
    }

    /**
     * Write the persisted packet data out to a CSV file in the specified location.
     */
    fun saveMessagesCSV(uri: Uri) {
        viewModelScope.launch(Dispatchers.Main) {
            // Extract distances to this device from position messages and put (node,SNR,distance) in
            // the file_uri
            val myNodeNum = myNodeNum ?: return@launch

            // Capture the current node value while we're still on main thread
            val nodes = nodeDB.nodeDBbyNum.value

            val positionToPos: (MeshProtos.Position?) -> Position? = { meshPosition ->
                meshPosition?.let { Position(it) }.takeIf {
                    it?.isValid() == true
                }
            }

            writeToUri(uri) { writer ->
                val nodePositions = mutableMapOf<Int, MeshProtos.Position?>()

                writer.appendLine("\"date\",\"time\",\"from\",\"sender name\",\"sender lat\",\"sender long\",\"rx lat\",\"rx long\",\"rx elevation\",\"rx snr\",\"distance\",\"hop limit\",\"payload\"")

                // Packets are ordered by time, we keep most recent position of
                // our device in localNodePosition.
                val dateFormat = SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())
                meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
                    // If we get a NodeInfo packet, use it to update our position data (if valid)
                    packet.nodeInfo?.let { nodeInfo ->
                        positionToPos.invoke(nodeInfo.position)?.let {
                            nodePositions[nodeInfo.num] = nodeInfo.position
                        }
                    }

                    packet.meshPacket?.let { proto ->
                        // If the packet contains position data then use it to update, if valid
                        packet.position?.let { position ->
                            positionToPos.invoke(position)?.let {
                                nodePositions[proto.from.takeIf { it != 0 } ?: myNodeNum] = position
                            }
                        }

                        // Filter out of our results any packet that doesn't report SNR.  This
                        // is primarily ADMIN_APP.
                        if (proto.rxSnr != 0.0f) {
                            val rxDateTime = dateFormat.format(packet.received_date)
                            val rxFrom = proto.from.toUInt()
                            val senderName = nodes[proto.from]?.user?.longName ?: ""

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
                            val rxSnr = proto.rxSnr

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
                                proto.decoded.portnumValue !in setOf(
                                    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                                    Portnums.PortNum.RANGE_TEST_APP_VALUE,
                                ) -> "<${proto.decoded.portnum}>"
                                proto.hasDecoded() -> proto.decoded.payload.toStringUtf8()
                                    .replace("\"", "\"\"")
                                proto.hasEncrypted() -> "${proto.encrypted.size()} encrypted bytes"
                                else -> ""
                            }

                            //  date,time,from,sender name,sender lat,sender long,rx lat,rx long,rx elevation,rx snr,distance,hop limit,payload
                            writer.appendLine("$rxDateTime,\"$rxFrom\",\"$senderName\",\"$senderLat\",\"$senderLong\",\"$rxLat\",\"$rxLong\",\"$rxAlt\",\"$rxSnr\",\"$dist\",\"$hopLimit\",\"$payload\"")
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

    fun addQuickChatAction(action: QuickChatAction) = viewModelScope.launch(Dispatchers.IO) {
        quickChatActionRepository.upsert(action)
    }

    fun deleteQuickChatAction(action: QuickChatAction) = viewModelScope.launch(Dispatchers.IO) {
        quickChatActionRepository.delete(action)
    }

    fun updateActionPositions(actions: List<QuickChatAction>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }

    val tracerouteResponse: LiveData<String?>
        get() = radioConfigRepository.tracerouteResponse.asLiveData()

    fun clearTracerouteResponse() {
        radioConfigRepository.clearTracerouteResponse()
    }

    private val _currentTab = MutableLiveData(0)
    val currentTab: LiveData<Int> get() = _currentTab

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
    }

    fun setNodeFilterText(text: String) {
        nodeFilterText.value = text
    }
}
