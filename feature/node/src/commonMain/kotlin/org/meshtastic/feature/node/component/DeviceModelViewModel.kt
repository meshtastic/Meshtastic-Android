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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.DeviceModelRepository
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.feature.node.model.GlbBounds
import kotlin.time.Duration.Companion.seconds

sealed interface DeviceModelState {
    data object Loading : DeviceModelState

    data object Failed : DeviceModelState

    /** [copyright] is the model's own notice, shown wherever the model is; [screen] is its display glass, if marked. */
    data class Ready(
        val imageFile: String,
        val glb: ByteArray,
        val bounds: GlbBounds,
        val copyright: String?,
        val screen: GlbBounds? = null,
    ) : DeviceModelState {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Ready && imageFile == other.imageFile && bounds == other.bounds)

        override fun hashCode(): Int = 31 * imageFile.hashCode() + bounds.hashCode()
    }
}

/** Fetches a device's GLB and the bounds the viewer frames it with; one model at a time, the last request wins. */
@KoinViewModel
class DeviceModelViewModel(
    private val repository: DeviceModelRepository,
    private val displayMirrorManager: DisplayMirrorManager,
    private val adminController: AdminController,
    nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val _state = MutableStateFlow<DeviceModelState>(DeviceModelState.Loading)
    val state: StateFlow<DeviceModelState> = _state.asStateFlow()

    /** The connected device's live screen while [setMirror] has it armed. */
    val screenFrame: StateFlow<MirrorFrame?> = displayMirrorManager.frame

    /**
     * Only firmware that can stream the display reports one in its metadata, so without it arming the mirror would go
     * unanswered and the model's screen would stay blank.
     */
    val supported: StateFlow<Boolean> =
        nodeRepository.ourNodeInfo
            .map { it?.metadata?.display != null }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Arming is an admin write, which managed mode forbids.
    private val managed: StateFlow<Boolean> =
        radioConfigRepository.localConfigFlow
            .map { it.security?.is_managed == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var mirroring = false
    private var wanted = false
    private var pendingDisarm: Job? = null
    private var loading: Job? = null
    private var loadedImageFile: String? = null

    init {
        // The dialog can open before the handshake has delivered the display; arm once it does.
        viewModelScope.launch { supported.collect { if (it) arm() } }
    }

    fun load(imageFile: String) = loadModel(imageFile) { repository.getModel(imageFile) }

    /** Swaps in Chirpy, the mascot, bundled with the app: the viewer's easter egg. */
    fun showChirpy() = loadModel(CHIRPY_KEY) {
        try {
            Res.readBytes(CHIRPY_MODEL)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun loadModel(key: String, fetch: suspend () -> ByteArray?) {
        if (loadedImageFile == key) return
        loadedImageFile = key
        loading?.cancel()
        _state.value = DeviceModelState.Loading
        loading = viewModelScope.launch {
            val glb = fetch()
            // Parsing walks the whole file, screen detection every triangle of it: off the main thread.
            val parsed = glb?.let { bytes ->
                withContext(dispatchers.default) {
                    GlbBounds.parse(bytes)?.let { bounds ->
                        DeviceModelState.Ready(
                            key,
                            bytes,
                            bounds,
                            GlbBounds.copyright(bytes),
                            GlbBounds.screen(bytes),
                        )
                    }
                }
            }
            _state.value =
                if (parsed != null) {
                    parsed
                } else {
                    // Lets the next open retry rather than replay the failure.
                    loadedImageFile = null
                    DeviceModelState.Failed
                }
        }
    }

    /** The renderer rejected a model the parser accepted: show the illustration instead. */
    fun markFailed() {
        // Forgets the model too, so asking for it again loads it rather than replaying the failure.
        loading?.cancel()
        loadedImageFile = null
        _state.value = DeviceModelState.Failed
    }

    fun setMirror(enabled: Boolean) {
        wanted = enabled
        if (enabled) {
            pendingDisarm?.cancel()
            pendingDisarm = null
            arm()
        } else if (mirroring && pendingDisarm == null) {
            // A rotation tears the dialog down and builds it again at once; keep the stream over that gap rather than
            // disarming, re-arming and showing a blank screen until the device repaints.
            pendingDisarm = viewModelScope.launch {
                delay(MIRROR_DISARM_GRACE)
                disarm()
            }
        }
    }

    private fun arm() {
        if (!wanted || mirroring || managed.value || !supported.value) return
        displayMirrorManager.reset()
        adminController.setDisplayMirror(true)
        mirroring = true
    }

    private fun disarm() {
        pendingDisarm = null
        if (!mirroring) return
        adminController.setDisplayMirror(false)
        mirroring = false
    }

    override fun onCleared() {
        pendingDisarm?.cancel()
        disarm()
    }

    companion object {
        internal const val CHIRPY_MODEL = "files/models/chirpy.glb"
        private const val CHIRPY_KEY = "chirpy"
    }
}

private val MIRROR_DISARM_GRACE = 3.seconds
