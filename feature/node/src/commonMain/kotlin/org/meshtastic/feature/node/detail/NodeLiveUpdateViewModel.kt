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
package org.meshtastic.feature.node.detail

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs

/** The node detail shortcut into the Live Update: track this node, or stop tracking it. */
@KoinViewModel
class NodeLiveUpdateViewModel(private val prefs: LiveUpdatePrefs) : ViewModel() {
    val mode: StateFlow<LiveUpdateMode> = prefs.mode

    fun setTracking(nodeNum: Int, track: Boolean) {
        when {
            track -> prefs.setMode(LiveUpdateMode.TrackNode(nodeNum))
            prefs.mode.value == LiveUpdateMode.TrackNode(nodeNum) -> prefs.setMode(LiveUpdateMode.Off)
        }
    }
}
