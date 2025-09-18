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

package com.geeksville.mesh.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.android.prefs.UiPrefs
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
    private val uiPrefs: UiPrefs,
) : ViewModel() {
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeRepository.myNodeInfo

    val ourNodeInfo: StateFlow<Node?>
        get() = nodeRepository.ourNodeInfo

    val isConnected =
        radioConfigRepository.connectionState
            .map { it.isConnected() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalConfig.getDefaultInstance(),
        )

    val meshService: IMeshService?
        get() = radioConfigRepository.meshService

    val provideLocation: StateFlow<Boolean>
        get() =
            myNodeInfo
                .flatMapLatest { myNodeEntity ->
                    // When myNodeInfo changes, set up emissions for the "provide-location-nodeNum" pref.
                    if (myNodeEntity == null) {
                        flowOf(false)
                    } else {
                        uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _excludedModulesUnlocked = MutableStateFlow(false)
    val excludedModulesUnlocked: StateFlow<Boolean> = _excludedModulesUnlocked.asStateFlow()

    fun setProvideLocation(value: Boolean) {
        myNodeInfo.value?.myNodeNum?.let { uiPrefs.setShouldProvideNodeLocation(it, value) }
    }

    fun unlockExcludedModules() {
        viewModelScope.launch { _excludedModulesUnlocked.value = true }
    }
}
