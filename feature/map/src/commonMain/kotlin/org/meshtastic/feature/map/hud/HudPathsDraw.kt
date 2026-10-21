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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.feature.map.trackFadeAlpha
import org.meshtastic.feature.map.trackRecency

private const val SNR_FLOOR_DB = -20f
private const val SNR_SPAN_DB = 30f
private const val UNKNOWN_SNR_QUALITY = 0.5f
private const val ARC_MIN_ALPHA = 0.55f

/**
 * How far along a stem from the viewer its tag sits. The two legs' stems leave the same point, so their tags sit at
 * different heights rather than on top of each other.
 */
private const val FORWARD_STEM_TAG_AT = 0.7f
private const val RETURN_STEM_TAG_AT = 0.45f
private val ArcMinWidth = 2.dp
private val ArcExtraWidth = 4.dp
private val TrackWidth = 3.dp
private val TrackRing = 4.dp
private val TrackRingStroke = 2.dp
private val TrackCasing = 1.5.dp
private const val CASING_LUMINANCE_SPLIT = 0.5f
private val LabelPadding = 4.dp
private val TagLift = 16.dp
private val TagMinGap = 48.dp
private val LabelScrim = Color.Black.copy(alpha = HUD_SCRIM_ALPHA)
private val Dashes = floatArrayOf(18f, 12f)

/** Projects [direction], or null when it is behind the camera. */
private fun DrawScope.screen(direction: Vec3, pose: CameraPose, focalPx: Float): Offset? =
    project(direction, pose, focalPx, size.width, size.height)?.let { Offset(it.x, it.y) }

/** Draws the polyline through [directions], clipping each piece at the near plane so nothing wraps around the view. */
private fun DrawScope.drawPolyline(
    directions: List<Vec3>,
    pose: CameraPose,
    focalPx: Float,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    val effect = if (dashed) PathEffect.dashPathEffect(Dashes) else null
    directions.zipWithNext { a, b ->
        val (ca, cb) = clipToNearPlane(a, b, pose) ?: return@zipWithNext
        val pa = screen(ca, pose, focalPx) ?: return@zipWithNext
        val pb = screen(cb, pose, focalPx) ?: return@zipWithNext
        drawLine(color, pa, pb, strokeWidth = width, cap = StrokeCap.Round, pathEffect = effect)
    }
}

/** A straight line from the bottom center of the view, where the viewer stands, to [far]. */
@Suppress("LongParameterList")
private fun DrawScope.drawFromViewer(
    far: PathPoint,
    pose: CameraPose,
    focalPx: Float,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    val end = screen(directionEnu(far.bearingDeg, far.elevationDeg), pose, focalPx) ?: return
    val effect = if (dashed) PathEffect.dashPathEffect(Dashes) else null
    drawLine(color, Offset(size.width / 2, size.height), end, width, StrokeCap.Round, pathEffect = effect)
}

/** The localized patterns for a route's apex tags, read in composition since drawing cannot reach resources. */
internal class RouteLabels(val skippedTemplate: String, val snrTemplate: String)

/** The words a track's time tags fall back on, read in composition like [RouteLabels]. */
internal class TrackLabels(val unknown: String, val now: String)

/** A small label on a dark plate, centered on [at]. */
private fun DrawScope.drawTag(text: String, at: Offset, textMeasurer: TextMeasurer, style: TextStyle) {
    val layout = textMeasurer.measure(text, style)
    val pad = LabelPadding.toPx()
    val box = Size(layout.size.width + 2 * pad, layout.size.height + pad)
    val topLeft = at - Offset(box.width / 2, box.height / 2)
    drawRoundRect(LabelScrim, topLeft, box, CornerRadius(pad))
    drawText(layout, topLeft = topLeft + Offset(pad, pad / 2))
}

/**
 * A traceroute in the air: each link a lifted arc between its hops, the forward leg arching up and the return leg down,
 * in the traceroute map's two colors. A link's reported SNR sets its width and strength and labels its apex. Hops with
 * no position break the chain: the arc jumps them dashed, tagged with how many it skipped. A leg starting at the phone,
 * the usual case for a handheld radio, rises from the bottom of the view.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
internal fun DrawScope.drawRoute(
    route: HudRoute,
    pose: CameraPose,
    focalPx: Float,
    forwardColor: Color,
    backColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    labels: RouteLabels,
) {
    listOfNotNull(route.forward, route.back).forEach { leg ->
        val color = if (leg.forward) forwardColor else backColor
        val liftSign = if (leg.forward) 1 else -1
        var from: Int? = null
        leg.points.forEachIndexed { index, point ->
            if (point == null) return@forEachIndexed
            val previous = from
            val start = previous?.let { leg.points[it] }
            if (previous != null && start != null) {
                val skipped = index - previous - 1
                // One reported SNR per link, so a link across skipped hops has none.
                val snr = if (skipped == 0) leg.snrDb.getOrNull(index - 1) else null
                val quality = snr?.let { ((it - SNR_FLOOR_DB) / SNR_SPAN_DB).coerceIn(0f, 1f) } ?: UNKNOWN_SNR_QUALITY
                val width = ArcMinWidth.toPx() + ArcExtraWidth.toPx() * quality
                val stroke = color.copy(alpha = ARC_MIN_ALPHA + (1 - ARC_MIN_ALPHA) * quality)
                val far = viewerLeg(start, point)
                val tag =
                    when {
                        skipped > 0 -> formatString(labels.skippedTemplate, skipped)
                        snr != null -> formatString(labels.snrTemplate, NumberFormatter.format(snr, 1))
                        else -> null
                    }
                if (far != null) {
                    // A link to or from the phone itself: the forward leg leaves the viewer, the return leg comes home.
                    drawFromViewer(far, pose, focalPx, stroke, width, dashed = false)
                    val end = screen(directionEnu(far.bearingDeg, far.elevationDeg), pose, focalPx)
                    if (end != null) {
                        // Toward the hop, so the tag stays clear of the bottom panel and the reticle.
                        val along = if (leg.forward) FORWARD_STEM_TAG_AT else RETURN_STEM_TAG_AT
                        val viewer = Offset(size.width / 2, size.height)
                        tag?.let { drawTag(it, lerp(viewer, end, along), textMeasurer, labelStyle) }
                    }
                } else if (!start.atViewer) {
                    val arc = arcDirections(start, point, liftSign)
                    drawPolyline(arc, pose, focalPx, stroke, width, dashed = skipped > 0)
                    val apex = screen(arc[arc.size / 2], pose, focalPx)
                    if (apex != null) tag?.let { drawTag(it, apex, textMeasurer, labelStyle) }
                }
            }
            from = index
        }
    }
}

/**
 * A node's track as breadcrumbs in its color, fading with the time of each fix as both track maps do. A leg into a
 * point with no altitude draws dashed, since that point only sits level as a guess. A leg to or from a point at the
 * viewer rises from the bottom of the view, as a route's does. The oldest point, and one near the middle of a long
 * track, is tagged with how long ago it was fixed.
 */
@Suppress("LongParameterList")
internal fun DrawScope.drawTrack(
    track: HudTrack,
    pose: CameraPose,
    focalPx: Float,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    labels: TrackLabels,
) {
    val points = track.points
    if (points.isEmpty()) return
    val base = Color(track.color)
    // A node's color can be as dark or as light as the scene behind it, so the track rides on an outline of the
    // opposite shade, as a chip's text does on its fill.
    val casing = if (base.luminance() > CASING_LUMINANCE_SPLIT) Color.Black else Color.White
    val recency = trackRecency(points.map { it.fixTimeSec })
    fun alphaAt(index: Int): Float = trackFadeAlpha(recency[index])
    fun TrackPoint.direction() = directionEnu(point.bearingDeg, point.elevationDeg)
    fun leg(a: TrackPoint, b: TrackPoint, color: Color, width: Float) {
        val far = viewerLeg(a.point, b.point)
        when {
            far != null -> drawFromViewer(far, pose, focalPx, color, width, dashed = !b.altitudeKnown)

            !a.point.atViewer ->
                drawPolyline(
                    listOf(a.direction(), b.direction()),
                    pose,
                    focalPx,
                    color,
                    width,
                    dashed = !b.altitudeKnown,
                )
        }
    }
    val casingWidth = TrackWidth.toPx() + 2 * TrackCasing.toPx()
    points.zipWithNext().forEachIndexed { i, (a, b) -> leg(a, b, casing.copy(alpha = alphaAt(i + 1)), casingWidth) }
    points.zipWithNext().forEachIndexed { i, (a, b) -> leg(a, b, base.copy(alpha = alphaAt(i + 1)), TrackWidth.toPx()) }
    points.forEachIndexed { i, p ->
        if (p.point.atViewer) return@forEachIndexed
        screen(p.direction(), pose, focalPx)?.let { at ->
            val ring = TrackRingStroke.toPx()
            drawCircle(
                casing.copy(alpha = alphaAt(i)),
                TrackRing.toPx(),
                at,
                style = Stroke(ring + 2 * TrackCasing.toPx()),
            )
            drawCircle(base.copy(alpha = alphaAt(i)), TrackRing.toPx(), at, style = Stroke(ring))
        }
    }
    val placed = mutableListOf<Offset>()
    val minGap = TagMinGap.toPx()
    trackLabelIndices(points).forEach { i ->
        val time = points[i].fixTimeSec ?: return@forEach
        val at = screen(points[i].direction(), pose, focalPx) ?: return@forEach
        // Two tags that close would overlap; the first, the oldest, wins.
        if (placed.any { (it - at).getDistance() < minGap }) return@forEach
        placed += at
        val text = formatAgo(time.toInt(), labels.unknown, labels.now)
        drawTag(text, at - Offset(0f, TagLift.toPx()), textMeasurer, labelStyle)
    }
}
