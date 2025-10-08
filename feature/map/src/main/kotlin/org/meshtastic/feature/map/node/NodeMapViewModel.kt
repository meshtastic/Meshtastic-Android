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

package org.meshtastic.feature.map.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.proto.toPosition
import org.meshtastic.feature.map.model.CustomTileSource
import org.meshtastic.proto.MeshProtos.Position
import org.meshtastic.proto.Portnums.PortNum
import javax.inject.Inject

@HiltViewModel
class NodeMapViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    nodeRepository: NodeRepository,
    meshLogRepository: MeshLogRepository,
    buildConfigProvider: BuildConfigProvider,
    private val mapPrefs: MapPrefs,
) : ViewModel() {
    private val destNum = savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum

    val node =
        nodeRepository.nodeDBbyNum
            .mapLatest { it[destNum] }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val applicationId = buildConfigProvider.applicationId

    val positionLogs: StateFlow<List<Position>> =
        meshLogRepository
            .getMeshPacketsFrom(destNum!!, PortNum.POSITION_APP_VALUE)
            .map { packets ->
                packets
                    .mapNotNull { it.toPosition() }
                    .asFlow()
                    .distinctUntilChanged { old, new ->
                        old.time == new.time || (old.latitudeI == new.latitudeI && old.longitudeI == new.longitudeI)
                    }
                    .toList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val tileSource
        get() = CustomTileSource.getTileSource(mapPrefs.mapStyle)
}
