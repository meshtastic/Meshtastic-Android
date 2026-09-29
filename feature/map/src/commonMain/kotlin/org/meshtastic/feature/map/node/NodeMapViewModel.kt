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
package org.meshtastic.feature.map.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class NodeMapViewModel(savedStateHandle: SavedStateHandle, nodeRepository: NodeRepository) : ViewModel() {
    private val destNumFromRoute = savedStateHandle.get<Int>("destNum")
    private val manualDestNum = MutableStateFlow<Int?>(null)

    private val destNumFlow =
        combine(MutableStateFlow(destNumFromRoute), manualDestNum) { route, manual -> manual ?: route ?: 0 }

    fun setDestNum(num: Int) {
        manualDestNum.value = num
    }

    val node =
        destNumFlow
            .flatMapLatest { destNum -> nodeRepository.nodeDBbyNum.mapLatest { it[destNum] } }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = null)
}
