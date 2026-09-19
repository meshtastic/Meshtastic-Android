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
package org.meshtastic.feature.node.list

import org.meshtastic.core.model.Node

/**
 * The nodes the unheard banner counts and its one-tap clear would remove.
 *
 * Deliberately narrower than [Node.isUnheardOnCurrentLora] alone, because this one deletes:
 * - **Favourites are exempt.** An explicit keep, and it is also what firmware marks a node added as a shared contact,
 *   which has never been heard over RF and would otherwise always qualify.
 * - **The connected radio is exempt**, and nothing is offered at all until [ourNum] is known. It and the node list
 *   arrive on independent flows, so the list can hold the local node while [ourNum] is still null; offering then would
 *   risk removing the user's own node.
 * - **MQTT nodes are exempt** via [Node.isUnheardOnCurrentLora]: they read unheard permanently, and removing one
 *   achieves nothing because the next uplinked packet brings it back.
 */
internal fun selectRemovableUnheardNodes(nodes: List<Node>, ourNum: Int?): List<Node> {
    if (ourNum == null) return emptyList()
    return nodes.filter { it.isUnheardOnCurrentLora && !it.isFavorite && it.num != ourNum }
}
