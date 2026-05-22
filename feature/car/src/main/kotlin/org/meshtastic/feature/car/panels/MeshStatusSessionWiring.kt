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
package org.meshtastic.feature.car.panels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.meshtastic.core.model.ConnectionState

/** Wires MeshStatusPanel to data sources during a car session. Attach in onCreateScreen, detach in onDestroy. */
class MeshStatusSessionWiring(private val panel: MeshStatusPanel) {
    private var connectionJob: Job? = null
    private var nodeCountJob: Job? = null
    private var messageTimeJob: Job? = null

    fun attach(
        scope: CoroutineScope,
        connectionFlow: Flow<ConnectionState>,
        nodeCountFlow: Flow<Int>,
        lastMessageTimeFlow: Flow<Long>,
        meshNameFlow: Flow<String?>,
    ) {
        connectionJob = scope.launch { connectionFlow.collect { panel.updateConnectionStatus(it) } }
        nodeCountJob = scope.launch { nodeCountFlow.collect { panel.updateNodeCount(it) } }
        messageTimeJob = scope.launch { lastMessageTimeFlow.collect { panel.updateLastMessageTime(it) } }
        scope.launch { meshNameFlow.collect { panel.updateMeshName(it) } }
    }

    fun detach() {
        connectionJob?.cancel()
        nodeCountJob?.cancel()
        messageTimeJob?.cancel()
    }
}
