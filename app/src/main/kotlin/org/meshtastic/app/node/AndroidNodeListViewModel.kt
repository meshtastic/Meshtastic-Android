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
package org.meshtastic.app.node

import androidx.lifecycle.SavedStateHandle
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.domain.usecase.GetFilteredNodesUseCase
import org.meshtastic.feature.node.list.NodeFilterPreferences
import org.meshtastic.feature.node.list.NodeListViewModel

@KoinViewModel
class AndroidNodeListViewModel(
    savedStateHandle: SavedStateHandle,
    nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    nodeManagementActions: NodeManagementActions,
    getFilteredNodesUseCase: GetFilteredNodesUseCase,
    nodeFilterPreferences: NodeFilterPreferences,
) : NodeListViewModel(
    savedStateHandle,
    nodeRepository,
    radioConfigRepository,
    serviceRepository,
    radioController,
    nodeManagementActions,
    getFilteredNodesUseCase,
    nodeFilterPreferences,
)
