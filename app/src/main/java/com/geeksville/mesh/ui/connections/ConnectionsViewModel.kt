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

package com.geeksville.mesh.ui.connections

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeDB: NodeRepository,
    private val uiPrefs: UiPrefs,
) : ViewModel() {
    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalConfig.getDefaultInstance(),
        )

    val connectionState
        get() = radioConfigRepository.connectionState

    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val ourNodeInfo: StateFlow<Node?>
        get() = nodeDB.ourNodeInfo

    private val meshService: IMeshService?
        get() = radioConfigRepository.meshService

    private val _hasShownNotPairedWarning = MutableStateFlow(uiPrefs.hasShownNotPairedWarning)
    val hasShownNotPairedWarning: StateFlow<Boolean> = _hasShownNotPairedWarning.asStateFlow()

    fun suppressNoPairedWarning() {
        _hasShownNotPairedWarning.value = true
        uiPrefs.hasShownNotPairedWarning = true
    }

    fun refreshProvideLocation() {
        viewModelScope.launch { setProvideLocation(getProvidePref()) }
    }

    fun setProvideLocation(value: Boolean) {
        viewModelScope.launch {
            uiPrefs.setShouldProvideNodeLocation(myNodeNum, value)
            if (value) {
                meshService?.startProvideLocation()
            } else {
                meshService?.stopProvideLocation()
            }
        }
    }

    private fun getProvidePref(): Boolean = uiPrefs.shouldProvideNodeLocation(myNodeNum)
}
