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
package org.meshtastic.feature.connections

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

/** Starts, switches and stops the Live Update from the Connections screen. */
@KoinViewModel
class LiveUpdateViewModel(private val prefs: LiveUpdatePrefs, nodeRepository: NodeRepository) : ViewModel() {
    val mode: StateFlow<LiveUpdateMode> = prefs.mode

    /** Nodes the user can track, in the order the chooser lists them. */
    val candidates: StateFlow<List<Node>> =
        combine(nodeRepository.nodeDBbyNum, nodeRepository.myNodeInfo) { db, myNodeInfo ->
            liveUpdateCandidates(db.values, myNodeInfo?.myNodeNum)
        }
            .stateInWhileSubscribed(emptyList())

    val trackedNode: StateFlow<Node?> =
        combine(prefs.mode, nodeRepository.nodeDBbyNum) { mode, db ->
            (mode as? LiveUpdateMode.TrackNode)?.let { db[it.nodeNum] }
        }
            .stateInWhileSubscribed(null)

    fun setMode(mode: LiveUpdateMode) = prefs.setMode(mode)
}

/** Every node but our own and ignored ones: favorites first, then the most recently heard. */
fun liveUpdateCandidates(nodes: Collection<Node>, ourNodeNum: Int?): List<Node> = nodes
    .filter { it.num != ourNodeNum && !it.isIgnored }
    .sortedWith(compareByDescending<Node> { it.isFavorite }.thenByDescending { it.lastHeard }.thenBy { it.num })

/** [candidates] whose long name, display name, short name or node id contains [query], ignoring case. */
fun filterLiveUpdateCandidates(candidates: List<Node>, query: String): List<Node> {
    val needle = query.trim()
    if (needle.isEmpty()) return candidates
    return candidates.filter { node ->
        node.user.long_name.contains(needle, ignoreCase = true) ||
            node.displayName.contains(needle, ignoreCase = true) ||
            node.user.short_name.contains(needle, ignoreCase = true) ||
            node.user.id.contains(needle, ignoreCase = true)
    }
}
