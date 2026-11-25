/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.EnsureSessionResult
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.util.toSharedContact
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.SharedContact

/** A write this screen sends to the remote node's node DB. */
enum class RemoteContactAction {
    ADD_CONTACT,
    FAVORITE,
    UNFAVORITE,
}

enum class RemoteWriteStatus {
    SENDING,
    ACKNOWLEDGED,
    NOT_ACKNOWLEDGED,
    INVALID_URL,
    NO_PUBLIC_KEY,
}

/** The latest write and how it went. [nodeNum] is null for a contact pasted as a URL. */
data class RemoteWriteResult(val nodeNum: Int?, val action: RemoteContactAction, val status: RemoteWriteStatus)

/**
 * Adds contacts to, and favorites or unfavorites nodes in, the node DB of [destNum], a node this radio administers
 * remotely. Candidates come from the local node DB, since firmware has no admin request that lists a remote node DB.
 */
@KoinViewModel
class RemoteContactsViewModel(
    @InjectedParam val destNum: Int,
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastResult = MutableStateFlow<RemoteWriteResult?>(null)
    val lastResult: StateFlow<RemoteWriteResult?> = _lastResult.asStateFlow()

    val destNode: StateFlow<Node?> = nodeRepository.nodeDBbyNum.map { it[destNum] }.stateInWhileSubscribed(null)

    /** Nodes with a public key, other than [destNum] itself; a keyless contact would erase the remote node's key. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val candidates: StateFlow<List<Node>> =
        _query
            .flatMapLatest { nodeRepository.getNodes(sort = NodeSortOption.ALPHABETICAL, filter = it) }
            .map { nodes -> nodes.filter { it.num != destNum && it.hasPKC } }
            .stateInWhileSubscribed(emptyList())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun perform(node: Node, action: RemoteContactAction) = send(node.num, action) {
        when (action) {
            RemoteContactAction.ADD_CONTACT -> radioController.addRemoteContact(destNum, node.toSharedContact())
            RemoteContactAction.FAVORITE -> radioController.setRemoteFavorite(destNum, node.num, favorite = true)
            RemoteContactAction.UNFAVORITE -> radioController.setRemoteFavorite(destNum, node.num, favorite = false)
        }
    }

    fun addContactFromUrl(url: String) {
        val contact = safeCatching { CommonUri.parse(url.trim()).toSharedContact() }.getOrNull()
        when {
            contact == null -> settle(null, RemoteContactAction.ADD_CONTACT, RemoteWriteStatus.INVALID_URL)
            contact.hasNoPublicKey() -> settle(null, RemoteContactAction.ADD_CONTACT, RemoteWriteStatus.NO_PUBLIC_KEY)
            else -> send(null, RemoteContactAction.ADD_CONTACT) { radioController.addRemoteContact(destNum, contact) }
        }
    }

    private fun send(nodeNum: Int?, action: RemoteContactAction, write: suspend () -> Boolean) {
        if (_lastResult.value?.status == RemoteWriteStatus.SENDING) return
        settle(nodeNum, action, RemoteWriteStatus.SENDING)
        viewModelScope.launch {
            val acknowledged =
                safeCatching { ensureRemoteAdminSession(destNum).isUsable() && write() }.getOrDefault(false)
            val status = if (acknowledged) RemoteWriteStatus.ACKNOWLEDGED else RemoteWriteStatus.NOT_ACKNOWLEDGED
            settle(nodeNum, action, status)
        }
    }

    private fun settle(nodeNum: Int?, action: RemoteContactAction, status: RemoteWriteStatus) {
        _lastResult.value = RemoteWriteResult(nodeNum, action, status)
    }

    private fun EnsureSessionResult.isUsable() =
        this == EnsureSessionResult.AlreadyActive || this == EnsureSessionResult.Refreshed

    private fun SharedContact.hasNoPublicKey() = user?.public_key?.size?.let { it == 0 } ?: true
}
