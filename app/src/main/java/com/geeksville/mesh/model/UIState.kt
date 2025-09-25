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

package com.geeksville.mesh.model

import android.app.Application
import android.net.Uri
import android.os.RemoteException
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos
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
import com.geeksville.mesh.repository.api.DeviceHardwareRepository
import com.geeksville.mesh.repository.api.FirmwareReleaseRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.radio.MeshActivity
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.service.MeshServiceNotifications
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.util.safeNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.getShortDate
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.strings.R
import javax.inject.Inject

// Given a human name, strip out the first letter of the first three words and return that as the
// initials for
// that user, ignoring emojis. If the original name is only one word, strip vowels from the original
// name and if the result is 3 or more characters, use the first three characters. If not, just take
// the first 3 characters of the original name.
fun getInitials(fullName: String): String {
    val maxInitialLength = 4
    val minWordCountForInitials = 2
    val name = fullName.trim().withoutEmojis()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials =
        when (words.size) {
            in 0 until minWordCountForInitials -> {
                val nameWithoutVowels =
                    if (name.isNotEmpty()) {
                        name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
                    } else {
                        ""
                    }
                if (nameWithoutVowels.length >= maxInitialLength) nameWithoutVowels else name
            }

            else -> words.map { it.first() }.joinToString("")
        }
    return initials.take(maxInitialLength)
}

private fun String.withoutEmojis(): String = filterNot { char -> char.isSurrogate() }

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists. Only changes are included in the
 * resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
internal fun getChannelList(new: List<ChannelSettings>, old: List<ChannelSettings>): List<ChannelProtos.Channel> =
    buildList {
        for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
            if (old.getOrNull(i) != new.getOrNull(i)) {
                add(
                    channel {
                        role =
                            when (i) {
                                0 -> ChannelProtos.Channel.Role.PRIMARY
                                in 1..new.lastIndex -> ChannelProtos.Channel.Role.SECONDARY
                                else -> ChannelProtos.Channel.Role.DISABLED
                            }
                        index = i
                        settings = new.getOrNull(i) ?: channelSettings {}
                    },
                )
            }
        }
    }

data class NodesUiState(
    val sort: NodeSortOption = NodeSortOption.LAST_HEARD,
    val filter: String = "",
    val includeUnknown: Boolean = false,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val distanceUnits: Int = 0,
    val tempInFahrenheit: Boolean = false,
    val showDetails: Boolean = false,
    val showIgnored: Boolean = false,
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
    val isUnmessageable: Boolean,
    val nodeColors: Pair<Int, Int>? = null,
)

@Suppress("LongParameterList", "LargeClass", "UnusedPrivateProperty")
@HiltViewModel
class UIViewModel
@Inject
constructor(
    private val app: Application,
    private val nodeDB: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: MeshLogRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val packetRepository: PacketRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    private val uiPrefs: UiPrefs,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
    private val meshServiceNotifications: MeshServiceNotifications,
) : ViewModel(),
    Logging {

    val theme: StateFlow<Int> = uiPreferencesDataSource.theme

    private val _lastTraceRouteTime = MutableStateFlow<Long?>(null)
    val lastTraceRouteTime: StateFlow<Long?> = _lastTraceRouteTime.asStateFlow()

    val firmwareVersion = myNodeInfo.mapNotNull { nodeInfo -> nodeInfo?.firmwareVersion }

    val firmwareEdition = meshLogRepository.getMyNodeInfo().map { nodeInfo -> nodeInfo?.firmwareEdition }

    val deviceHardware: StateFlow<DeviceHardware?> =
        ourNodeInfo
            .mapNotNull { nodeInfo ->
                nodeInfo?.user?.hwModel?.let { hwModel ->
                    deviceHardwareRepository.getDeviceHardwareByModel(hwModel.safeNumber()).getOrNull()
                }
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val clientNotification: StateFlow<MeshProtos.ClientNotification?> = serviceRepository.clientNotification

    fun clearClientNotification(notification: MeshProtos.ClientNotification) {
        serviceRepository.clearClientNotification()
        meshServiceNotifications.clearClientNotification(notification)
    }

    /**
     * Emits events for mesh network send/receive activity. This is a SharedFlow to ensure all events are delivered,
     * even if they are the same.
     */
    val meshActivity: SharedFlow<MeshActivity> =
        radioInterfaceService.meshActivity.shareIn(viewModelScope, SharingStarted.Eagerly, 0)

    data class AlertData(
        val title: String,
        val message: String? = null,
        val html: String? = null,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val choices: Map<String, () -> Unit> = emptyMap(),
    )

    private val _currentAlert: MutableStateFlow<AlertData?> = MutableStateFlow(null)
    val currentAlert = _currentAlert.asStateFlow()

    fun showAlert(
        title: String,
        message: String? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        dismissable: Boolean = true,
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        _currentAlert.value =
            AlertData(
                title = title,
                message = message,
                html = html,
                onConfirm = {
                    onConfirm?.invoke()
                    dismissAlert()
                },
                onDismiss = { if (dismissable) dismissAlert() },
                choices = choices,
            )
    }

    private fun dismissAlert() {
        _currentAlert.value = null
    }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    fun setTitle(title: String) {
        viewModelScope.launch { _title.value = title }
    }

    val meshService: IMeshService?
        get() = serviceRepository.meshService

    private val localConfig = MutableStateFlow<LocalConfig>(LocalConfig.getDefaultInstance())

    val config
        get() = localConfig.value

    private val _moduleConfig = MutableStateFlow<LocalModuleConfig>(LocalModuleConfig.getDefaultInstance())
    val moduleConfig: StateFlow<LocalModuleConfig> = _moduleConfig
    val module
        get() = _moduleConfig.value

    private val _channels = MutableStateFlow(channelSet {})
    val channels: StateFlow<AppOnlyProtos.ChannelSet>
        get() = _channels

    val quickChatActions
        get() =
            quickChatActionRepository
                .getAllActions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val nodeFilterText = MutableStateFlow("")
    private val nodeSortOption =
        MutableStateFlow(NodeSortOption.entries.getOrElse(uiPrefs.nodeSortOption) { NodeSortOption.VIA_FAVORITE })
    private val includeUnknown = MutableStateFlow(uiPrefs.includeUnknown)
    private val showDetails = MutableStateFlow(uiPrefs.showDetails)
    private val onlyOnline = MutableStateFlow(uiPrefs.onlyOnline)
    private val onlyDirect = MutableStateFlow(uiPrefs.onlyDirect)

    private val _showIgnored = MutableStateFlow(uiPrefs.showIgnored)
    val showIgnored: StateFlow<Boolean> = _showIgnored

    private val _showQuickChat = MutableStateFlow(uiPrefs.showQuickChat)
    val showQuickChat: StateFlow<Boolean> = _showQuickChat

    fun toggleShowQuickChat() = toggle(_showQuickChat) { uiPrefs.showQuickChat = it }

    private fun toggle(state: MutableStateFlow<Boolean>, onChanged: (newValue: Boolean) -> Unit) {
        (!state.value).let { toggled ->
            state.update { toggled }
            onChanged(toggled)
        }
    }

    data class NodeFilterState(
        val filterText: String,
        val includeUnknown: Boolean,
        val onlyOnline: Boolean,
        val onlyDirect: Boolean,
        val showIgnored: Boolean,
    )

    val nodeFilterStateFlow: Flow<NodeFilterState> =
        combine(nodeFilterText, includeUnknown, onlyOnline, onlyDirect, showIgnored) {
                filterText,
                includeUnknown,
                onlyOnline,
                onlyDirect,
                showIgnored,
            ->
            NodeFilterState(filterText, includeUnknown, onlyOnline, onlyDirect, showIgnored)
        }

    val nodesUiState: StateFlow<NodesUiState> =
        combine(nodeFilterStateFlow, nodeSortOption, showDetails, radioConfigRepository.deviceProfileFlow) {
                filterFlow,
                sort,
                showDetails,
                profile,
            ->
            NodesUiState(
                sort = sort,
                filter = filterFlow.filterText,
                includeUnknown = filterFlow.includeUnknown,
                onlyOnline = filterFlow.onlyOnline,
                onlyDirect = filterFlow.onlyDirect,
                distanceUnits = profile.config.display.units.number,
                tempInFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
                showDetails = showDetails,
                showIgnored = filterFlow.showIgnored,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NodesUiState.Empty,
            )

    val unfilteredNodeList: StateFlow<List<Node>> =
        nodeDB
            .getNodes()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val nodeList: StateFlow<List<Node>> =
        nodesUiState
            .flatMapLatest { state ->
                nodeDB
                    .getNodes(state.sort, state.filter, state.includeUnknown, state.onlyOnline, state.onlyDirect)
                    .map { list -> list.filter { it.isIgnored == state.showIgnored } }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    val ourNodeInfo: StateFlow<Node?>
        get() = nodeDB.ourNodeInfo

    fun getNode(userId: String?) = nodeDB.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun getUser(userId: String?) = nodeDB.getUser(userId ?: DataPacket.ID_BROADCAST)

    val snackBarHostState = SnackbarHostState()

    fun showSnackBar(text: Int) = showSnackBar(app.getString(text))

    fun showSnackBar(
        text: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
        onActionPerformed: (() -> Unit) = {},
        onDismissed: (() -> Unit) = {},
    ) = viewModelScope.launch {
        snackBarHostState.showSnackbar(text, actionLabel, withDismissAction, duration).run {
            when (this) {
                SnackbarResult.ActionPerformed -> onActionPerformed()
                SnackbarResult.Dismissed -> onDismissed()
            }
        }
    }

    init {
        serviceRepository.errorMessage
            .filterNotNull()
            .onEach {
                showAlert(
                    title = app.getString(R.string.client_notification),
                    message = it,
                    onConfirm = { serviceRepository.clearErrorMessage() },
                    dismissable = false,
                )
            }
            .launchIn(viewModelScope)

        radioConfigRepository.localConfigFlow.onEach { config -> localConfig.value = config }.launchIn(viewModelScope)
        radioConfigRepository.moduleConfigFlow
            .onEach { config -> _moduleConfig.value = config }
            .launchIn(viewModelScope)
        radioConfigRepository.channelSetFlow
            .onEach { channelSet -> _channels.value = channelSet }
            .launchIn(viewModelScope)

        debug("ViewModel created")
    }

    val contactList =
        combine(nodeDB.myNodeInfo, packetRepository.getContacts(), channels, packetRepository.getContactSettings()) {
                myNodeInfo,
                contacts,
                channelSet,
                settings,
            ->
            val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList()
            // Add empty channel placeholders (always show Broadcast contacts, even when empty)
            val placeholder =
                (0 until channelSet.settingsCount).associate { ch ->
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
                val node = getNode(if (fromLocal) data.to else data.from)

                val shortName = user.shortName
                val longName =
                    if (toBroadcast) {
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
                    isUnmessageable = user.isUnmessagable,
                    nodeColors =
                    if (!toBroadcast) {
                        node.colors
                    } else {
                        null
                    },
                )
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun getMessagesFrom(contactKey: String): StateFlow<List<Message>> {
        contactKeyForMessages.value = contactKey
        return messagesForContactKey
    }

    private val contactKeyForMessages: MutableStateFlow<String?> = MutableStateFlow(null)
    private val messagesForContactKey: StateFlow<List<Message>> =
        contactKeyForMessages
            .filterNotNull()
            .flatMapLatest { contactKey -> packetRepository.getMessagesFrom(contactKey, ::getNode) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun generatePacketId(): Int? {
        return try {
            meshService?.packetId
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
            return null
        }
    }

    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null) {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        // if the destination is a node, we need to ensure it's a
        // favorite so it does not get removed from the on-device node database.
        if (channel == null) { // no channel specified, so we assume it's a direct message
            val node = nodeDB.getNode(dest)
            if (!node.isFavorite) {
                favoriteNode(nodeDB.getNode(dest))
            }
        }
        val p = DataPacket(dest, channel ?: 0, str, replyId)
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

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey)) }

    private val _sharedContactRequested: MutableStateFlow<AdminProtos.SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested: StateFlow<AdminProtos.SharedContact?>
        get() = _sharedContactRequested.asStateFlow()

    fun setSharedContactRequested(sharedContact: AdminProtos.SharedContact?) {
        _sharedContactRequested.value = sharedContact
    }

    fun addSharedContact(sharedContact: AdminProtos.SharedContact) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.AddSharedContact(sharedContact)) }

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

    fun setMuteUntil(contacts: List<String>, until: Long) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.setMuteUntil(contacts, until) }

    fun deleteContacts(contacts: List<String>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteContacts(contacts) }

    fun deleteMessages(uuidList: List<Long>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteMessages(uuidList) }

    fun deleteWaypoint(id: Int) = viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteWaypoint(id) }

    fun clearUnreadCount(contact: String, timestamp: Long) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.clearUnreadCount(contact, timestamp)
        val unreadCount = packetRepository.getUnreadCount(contact)
        if (unreadCount == 0) meshServiceNotifications.cancelMessageNotification(contact)
    }

    // Connection state to our radio device
    val connectionState
        get() = serviceRepository.connectionState

    val isConnectedStateFlow =
        serviceRepository.connectionState
            .map { it.isConnected() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _requestChannelSet = MutableStateFlow<AppOnlyProtos.ChannelSet?>(null)
    val requestChannelSet: StateFlow<AppOnlyProtos.ChannelSet?>
        get() = _requestChannelSet

    fun requestChannelUrl(url: Uri) = runCatching { _requestChannelSet.value = url.toChannelSet() }
        .onFailure { ex ->
            errormsg("Channel url error: ${ex.message}")
            showSnackBar(R.string.channel_invalid)
        }

    val latestStableFirmwareRelease = firmwareReleaseRepository.stableRelease.mapNotNull { it?.asDeviceVersion() }

    /** Called immediately after activity observes requestChannelUrl */
    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
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

    fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            errormsg("Favorite node error:", ex)
        }
    }

    fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Ignore(node))
        } catch (ex: RemoteException) {
            errormsg("Ignore node error:", ex)
        }
    }

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> removeNode(action.node.num)
            is NodeMenuAction.Ignore -> ignoreNode(action.node)
            is NodeMenuAction.Favorite -> favoriteNode(action.node)
            is NodeMenuAction.RequestUserInfo -> requestUserInfo(action.node.num)
            is NodeMenuAction.RequestPosition -> requestPosition(action.node.num)
            is NodeMenuAction.TraceRoute -> {
                requestTraceroute(action.node.num)
                _lastTraceRouteTime.value = System.currentTimeMillis()
            }

            else -> {}
        }
    }

    fun setNodeNotes(nodeNum: Int, notes: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            nodeDB.setNodeNotes(nodeNum, notes)
        } catch (ex: java.io.IOException) {
            errormsg("Set node notes IO error: ${ex.message}")
        } catch (ex: java.sql.SQLException) {
            errormsg("Set node notes SQL error: ${ex.message}")
        }
    }

    // managed mode disables all access to configuration
    val isManaged: Boolean
        get() = config.device.isManaged || config.security.isManaged

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val maxChannels
        get() = myNodeInfo.value?.maxChannels ?: 8

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

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: AppOnlyProtos.ChannelSet) = viewModelScope.launch {
        getChannelList(channelSet.settingsList, channels.value.settingsList).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settingsList)

        val newConfig = config { lora = channelSet.loraConfig }
        if (config.lora != newConfig.lora) setConfig(newConfig)
    }

    fun setOwner(name: String) {
        val user =
            ourNodeInfo.value?.user?.copy {
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

    fun addQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.upsert(action) }

    fun deleteQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.delete(action) }

    fun updateActionPositions(actions: List<QuickChatAction>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }

    val tracerouteResponse: LiveData<String?>
        get() = serviceRepository.tracerouteResponse.asLiveData()

    fun clearTracerouteResponse() {
        serviceRepository.clearTracerouteResponse()
    }

    fun setNodeFilterText(text: String) {
        nodeFilterText.value = text
    }

    val appIntroCompleted: StateFlow<Boolean> = uiPreferencesDataSource.appIntroCompleted

    fun onAppIntroCompleted() {
        uiPreferencesDataSource.setAppIntroCompleted(true)
    }
}
