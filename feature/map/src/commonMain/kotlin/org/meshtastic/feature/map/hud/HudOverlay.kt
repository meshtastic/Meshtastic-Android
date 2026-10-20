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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor

private const val HORIZON_SPAN_DEG = 90
private const val HORIZON_STEP_DEG = 5
private const val HORIZON_ALPHA = 0.4f
private const val ZONE_ALPHA = 0.3f
private const val TAPE_MINOR_DEG = 5
private const val TAPE_MAJOR_DEG = 15
private const val TAPE_LABEL_DEG = 30
private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val LOCKED_FILL_ALPHA = 0.35f
private const val FULL_TURN = 360
private val ZoneMinRadius = 6.dp
private val TapeHeight = 30.dp
private val TapeMinorTick = 5.dp
private val TapeMajorTick = 9.dp
private val TapeMarker = 5.dp
private val ReticleRadius = 36.dp

/** Light enough to read as part of the view; the labels carry their own shadow for contrast. */
private val TapeScrim = Color.Black.copy(alpha = 0.3f)

/** The four cardinal letters, localized, for the compass tape. */
internal data class Cardinals(val north: String, val east: String, val south: String, val west: String) {
    fun at(deg: Int): String? = when (deg) {
        0 -> north
        QUARTER_TURN -> east
        HALF_TURN -> south
        THREE_QUARTER_TURN -> west
        else -> null
    }
}

/** The true horizontal (elevation 0) across the view, as a check that the overlay sits level with the world. */
internal fun DrawScope.drawHorizon(pose: CameraPose, focalPx: Float) {
    val points =
        (-HORIZON_SPAN_DEG..HORIZON_SPAN_DEG step HORIZON_STEP_DEG).mapNotNull { offset ->
            project(directionEnu(pose.azimuthDeg + offset, 0.0), pose, focalPx, size.width, size.height)
        }
    points.zipWithNext { a, b ->
        drawLine(Color.White.copy(alpha = HORIZON_ALPHA), Offset(a.x, a.y), Offset(b.x, b.y), 1.dp.toPx())
    }
}

/**
 * The zone the target could lie in, drawn as a soft oval in its color, strongest at its best-estimate point and fading
 * to clear at the edge. Its width is the bearing uncertainty, widened by [headingAccuracyDeg] since a heading error
 * moves every target sideways; its height is the elevation uncertainty; it lies along the horizon even when the phone
 * rolls.
 */
internal fun DrawScope.drawPrecisionZone(
    target: HudTarget,
    pose: CameraPose,
    focalPx: Float,
    headingAccuracyDeg: Double?,
) {
    val across = (target.precisionHalfAngleDeg ?: 0.0) + (headingAccuracyDeg ?: 0.0)
    val tall = target.elevationSpreadDeg
    fun at(bearingOffset: Double, elevationOffset: Double) = project(
        directionEnu(target.bearingDeg + bearingOffset, target.elevationDeg + elevationOffset),
        pose,
        focalPx,
        size.width,
        size.height,
    )
        ?.let { Offset(it.x, it.y) }
    val c = at(0.0, 0.0) ?: return
    val left = at(-across, 0.0)
    val right = at(across, 0.0)
    val top = at(0.0, tall)
    val limit = size.maxDimension
    val minRadius = ZoneMinRadius.toPx()
    val rx = (if (left != null && right != null) (right - left).getDistance() / 2 else limit).coerceIn(minRadius, limit)
    val ry = (top?.let { (it - c).getDistance() } ?: limit).coerceIn(minRadius, limit)
    val degreesPerRad = (HALF_TURN / PI).toFloat()
    val angle = if (left != null && right != null) atan2(right.y - left.y, right.x - left.x) * degreesPerRad else 0f
    val color = Color(target.subject.color)
    withTransform({
        rotate(angle, pivot = c)
        scale(scaleX = 1f, scaleY = ry / rx, pivot = c)
    }) {
        drawCircle(
            brush =
            Brush.radialGradient(
                colors =
                listOf(color.copy(alpha = ZONE_ALPHA), color.copy(alpha = ZONE_ALPHA / 2), Color.Transparent),
                center = c,
                radius = rx,
            ),
            radius = rx,
            center = c,
        )
    }
}

/**
 * A heading tape across the top at [topPx], scaled to the camera so its ticks sit over the directions they name.
 * [trueHeadingDeg] is at the center; each target shows as a marker at its true bearing, larger when focused, so
 * everything around the user is visible without turning.
 */
@Suppress("LongParameterList")
internal fun DrawScope.drawCompassTape(
    trueHeadingDeg: Double,
    declinationDeg: Float,
    targets: List<HudTarget>,
    focalPx: Float,
    topPx: Float,
    cardinals: Cardinals,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val pxPerDeg = focalPx * (PI / HALF_TURN).toFloat()
    val height = TapeHeight.toPx()
    val centerX = size.width / 2
    drawRect(TapeScrim, Offset(0f, topPx), Size(size.width, height))
    val halfSpanDeg = centerX / pxPerDeg
    val first = ceil((trueHeadingDeg - halfSpanDeg) / TAPE_MINOR_DEG).toInt() * TAPE_MINOR_DEG
    val last = floor((trueHeadingDeg + halfSpanDeg) / TAPE_MINOR_DEG).toInt() * TAPE_MINOR_DEG
    for (deg in first..last step TAPE_MINOR_DEG) {
        val x = centerX + ((deg - trueHeadingDeg) * pxPerDeg).toFloat()
        val bearing = ((deg % FULL_TURN) + FULL_TURN) % FULL_TURN
        val major = bearing % TAPE_MAJOR_DEG == 0
        val tick = if (major) TapeMajorTick.toPx() else TapeMinorTick.toPx()
        drawLine(Color.White, Offset(x, topPx + height - tick), Offset(x, topPx + height), 1.dp.toPx())
        val label = cardinals.at(bearing) ?: bearing.takeIf { it % TAPE_LABEL_DEG == 0 }?.toString()
        if (label != null) {
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, topPx + 2.dp.toPx()))
        }
    }
    targets.forEach { target ->
        val trueBearing = target.bearingDeg + declinationDeg
        val offset = signedTurnDeg(trueHeadingDeg, trueBearing)
        if (abs(offset) > halfSpanDeg) return@forEach
        val x = centerX + (offset * pxPerDeg).toFloat()
        val r = if (target.focused) TapeMarker.toPx() * 2 else TapeMarker.toPx()
        drawCircle(Color(target.subject.color), radius = r, center = Offset(x, topPx + height - r))
    }
    val caret =
        Path().apply {
            moveTo(centerX, topPx + height + 6.dp.toPx())
            lineTo(centerX - 6.dp.toPx(), topPx + height + 14.dp.toPx())
            lineTo(centerX + 6.dp.toPx(), topPx + height + 14.dp.toPx())
            close()
        }
    drawPath(caret, Color.White)
}

/** The finder's center ring, filled with the target's color once it is on target. */
internal fun DrawScope.drawReticle(color: Color, locked: Boolean) {
    val radius = ReticleRadius.toPx()
    if (locked) drawCircle(color.copy(alpha = LOCKED_FILL_ALPHA), radius = radius)
    drawCircle(if (locked) color else Color.White, radius = radius, style = Stroke(width = 2.dp.toPx()))
    val tick = 8.dp.toPx()
    val c = center
    listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
        drawLine(Color.White, c + d * radius, c + d * (radius + tick), 2.dp.toPx())
    }
    drawCircle(Color.White, radius = 2.dp.toPx(), center = c)
}

/** Where the phone's camera sits above the ground it stands on, for drawing things that lie on that ground. */
private const val EYE_HEIGHT_M = 1.5

/** How tall a geofence's wall stands, so the fence reads as a boundary rather than a line on the grass. */
private const val FENCE_WALL_M = 3.0
private const val FENCE_WALL_ALPHA = 0.3f
private const val FENCE_LINE_ALPHA = 0.85f
private val FenceColor = Color(0xFFFFB300)
private val FenceLineWidth = 2.dp

/** The direction to a fence point [riseM] above the phone's height. */
private fun groundDirection(point: FencePoint, riseM: Double) =
    directionEnu(point.bearingDeg, elevationDeg(point.distanceM, riseM))

/**
 * A geofence as a wall standing on the ground along its edge, solid at the foot and fading upward, so the boundary
 * reads in depth from inside or outside. Segments that fall behind the camera are left out.
 */
internal fun DrawScope.drawGeofence(fence: HudFence, pose: CameraPose, focalPx: Float) {
    fun at(point: FencePoint, riseM: Double) = project(
        groundDirection(point, riseM),
        pose,
        focalPx,
        size.width,
        size.height,
    )
        ?.let { Offset(it.x, it.y) }
    val edge = fence.edge
    edge.indices.forEach { i ->
        val p = edge[i]
        val q = edge[(i + 1) % edge.size]
        // The foot line is clipped at the near plane, so the boundary runs to the view's edge from inside the fence.
        val clippedFoot = clipToNearPlane(groundDirection(p, -EYE_HEIGHT_M), groundDirection(q, -EYE_HEIGHT_M), pose)
        clippedFoot?.let { (a, b) ->
            val fa = project(a, pose, focalPx, size.width, size.height)
            val fb = project(b, pose, focalPx, size.width, size.height)
            if (fa != null && fb != null) {
                drawLine(
                    FenceColor.copy(alpha = FENCE_LINE_ALPHA),
                    Offset(fa.x, fa.y),
                    Offset(fb.x, fb.y),
                    FenceLineWidth.toPx(),
                )
            }
        }
        // The wall is decoration: drawn only where its whole segment is in front of the camera.
        val footP = at(p, -EYE_HEIGHT_M) ?: return@forEach
        val footQ = at(q, -EYE_HEIGHT_M) ?: return@forEach
        val topP = at(p, FENCE_WALL_M - EYE_HEIGHT_M) ?: return@forEach
        val topQ = at(q, FENCE_WALL_M - EYE_HEIGHT_M) ?: return@forEach
        val wall =
            Path().apply {
                moveTo(footP.x, footP.y)
                lineTo(footQ.x, footQ.y)
                lineTo(topQ.x, topQ.y)
                lineTo(topP.x, topP.y)
                close()
            }
        drawPath(
            wall,
            Brush.linearGradient(
                colors = listOf(FenceColor.copy(alpha = FENCE_WALL_ALPHA), Color.Transparent),
                start = (footP + footQ) / 2f,
                end = (topP + topQ) / 2f,
            ),
        )
    }
}
