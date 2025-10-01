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

package com.geeksville.mesh.ui.map

import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.map.BaseMapViewModel
import javax.inject.Inject

@HiltViewModel
class MapViewModel
@Inject
constructor(
    mapPrefs: MapPrefs,
    packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    serviceRepository: ServiceRepository,
    radioConfigRepository: RadioConfigRepository,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, serviceRepository) {

    var mapStyleId: Int
        get() = mapPrefs.mapStyle
        set(value) {
            mapPrefs.mapStyle = value
        }

    val localConfig =
        radioConfigRepository.localConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalConfig.getDefaultInstance(),
        )

    val config
        get() = localConfig.value

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
}
