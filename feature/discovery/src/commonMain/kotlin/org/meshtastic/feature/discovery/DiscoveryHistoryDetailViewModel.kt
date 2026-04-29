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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class DiscoveryHistoryDetailViewModel(
    @InjectedParam private val sessionId: Long,
    private val discoveryDao: DiscoveryDao,
) : ViewModel() {

    val session: StateFlow<DiscoverySessionEntity?> =
        discoveryDao.getSessionFlow(sessionId).stateInWhileSubscribed(initialValue = null)

    val presetResults: StateFlow<List<DiscoveryPresetResultEntity>> =
        discoveryDao.getPresetResultsFlow(sessionId).stateInWhileSubscribed(initialValue = emptyList())

    private val _nodesByPreset = MutableStateFlow<Map<Long, List<DiscoveredNodeEntity>>>(emptyMap())
    val nodesByPreset: StateFlow<Map<Long, List<DiscoveredNodeEntity>>> = _nodesByPreset.asStateFlow()

    init {
        loadNodes()
    }

    private fun loadNodes() {
        safeLaunch(tag = "loadNodes") {
            val results = discoveryDao.getPresetResults(sessionId)
            val nodesMap = mutableMapOf<Long, List<DiscoveredNodeEntity>>()
            for (result in results) {
                nodesMap[result.id] = discoveryDao.getDiscoveredNodes(result.id)
            }
            _nodesByPreset.value = nodesMap
        }
    }
}
