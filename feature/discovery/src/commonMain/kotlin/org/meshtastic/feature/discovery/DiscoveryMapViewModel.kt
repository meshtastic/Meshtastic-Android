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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class DiscoveryMapViewModel(@InjectedParam private val sessionId: Long, private val discoveryDao: DiscoveryDao) :
    ViewModel() {

    val session: StateFlow<DiscoverySessionEntity?> =
        discoveryDao.getSessionFlow(sessionId).stateInWhileSubscribed(initialValue = null)

    private val _allNodes = MutableStateFlow<List<DiscoveredNodeEntity>>(emptyList())
    val allNodes: StateFlow<List<DiscoveredNodeEntity>> = _allNodes.asStateFlow()

    init {
        loadAllNodes()
    }

    private fun loadAllNodes() {
        safeLaunch(tag = "loadAllNodes") {
            val results = discoveryDao.getPresetResults(sessionId)
            val nodes = results.flatMap { discoveryDao.getDiscoveredNodes(it.id) }
            // Deduplicate by nodeNum — keep the entry with strongest signal
            val deduped =
                nodes.groupBy { it.nodeNum }.values.map { dupes -> dupes.maxByOrNull { it.snr } ?: dupes.first() }
            _allNodes.value = deduped
        }
    }
}
