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
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.node.detail.NodeDetailViewModel
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase

@KoinViewModel
class AndroidNodeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    nodeManagementActions: NodeManagementActions,
    nodeRequestActions: NodeRequestActions,
    serviceRepository: ServiceRepository,
    getNodeDetailsUseCase: GetNodeDetailsUseCase,
) : NodeDetailViewModel(
    savedStateHandle,
    nodeManagementActions,
    nodeRequestActions,
    serviceRepository,
    getNodeDetailsUseCase,
)
