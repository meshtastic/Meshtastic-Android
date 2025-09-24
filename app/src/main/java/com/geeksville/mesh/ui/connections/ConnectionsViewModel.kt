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
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.prefs.ui.UiPrefs
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel
@Inject
constructor(
    radioConfigRepository: RadioConfigRepository,
    nodeRepository: NodeRepository,
    bluetoothRepository: BluetoothRepository,
    private val uiPrefs: UiPrefs,
) : ViewModel() {
    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalConfig.getDefaultInstance(),
        )

    val connectionState = radioConfigRepository.connectionState

    val myNodeInfo: StateFlow<MyNodeEntity?> = nodeRepository.myNodeInfo

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val bluetoothState = bluetoothRepository.state

    private val _hasShownNotPairedWarning = MutableStateFlow(uiPrefs.hasShownNotPairedWarning)
    val hasShownNotPairedWarning: StateFlow<Boolean> = _hasShownNotPairedWarning.asStateFlow()

    fun suppressNoPairedWarning() {
        _hasShownNotPairedWarning.value = true
        uiPrefs.hasShownNotPairedWarning = true
    }
}
