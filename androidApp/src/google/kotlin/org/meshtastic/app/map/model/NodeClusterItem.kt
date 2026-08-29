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
package org.meshtastic.app.map.model

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.precisionRadiusMetersOrNull
import org.meshtastic.feature.map.MapNodePolicy

data class NodeClusterItem(
    val node: Node,
    val nodePosition: LatLng,
    val nodeTitle: String,
    val nodeSnippet: String,
    val myNodeNum: Int? = null,
) : ClusterItem {
    override val position: LatLng
        get() = nodePosition

    override val title: String
        get() = nodeTitle

    override val snippet: String
        get() = nodeSnippet

    override val zIndex: Float
        get() = MapNodePolicy.priorityOf(node, myNodeNum).toFloat()

    fun getPrecisionMeters(): Double? = precisionRadiusMetersOrNull(node.position.precision_bits)
}
