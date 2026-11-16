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
package org.meshtastic.feature.map.hud

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.meshtastic.core.common.util.bearing
import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.feature.map.survey.SurveyCellUi
import kotlin.math.cos
import kotlin.math.sin

/** How far out the ground is painted. Beyond this a 50 m square is a sliver on the horizon. */
internal const val SURVEY_TILE_RADIUS_M = 300.0

/** The most squares drawn at once, nearest first, so a dense survey cannot slow the frame. */
internal const val SURVEY_TILE_LIMIT = 400

private const val FULL_CIRCLE = 360.0
private const val NEAR_ALPHA = 0.5f
private const val FAR_ALPHA = 0.15f

/**
 * A survey square laid on the ground around the phone. [corners] are east and north offsets in metres, turned to
 * magnetic north like the camera pose, so they project with the same math as everything else in the view.
 */
data class HudSurveyTile(val corners: List<Pair<Double, Double>>, val quality: Quality, val distanceM: Double)

/**
 * The squares within [SURVEY_TILE_RADIUS_M] of [here], farthest first so nearer ones paint over them, capped at the
 * [SURVEY_TILE_LIMIT] nearest.
 */
internal fun List<SurveyCellUi>.toHudTiles(here: PhoneLocation, declinationDeg: Float): List<HudSurveyTile> =
    mapNotNull { item ->
        val cell = item.cell
        val centerLat = (cell.south + cell.north) / 2
        val centerLon = (cell.west + cell.east) / 2
        val distance = latLongToMeter(here.latitude, here.longitude, centerLat, centerLon)
        if (distance > SURVEY_TILE_RADIUS_M) return@mapNotNull null
        val corners =
            listOf(
                cell.south to cell.west,
                cell.south to cell.east,
                cell.north to cell.east,
                cell.north to cell.west,
            )
                .map { (lat, lon) -> here.offsetTo(lat, lon, declinationDeg) }
        HudSurveyTile(corners, item.quality, distance)
    }
        .sortedBy { it.distanceM }
        .take(SURVEY_TILE_LIMIT)
        .reversed()

/** East and north metres from here to the point, in the magnetic frame the camera pose uses. */
private fun PhoneLocation.offsetTo(lat: Double, lon: Double, declinationDeg: Float): Pair<Double, Double> {
    val distance = latLongToMeter(latitude, longitude, lat, lon)
    val magnetic = ((bearing(latitude, longitude, lat, lon) - declinationDeg) % FULL_CIRCLE).toRadians()
    return distance * sin(magnetic) to distance * cos(magnetic)
}

/**
 * The survey squares painted on the ground the phone stands on, fading with distance. Each square is clipped to the
 * part in front of the camera, so the one underfoot still shows when the phone looks down at it.
 */
internal fun DrawScope.drawSurveyTiles(
    tiles: List<HudSurveyTile>,
    colors: Map<Quality, Color>,
    pose: CameraPose,
    focalPx: Float,
) {
    tiles.forEach { tile ->
        val color = colors[tile.quality] ?: return@forEach
        val ground = tile.corners.map { (east, north) -> Vec3(east, north, -EYE_HEIGHT_M) }
        val visible = clipPolygonToNearPlane(ground, pose)
        if (visible.size < MIN_POLYGON) return@forEach
        val points = visible.mapNotNull { project(it, pose, focalPx, size.width, size.height) }
        if (points.size != visible.size) return@forEach
        val path =
            Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
        val fade = (tile.distanceM / SURVEY_TILE_RADIUS_M).coerceIn(0.0, 1.0).toFloat()
        drawPath(path, color.copy(alpha = NEAR_ALPHA + (FAR_ALPHA - NEAR_ALPHA) * fade))
    }
}

private const val MIN_POLYGON = 3

/** The part of [polygon] in front of the camera (Sutherland-Hodgman against the near plane). */
internal fun clipPolygonToNearPlane(polygon: List<Vec3>, pose: CameraPose): List<Vec3> {
    val out = mutableListOf<Vec3>()
    polygon.indices.forEach { i ->
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val aIn = (a dot pose.forward) >= NEAR_PLANE
        val bIn = (b dot pose.forward) >= NEAR_PLANE
        if (aIn) out += a
        if (aIn != bIn) clipToNearPlane(a, b, pose)?.let { (p, q) -> out += if (aIn) q else p }
    }
    return out
}
