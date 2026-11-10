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
package org.meshtastic.feature.map.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.feature.map.hud.MeshHudScreen

fun EntryProviderScope<NavKey>.mapGraph(backStack: NavBackStack<NavKey>) {
    entry<MapRoute.Map> { args ->
        val mapScreen = org.meshtastic.core.ui.util.LocalMapMainScreenProvider.current
        mapScreen(
            { id -> backStack.add(NodesRoute.NodeDetail(id)) }, // onClickNodeChip
            { id -> backStack.add(NodesRoute.NodeDetail(id)) }, // navigateToNodeDetails
            args.waypointId,
            args.sitePlannerNodeNum,
            { backStack.add(MapRoute.MeshHud()) }, // onOpenMeshHud
        )
    }

    // No pane metadata: the camera view takes the whole window.
    entry<MapRoute.MeshHud> { args ->
        MeshHudScreen(
            focusNodeNum = args.focusNodeNum,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onOpenNode = { num -> backStack.add(NodesRoute.NodeDetail(num)) },
            tracerouteDestNum = args.tracerouteDestNum,
            tracerouteRequestId = args.tracerouteRequestId,
            tracerouteLogUuid = args.tracerouteLogUuid,
            trackNodeNum = args.trackNodeNum,
            // The map opens the waypoint's own dialog, whose edit has the pickers. The map replaces this view and
            // the map it was opened from, so back does not pass through either again.
            onEditWaypoint = { id ->
                val below = backStack.getOrNull(backStack.lastIndex - 1)
                backStack.add(MapRoute.Map(waypointId = id))
                backStack.removeAt(backStack.lastIndex - 1)
                if (below is MapRoute.Map) backStack.removeAt(backStack.lastIndex - 1)
            },
        )
    }
}
