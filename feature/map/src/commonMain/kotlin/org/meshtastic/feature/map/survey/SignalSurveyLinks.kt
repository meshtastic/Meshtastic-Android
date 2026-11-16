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
package org.meshtastic.feature.map.survey

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.Quality

/** A line from a survey square to a node heard directly there. */
data class SurveyLink(
    val nodeNum: Int,
    val name: String,
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
    val quality: Quality,
    val distanceM: Double,
)

/** The lines for one square, and how many direct senders could not get one because they share no position. */
data class SurveyLinks(val links: List<SurveyLink> = emptyList(), val unplaced: Int = 0)

private const val CENTER = 0.5

/** The lines from [selected] to each node heard directly in it, using [nodes] for where those nodes say they are. */
internal fun surveyLinks(selected: SurveyCellUi, nodes: Map<Int, Node>): SurveyLinks {
    val cell = selected.cell
    val latitude = (cell.south + cell.north) * CENTER
    val longitude = (cell.west + cell.east) * CENTER
    val links =
        selected.directQuality
            .mapNotNull { (num, quality) ->
                val node = nodes[num]?.takeIf { it.validPosition != null } ?: return@mapNotNull null
                SurveyLink(
                    nodeNum = num,
                    name = node.user.short_name.ifBlank { node.user.long_name },
                    fromLatitude = latitude,
                    fromLongitude = longitude,
                    toLatitude = node.latitude,
                    toLongitude = node.longitude,
                    quality = quality,
                    distanceM = latLongToMeter(latitude, longitude, node.latitude, node.longitude),
                )
            }
            .sortedBy { it.distanceM }
    return SurveyLinks(links, unplaced = selected.directQuality.size - links.size)
}
