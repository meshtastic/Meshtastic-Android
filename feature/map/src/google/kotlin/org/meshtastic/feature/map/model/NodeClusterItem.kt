/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.map.model

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import org.meshtastic.core.database.model.Node

data class NodeClusterItem(val node: Node, val nodePosition: LatLng, val nodeTitle: String, val nodeSnippet: String) :
    ClusterItem {
    override fun getPosition(): LatLng = nodePosition

    override fun getTitle(): String = nodeTitle

    override fun getSnippet(): String = nodeSnippet

    override fun getZIndex(): Float? = null

    fun getPrecisionMeters(): Double? {
        val precisionMap =
            mapOf(
                10 to 23345.484932,
                11 to 11672.7369,
                12 to 5836.36288,
                13 to 2918.175876,
                14 to 1459.0823719999053,
                15 to 729.53562,
                16 to 364.7622,
                17 to 182.375556,
                18 to 91.182212,
                19 to 45.58554,
            )
        return precisionMap[this.node.position.precision_bits ?: 0]
    }
}
