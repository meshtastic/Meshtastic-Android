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
package org.meshtastic.feature.node.navigation

import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.navigation.NodesRoute

/**
 * Pushes [route] from a node detail screen. A mesh view opened from node screens that were themselves opened from a
 * mesh view replaces that mesh view rather than stacking a second one.
 */
internal fun MutableList<NavKey>.navigateFromNodeDetail(route: NavKey) {
    val meshHudIndex = if (route is MapRoute.MeshHud) indexOfMeshHudUnderNodeDetail() else -1
    // Add before removing so the back stack is never momentarily empty.
    add(route)
    if (meshHudIndex >= 0) {
        repeat(lastIndex - meshHudIndex) { removeAt(meshHudIndex) }
    }
}

private fun List<NavKey>.indexOfMeshHudUnderNodeDetail(): Int {
    var index = lastIndex
    while (index >= 0 && this[index].isNodeScreen()) index--
    return if (index in 0 until lastIndex && this[index] is MapRoute.MeshHud) index else -1
}

private fun NavKey.isNodeScreen(): Boolean = this is NodesRoute.NodeDetail || this is NodeDetailRoute
