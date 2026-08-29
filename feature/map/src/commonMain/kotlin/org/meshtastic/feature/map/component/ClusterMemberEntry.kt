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
package org.meshtastic.feature.map.component

import org.meshtastic.core.model.Node

/**
 * One node in a cluster the map cannot zoom apart.
 *
 * [node] is nullable because the two maps learn about cluster members differently: the Google map holds the node it
 * clustered, while the MapLibre map reads names off the cluster's own leaf features and may be listing a node the
 * database no longer has. When it is present the row gets the same chip the rest of the app draws.
 */
data class ClusterMemberEntry(val nodeNum: Int, val title: String, val subtitle: String, val node: Node?)
