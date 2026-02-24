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
package com.geeksville.mesh.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

data class LocalStatsWidgetUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val myNodeInfo: MyNodeEntity? = null,
    val nodes: Map<Int, Node> = emptyMap(),
    val stats: org.meshtastic.proto.LocalStats? = null,
    val localNode: Node? = null,
)

@Singleton
class LocalStatsWidgetStateProvider
@Inject
constructor(
    nodeRepository: NodeRepository,
    serviceRepository: ServiceRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val state: StateFlow<LocalStatsWidgetUiState> =
        combine(
            serviceRepository.connectionState,
            nodeRepository.myNodeInfo,
            nodeRepository.nodeDBbyNum,
            nodeRepository.localStats,
            nodeRepository.ourNodeInfo,
        ) { connectionState, myNodeInfo, nodes, stats, localNode ->
            LocalStatsWidgetUiState(
                connectionState = connectionState,
                myNodeInfo = myNodeInfo,
                nodes = nodes,
                stats = stats,
                localNode = localNode,
            )
        }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = LocalStatsWidgetUiState())
}
