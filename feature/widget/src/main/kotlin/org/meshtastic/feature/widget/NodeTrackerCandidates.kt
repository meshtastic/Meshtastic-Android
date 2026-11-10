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
package org.meshtastic.feature.widget

import org.meshtastic.core.model.Node

/**
 * The nodes the tracker picker offers: favorites first, then most recently heard, filtered by [query] against the long
 * name, short name and node id. Our own radio is left out, since Local Stats already covers it, and so are ignored
 * nodes.
 */
internal fun trackerCandidates(nodes: Collection<Node>, query: String, ourNodeNum: Int?): List<Node> {
    val needle = query.trim()
    return nodes
        .asSequence()
        .filter { !it.isIgnored && it.num != ourNodeNum }
        .filter { node ->
            needle.isEmpty() ||
                node.user.long_name.contains(needle, ignoreCase = true) ||
                node.displayName.contains(needle, ignoreCase = true) ||
                node.user.short_name.contains(needle, ignoreCase = true) ||
                node.user.id.contains(needle, ignoreCase = true)
        }
        .sortedWith(compareByDescending<Node> { it.isFavorite }.thenByDescending { it.lastHeard }.thenBy { it.num })
        .toList()
}
