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
package org.meshtastic.app.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.meshtastic.core.model.Node

/** Resolves a one-shot Site Planner route request against the live node database. */
internal class SitePlannerRequestState(nodesByNum: StateFlow<Map<Int, Node>>) {
    private val routeRequest = MutableStateFlow<SitePlannerRouteRequest>(SitePlannerRouteRequest.None)

    val request: Flow<Node?> =
        combine(routeRequest, nodesByNum) { request, nodes ->
            (request as? SitePlannerRouteRequest.Pending)?.nodeNum?.let { nodes[it] }
        }

    fun setNodeNum(nodeNum: Int?) {
        routeRequest.update { current ->
            when {
                nodeNum == null -> SitePlannerRouteRequest.None
                current is SitePlannerRouteRequest.Consumed && current.nodeNum == nodeNum -> current
                else -> SitePlannerRouteRequest.Pending(nodeNum)
            }
        }
    }

    fun consume(nodeNum: Int) {
        routeRequest.update { current ->
            if (current is SitePlannerRouteRequest.Pending && current.nodeNum == nodeNum) {
                SitePlannerRouteRequest.Consumed(nodeNum)
            } else {
                current
            }
        }
    }
}

/** Retained in the entry-scoped ViewModel so returning to its composition cannot re-arm a consumed route argument. */
private sealed interface SitePlannerRouteRequest {
    data object None : SitePlannerRouteRequest

    data class Pending(val nodeNum: Int) : SitePlannerRouteRequest

    data class Consumed(val nodeNum: Int) : SitePlannerRouteRequest
}
