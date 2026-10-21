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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.feature.map.trackFadeAlpha
import org.meshtastic.feature.map.trackRecency
import kotlin.math.cos
import kotlin.math.sin

private val RadarPathWidth = 2.dp
private val RadarReturnShift = 3.dp
private val RadarTrackRing = 3.dp
private val RadarDashes = floatArrayOf(10f, 8f)

/**
 * Where a point [distanceM] away on magnetic [bearingDeg] sits on a heading-up radar, as an offset from its center at
 * [pxPerM]. Unlike [radarOffset] it does not stop at the rim, so a path toward a farther point leaves the radar heading
 * the right way.
 */
internal fun radarPathOffset(bearingDeg: Double, distanceM: Double, headingDeg: Double, pxPerM: Float): Offset {
    val rel = (bearingDeg - headingDeg).toRadians()
    val r = distanceM * pxPerM
    return Offset((r * sin(rel)).toFloat(), (-r * cos(rel)).toFloat())
}

/**
 * The open traceroute and track in plan view, under the radar's dots: a scoped view whose nodes are all too close to
 * point at still shows what it was opened for. The return leg is shifted aside so it does not hide the forward one, and
 * a hop with no position is bridged dashed, as in the camera view.
 */
internal fun DrawScope.drawRadarPaths(route: HudRoute?, track: HudTrack?, headingDeg: Double, pxPerM: Float) {
    fun at(p: PathPoint) = center + radarPathOffset(p.bearingDeg, p.distanceM, headingDeg, pxPerM)
    track?.let { drawRadarTrack(it, ::at) }
    route?.let {
        drawRadarLeg(it.forward, TracerouteColors.OutgoingRoute, Offset.Zero, ::at)
        it.back?.let { back ->
            val shift = RadarReturnShift.toPx()
            drawRadarLeg(back, TracerouteColors.ReturnRoute, Offset(shift, shift), ::at)
        }
    }
}

private fun DrawScope.drawRadarLeg(leg: RouteLeg, color: Color, shift: Offset, at: (PathPoint) -> Offset) {
    var previous: Offset? = null
    var skipped = false
    leg.points.forEach { point ->
        if (point == null) {
            skipped = previous != null
            return@forEach
        }
        val here = at(point) + shift
        previous?.let { from ->
            drawLine(
                color,
                from,
                here,
                RadarPathWidth.toPx(),
                StrokeCap.Round,
                pathEffect = if (skipped) PathEffect.dashPathEffect(RadarDashes) else null,
            )
        }
        previous = here
        skipped = false
    }
}

private fun DrawScope.drawRadarTrack(track: HudTrack, at: (PathPoint) -> Offset) {
    val points = track.points.map { at(it.point) }
    if (points.isEmpty()) return
    val base = Color(track.color)
    val recency = trackRecency(track.points.map { it.fixTimeSec })
    fun alphaAt(index: Int) = trackFadeAlpha(recency[index])
    points.zipWithNext().forEachIndexed { i, (a, b) ->
        drawLine(base.copy(alpha = alphaAt(i + 1)), a, b, RadarPathWidth.toPx(), StrokeCap.Round)
    }
    points.forEachIndexed { i, p ->
        drawCircle(base.copy(alpha = alphaAt(i)), RadarTrackRing.toPx(), p, style = Stroke(1.dp.toPx()))
    }
}
