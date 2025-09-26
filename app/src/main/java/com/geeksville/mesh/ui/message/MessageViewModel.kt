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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MessageViewModel
@Inject
constructor(
    nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val channels =
        radioConfigRepository.channelSetFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            channelSet {},
        )

    val quickChatActions =
        quickChatActionRepository
            .getAllActions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
