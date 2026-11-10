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
package org.meshtastic.core.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.common.util.isValidDeviceAddress
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs

/**
 * Turns a Live Update off once what it shows is gone: the tracked node was deleted or ignored, or the user disconnected
 * or switched radios. A dropped link changes neither, so the mode survives it and the notification shows the state.
 */
class LiveUpdateGuard(
    private val prefs: LiveUpdatePrefs,
    private val nodes: () -> Flow<Map<Int, Node>>,
    private val selectedAddress: Flow<String?>,
    private val radioOperationLock: RadioOperationLock,
) {
    suspend fun run() = coroutineScope {
        launch { watchTrackedNode() }
        launch { watchSelection() }
    }

    private suspend fun watchTrackedNode() {
        prefs.mode.collectLatest { mode ->
            if (mode !is LiveUpdateMode.TrackNode) return@collectLatest
            var seen = false
            nodes().collect { db ->
                val node = db[mode.nodeNum]
                if (trackedNodeEnds(node, seen)) {
                    prefs.setMode(LiveUpdateMode.Off)
                } else if (node != null) {
                    seen = true
                }
            }
        }
    }

    private suspend fun watchSelection() {
        var previous: String? = null
        selectedAddress.collect { current ->
            val firmwareBusy =
                radioOperationLock.activeOperations.any {
                    it == RadioOperation.FirmwareUpdate || it == RadioOperation.FirmwareMaintenance
                }
            if (prefs.mode.value != LiveUpdateMode.Off && selectionEndsLiveUpdate(previous, current, firmwareBusy)) {
                prefs.setMode(LiveUpdateMode.Off)
            }
            previous = current
        }
    }
}

/**
 * Whether a tracked node's latest row ends tracking. A node absent before it was ever seen is the database still
 * loading, not a deletion, so only a node that disappears after being seen counts.
 */
internal fun trackedNodeEnds(node: Node?, seenBefore: Boolean): Boolean = node?.isIgnored ?: seenBefore

/**
 * Whether a change of the selected radio ends a Live Update. The firmware flow deselects and reselects the radio around
 * a flash, which is not the user leaving it.
 */
internal fun selectionEndsLiveUpdate(previous: String?, current: String?, firmwareBusy: Boolean): Boolean =
    !firmwareBusy && isValidDeviceAddress(previous) && current != previous
