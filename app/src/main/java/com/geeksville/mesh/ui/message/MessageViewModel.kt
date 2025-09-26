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

package com.geeksville.mesh.ui.message

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.MeshServiceNotifications
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.prefs.ui.UiPrefs
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MessageViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: PacketRepository,
    private val uiPrefs: UiPrefs,
    private val meshServiceNotifications: MeshServiceNotifications,
) : ViewModel() {
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val nodeList: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val channels =
        radioConfigRepository.channelSetFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            channelSet {},
        )

    private val _showQuickChat = MutableStateFlow(uiPrefs.showQuickChat)
    val showQuickChat: StateFlow<Boolean> = _showQuickChat

    val quickChatActions =
        quickChatActionRepository
            .getAllActions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // TODO this should be moved to a repository class
    private val _lastTraceRouteTime = MutableStateFlow<Long?>(null)
    val lastTraceRouteTime: StateFlow<Long?> = _lastTraceRouteTime.asStateFlow()

    fun setTitle(title: String) {
        viewModelScope.launch { _title.value = title }
    }

    fun getMessagesFrom(contactKey: String): StateFlow<List<Message>> {
        contactKeyForMessages.value = contactKey
        return messagesForContactKey
    }

    fun toggleShowQuickChat() = toggle(_showQuickChat) { uiPrefs.showQuickChat = it }

    private fun toggle(state: MutableStateFlow<Boolean>, onChanged: (newValue: Boolean) -> Unit) {
        (!state.value).let { toggled ->
            state.update { toggled }
            onChanged(toggled)
        }
    }

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)

    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null) {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        // if the destination is a node, we need to ensure it's a
        // favorite so it does not get removed from the on-device node database.
        if (channel == null) { // no channel specified, so we assume it's a direct message
            val node = nodeRepository.getNode(dest)
            if (!node.isFavorite) {
                favoriteNode(nodeRepository.getNode(dest))
            }
        }
        val p = DataPacket(dest, channel ?: 0, str, replyId)
        sendDataPacket(p)
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey)) }

    fun deleteMessages(uuidList: List<Long>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteMessages(uuidList) }

    fun clearUnreadCount(contact: String, timestamp: Long) = viewModelScope.launch(Dispatchers.IO) {
        packetRepository.clearUnreadCount(contact, timestamp)
        val unreadCount = packetRepository.getUnreadCount(contact)
        if (unreadCount == 0) meshServiceNotifications.cancelMessageNotification(contact)
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

    private fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Favorite node error")
        }
    }

    private fun sendDataPacket(p: DataPacket) {
        try {
            serviceRepository.meshService?.send(p)
        } catch (ex: RemoteException) {
            Timber.e("Send DataPacket error: ${ex.message}")
        }
    }

    private fun removeNode(nodeNum: Int) = viewModelScope.launch(Dispatchers.IO) {
        Timber.i("Removing node '$nodeNum'")
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return@launch
            serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
            nodeRepository.deleteNode(nodeNum)
        } catch (ex: RemoteException) {
            Timber.e("Remove node error: ${ex.message}")
        }
    }

    private fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Ignore(node))
        } catch (ex: RemoteException) {
            Timber.e(ex, "Ignore node error:")
        }
    }

    private fun requestUserInfo(destNum: Int) {
        Timber.i("Requesting UserInfo for '$destNum'")
        try {
            serviceRepository.meshService?.requestUserInfo(destNum)
        } catch (ex: RemoteException) {
            Timber.e("Request NodeInfo error: ${ex.message}")
        }
    }

    fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        Timber.i("Requesting position for '$destNum'")
        try {
            serviceRepository.meshService?.requestPosition(destNum, position)
        } catch (ex: RemoteException) {
            Timber.e("Request position error: ${ex.message}")
        }
    }

    fun requestTraceroute(destNum: Int) {
        Timber.i("Requesting traceroute for '$destNum'")
        try {
            val packetId = serviceRepository.meshService?.packetId ?: return
            serviceRepository.meshService?.requestTraceroute(packetId, destNum)
        } catch (ex: RemoteException) {
            Timber.e("Request traceroute error: ${ex.message}")
        }
    }
}
