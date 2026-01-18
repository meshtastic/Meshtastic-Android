/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.SharedContact
import javax.inject.Inject

@HiltViewModel
class SharedContactViewModel
@Inject
constructor(
    nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val unfilteredNodes: StateFlow<List<Node>> =
        nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    fun addSharedContact(sharedContact: SharedContact) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.ImportContact(sharedContact)) }
}
