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
package org.meshtastic.feature.node.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.DeviceModelRepository
import org.meshtastic.feature.node.model.GlbBounds

sealed interface DeviceModelState {
    data object Loading : DeviceModelState

    data object Failed : DeviceModelState

    /** [copyright] is the model's own notice, shown wherever the model is. */
    data class Ready(val imageFile: String, val glb: ByteArray, val bounds: GlbBounds, val copyright: String?) :
        DeviceModelState {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Ready && imageFile == other.imageFile && bounds == other.bounds)

        override fun hashCode(): Int = 31 * imageFile.hashCode() + bounds.hashCode()
    }
}

/** Fetches a device's GLB and the bounds the viewer frames it with; one model at a time, the last request wins. */
@KoinViewModel
class DeviceModelViewModel(private val repository: DeviceModelRepository) : ViewModel() {
    private val _state = MutableStateFlow<DeviceModelState>(DeviceModelState.Loading)
    val state: StateFlow<DeviceModelState> = _state.asStateFlow()

    private var loading: Job? = null
    private var loadedImageFile: String? = null

    fun load(imageFile: String) {
        if (loadedImageFile == imageFile) return
        loadedImageFile = imageFile
        loading?.cancel()
        _state.value = DeviceModelState.Loading
        loading = viewModelScope.launch {
            val glb = repository.getModel(imageFile)
            val bounds = glb?.let(GlbBounds::parse)
            _state.value =
                if (glb != null && bounds != null) {
                    DeviceModelState.Ready(imageFile, glb, bounds, GlbBounds.copyright(glb))
                } else {
                    // Lets the next open retry rather than replay the failure.
                    loadedImageFile = null
                    DeviceModelState.Failed
                }
        }
    }

    /** The renderer rejected a model the parser accepted: show the illustration instead. */
    fun markFailed() {
        _state.value = DeviceModelState.Failed
    }
}
