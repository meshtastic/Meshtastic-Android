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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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

/** The outcome of one write to the remote node's node DB, shown once. */
sealed interface RemoteContactsEvent {
    data object Acknowledged : RemoteContactsEvent

    data object NotAcknowledged : RemoteContactsEvent

    data object InvalidUrl : RemoteContactsEvent

    data object NoPublicKey : RemoteContactsEvent
}

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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<RemoteContactsEvent>(Channel.BUFFERED)
    val events: Flow<RemoteContactsEvent> = _events.receiveAsFlow()

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

    fun addContact(node: Node) = send { radioController.addRemoteContact(destNum, node.toSharedContact()) }

    fun addContactFromUrl(url: String) {
        val contact = safeCatching { CommonUri.parse(url.trim()).toSharedContact() }.getOrNull()
        when {
            contact == null -> _events.trySend(RemoteContactsEvent.InvalidUrl)
            contact.hasNoPublicKey() -> _events.trySend(RemoteContactsEvent.NoPublicKey)
            else -> send { radioController.addRemoteContact(destNum, contact) }
        }
    }

    fun setFavorite(node: Node, favorite: Boolean) = send {
        radioController.setRemoteFavorite(destNum, node.num, favorite)
    }

    private fun send(write: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val acknowledged =
                try {
                    ensureRemoteAdminSession(destNum).isUsable() && write()
                } finally {
                    _busy.value = false
                }
            _events.send(
                if (acknowledged) RemoteContactsEvent.Acknowledged else RemoteContactsEvent.NotAcknowledged,
            )
        }
    }

    private fun EnsureSessionResult.isUsable() =
        this == EnsureSessionResult.AlreadyActive || this == EnsureSessionResult.Refreshed

    private fun SharedContact.hasNoPublicKey() = user?.public_key?.size?.let { it == 0 } ?: true
}
