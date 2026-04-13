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
package org.meshtastic.feature.map.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position

@KoinViewModel
class NodeMapViewModel(
    savedStateHandle: SavedStateHandle,
    nodeRepository: NodeRepository,
    meshLogRepository: MeshLogRepository,
) : ViewModel() {
    private val destNum = savedStateHandle.get<Int>("destNum") ?: 0

    private val destNumFlow = MutableStateFlow(destNum)

    val node =
        destNumFlow
            .flatMapLatest { destNum -> nodeRepository.nodeDBbyNum.mapLatest { it[destNum] } }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = null)

    private val ourNodeNumFlow = nodeRepository.myNodeInfo.map { it?.myNodeNum }.distinctUntilChanged()

    val positionLogs: StateFlow<List<Position>> =
        ourNodeNumFlow
            .map { ourNodeNum -> if (destNum == ourNodeNum) MeshLog.NODE_NUM_LOCAL else destNum }
            .distinctUntilChanged()
            .flatMapLatest { logId ->
                meshLogRepository.getMeshPacketsFrom(logId, PortNum.POSITION_APP.value).map { packets ->
                    packets
                        .mapNotNull { it.toPosition() }
                        .filterConsecutiveDuplicates { old, new ->
                            old.time == new.time ||
                                (old.latitude_i == new.latitude_i && old.longitude_i == new.longitude_i)
                        }
                }
            }
            .stateInWhileSubscribed(initialValue = emptyList())
}

/**
 * Filters consecutive duplicate elements from a list, similar to [Sequence.distinctUntilChanged]. An element is
 * considered a duplicate if [predicate] returns `true` for it and the previous element.
 */
private fun <T> List<T>.filterConsecutiveDuplicates(predicate: (old: T, new: T) -> Boolean): List<T> {
    if (size <= 1) return this
    return buildList {
        add(this@filterConsecutiveDuplicates.first())
        for (i in 1 until this@filterConsecutiveDuplicates.size) {
            if (!predicate(this@filterConsecutiveDuplicates[i - 1], this@filterConsecutiveDuplicates[i])) {
                add(this@filterConsecutiveDuplicates[i])
            }
        }
    }
}
