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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.ONLINE_WINDOW_SECONDS
import org.meshtastic.proto.Config

/**
 * Which nodes a map shows, and which of them draws on top.
 *
 * Renderer-independent on purpose. Both map engines answer these two questions, and until this existed each answered
 * them separately — the Google map inline in `MapView`, the MapLibre map in its own module, which the Google flavour
 * cannot even depend on. They had already drifted apart on the first question.
 */
object MapNodePolicy {

    /** Drawn above everything else: this node and favorites. */
    const val PRIORITY_PROMINENT = 5

    /** Everything else, with nothing to distinguish between them. */
    const val PRIORITY_ORDINARY = 4

    /**
     * How far up the stack a node draws.
     *
     * The Google map turns this into a marker `zIndex` and the MapLibre map into a symbol sort key, but it is one
     * product decision and it was previously written out as `5.0f`/`5.0f`/`4.0f` in one place and `5`/`4` in the other.
     */
    fun priorityOf(node: Node, myNodeNum: Int?): Int =
        if (node.num == myNodeNum || node.isFavorite) PRIORITY_PROMINENT else PRIORITY_ORDINARY

    /**
     * Applies the map's filter chips to the node list.
     *
     * Pure so it can be tested without a renderer: the filter rules are the part users notice when they go wrong, and
     * they should not need a GPU to verify.
     *
     * Your own node is exempt from both filters. You are always on your own map, whether or not you have favourited
     * yourself and whether or not you have heard from yourself lately — the Google map has always done this, and the
     * MapLibre map's own copy of these rules did not, so filtering to favourites there hid you from yourself.
     *
     * Nodes without a fix are dropped up front. They cannot be drawn, and leaving them in drags a camera that fits to
     * these bounds toward (0, 0) — which is how the OSMdroid map used to open in the Atlantic.
     */
    fun visibleNodes(
        nodes: List<Node>,
        filterState: BaseMapViewModel.MapFilterState,
        nowSeconds: Long,
        myNodeNum: Int?,
    ): List<Node> = nodes.filter { node ->
        node.validPosition != null && (node.num == myNodeNum || node.passesFilters(filterState, nowSeconds))
    }

    /**
     * One node against every filter.
     *
     * The node-level rules match the node list's query exactly, because they carry its labels: direct means `hopsAway
     * == 0` — a node whose distance was never measured (-1) is not direct — and unknown means a node that has not sent
     * a short name.
     *
     * [BaseMapViewModel.MapFilterState.showIgnored] is the one place the map deliberately differs. The node list
     * segregates (`isIgnored == showIgnored`, so switching it on shows *only* ignored nodes); here it reads literally
     * and adds them, because a map of nothing but ignored nodes is not a view anyone asked for.
     */
    private fun Node.passesFilters(filterState: BaseMapViewModel.MapFilterState, nowSeconds: Long): Boolean {
        val secondsSinceHeard = nowSeconds - lastHeard
        val cutoff = filterState.lastHeardFilter.seconds
        return (!filterState.onlyFavorites || isFavorite) &&
            (!isIgnored || filterState.showIgnored) &&
            user.role !in filterState.excludedRoles &&
            (!filterState.onlyOnline || secondsSinceHeard <= ONLINE_WINDOW_SECONDS) &&
            (!filterState.onlyDirect || hopsAway == 0) &&
            (!filterState.excludeMqtt || !viaMqtt) &&
            (filterState.includeUnknown || user.short_name.isNotEmpty()) &&
            (cutoff == LastHeardFilter.Any.seconds || secondsSinceHeard <= cutoff)
    }
}

/**
 * Resolves persisted role names back to roles, dropping any the current protobufs no longer define.
 *
 * Names rather than ordinals, and forgiving of names it does not know: a set written by a newer build (or a role
 * removed from the protos) must not throw on the way back in.
 */
fun decodeExcludedRoles(names: Set<String>): Set<Config.DeviceConfig.Role> =
    names.mapNotNullTo(mutableSetOf()) { name -> Config.DeviceConfig.Role.entries.firstOrNull { it.name == name } }
