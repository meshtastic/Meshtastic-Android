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
package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.meshtastic.feature.map.mapcompose.geo.GeoPoint
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.hasPath
import ovh.plrapps.mapcompose.api.makePathDataBuilder
import ovh.plrapps.mapcompose.api.removePath
import ovh.plrapps.mapcompose.api.removePaths
import ovh.plrapps.mapcompose.ui.state.MapState

/** Adds (or replaces) a lat/lon path, converting through [WebMercator] and skipping degenerate inputs. */
@Suppress("LongParameterList")
internal fun MapState.setLatLonPath(
    id: String,
    points: List<GeoPoint>,
    color: Color,
    width: Dp = 4.dp,
    fillColor: Color? = null,
    zIndex: Float = 0f,
) {
    if (hasPath(id)) removePath(id)
    if (points.size < 2) return
    val builder = makePathDataBuilder()
    points.forEach { point ->
        val p = WebMercator.toNormalized(point.latitude, point.longitude)
        builder.addPoint(p.x, p.y)
    }
    val data = builder.build() ?: return
    addPath(id = id, pathData = data, width = width, color = color, fillColor = fillColor, zIndex = zIndex)
}

/**
 * Reconciles the set of filled polygon paths whose ids start with [group] against [targets]: stale paths are removed,
 * and every target is (re)drawn from [pathFor]'s lat/lon ring with a translucent fill of the item's [color].
 */
@Suppress("LongParameterList")
internal fun <T> MapState.reconcilePaths(
    group: String,
    targets: Map<String, T>,
    pathFor: (T) -> List<GeoPoint>,
    color: (T) -> Color,
    fillAlpha: Float,
    width: Dp = 2.dp,
    zIndex: Float = 0f,
) {
    removePaths { id -> id.startsWith(group) && id !in targets }
    targets.forEach { (id, item) ->
        val strokeColor = color(item)
        setLatLonPath(
            id = id,
            points = pathFor(item),
            color = strokeColor,
            width = width,
            fillColor = strokeColor.copy(alpha = fillAlpha),
            zIndex = zIndex,
        )
    }
}
