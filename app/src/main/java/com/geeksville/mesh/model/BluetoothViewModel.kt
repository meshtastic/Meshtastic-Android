/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Thin view model which adapts the view layer to the `BluetoothRepository`.
 */
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
) : ViewModel() {
    /**
     * Called when permissions have been updated.  This causes an explicit refresh of the
     * bluetooth state.
     */
    fun permissionsUpdated() = bluetoothRepository.refreshState()

    val enabled = bluetoothRepository.state.map { it.enabled }.asLiveData()
}