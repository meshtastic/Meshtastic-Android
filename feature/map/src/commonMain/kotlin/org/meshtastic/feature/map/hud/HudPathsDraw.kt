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
private val SparkCore = 2.5.dp
private val SparkGlow = 5.dp
private val SparkTail = 14.dp

/** Sparks ride a path this many degrees apart, and travel this many degrees a second, so they hold to the world. */
private const val SPARK_SPACING_DEG = 30.0
private const val SPARK_DEG_PER_SECOND = 4.0
private const val SPARK_GLOW_ALPHA = 0.6f
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

/** A leg to or from the viewer as directions, from its feet out to [far] when [leaving], and back otherwise. */
private fun viewerPath(far: PathPoint, leaving: Boolean): List<Vec3> {
    val out = listOf(VIEWER_DOWN, directionEnu(far.bearingDeg, far.elevationDeg))
    return if (leaving) out else out.reversed()
}

/** Black on a light color and white on a dark one, for whatever is drawn over or around it. */
private fun contrastOn(color: Color): Color =
    if (color.luminance() > CASING_LUMINANCE_SPLIT) Color.Black else Color.White

/**
 * Packets in flight along [path], after the Chirpy Hop backdrop's hops: each spark a bright core over a halo in the
 * path's color, with a short tail behind it, moving with [seconds].
 */
@Suppress("LongParameterList")
private fun DrawScope.drawSparks(
    path: List<Vec3>,
    seconds: Double,
    pose: CameraPose,
    focalPx: Float,
    core: Color,
    glow: Color,
) {
    val tail = SparkTail.toPx()
    sparksAlong(path, seconds * SPARK_DEG_PER_SECOND, SPARK_SPACING_DEG).forEach { spark ->
        val at = screen(spark.at, pose, focalPx) ?: return@forEach
        val behind = screen(spark.behind, pose, focalPx) ?: return@forEach
        val ahead = screen(spark.ahead, pose, focalPx) ?: return@forEach
        val heading = (ahead - behind).let { d -> d.getDistance().takeIf { it > 0f }?.let { d / it } }
        heading?.let { drawLine(glow, at - it * tail, at, SparkGlow.toPx(), StrokeCap.Round) }
        drawCircle(glow, SparkGlow.toPx(), at)
        drawCircle(core, SparkCore.toPx(), at)
    }
}

/** Appends [leg] to [path], dropping its first direction where it repeats the path's last. */
private fun MutableList<Vec3>.extend(leg: List<Vec3>) {
    addAll(if (leg.firstOrNull() == lastOrNull()) leg.drop(1) else leg)
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
    sparkSeconds: Double,
) {
    val tags = mutableListOf<Pair<String, Offset>>()
    listOfNotNull(route.forward, route.back).forEach { leg ->
        val color = if (leg.forward) forwardColor else backColor
        val liftSign = if (leg.forward) 1 else -1
        // The leg hop by hop as directions, for its sparks to run along once every link is down.
        val path = mutableListOf<Vec3>()
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
                    path.extend(viewerPath(far, leaving = start.atViewer))
                    screen(directionEnu(far.bearingDeg, far.elevationDeg), pose, focalPx)?.let { end ->
                        // Toward the hop, so the tag stays clear of the bottom panel and the reticle.
                        val along = if (leg.forward) FORWARD_STEM_TAG_AT else RETURN_STEM_TAG_AT
                        tag?.let { tags += it to lerp(Offset(size.width / 2, size.height), end, along) }
                    }
                } else if (!start.atViewer) {
                    val arc = arcDirections(start, point, liftSign)
                    drawPolyline(arc, pose, focalPx, stroke, width, dashed = skipped > 0)
                    path.extend(arc)
                    val apex = screen(arc[arc.size / 2], pose, focalPx)
                    if (apex != null && tag != null) tags += tag to apex
                }
            }
            from = index
        }
        drawSparks(path, sparkSeconds, pose, focalPx, contrastOn(color), color.copy(alpha = SPARK_GLOW_ALPHA))
    }
    tags.forEach { (text, at) -> drawTag(text, at, textMeasurer, labelStyle) }
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
    sparkSeconds: Double,
) {
    val points = track.points
    if (points.isEmpty()) return
    val base = Color(track.color)
    // A node's color can be as dark or as light as the scene behind it, so the track rides on an outline of the
    // opposite shade, as a chip's text does on its fill.
    val casing = contrastOn(base)
    val recency = trackRecency(points.map { it.fixTimeSec })
    fun alphaAt(index: Int): Float = trackFadeAlpha(recency[index])
    fun TrackPoint.direction() = directionEnu(point.bearingDeg, point.elevationDeg)

    // Each leg from older a to newer b as directions along the ground; one with an end at the viewer runs to its feet.
    val legs = points.zipWithNext { a, b ->
        val far = viewerLeg(a.point, b.point)
        when {
            far != null -> viewerPath(far, leaving = a.point.atViewer)
            a.point.atViewer -> emptyList()
            else -> groundDirections(a.point, b.point)
        }
    }
    fun drawLegs(width: Float, color: (Int) -> Color) = points.zipWithNext().forEachIndexed { i, (a, b) ->
        val far = viewerLeg(a.point, b.point)
        when {
            far != null -> drawFromViewer(far, pose, focalPx, color(i + 1), width, dashed = !b.altitudeKnown)

            legs[i].isNotEmpty() ->
                drawPolyline(legs[i], pose, focalPx, color(i + 1), width, dashed = !b.altitudeKnown)
        }
    }
    drawLegs(TrackWidth.toPx() + 2 * TrackCasing.toPx()) { casing.copy(alpha = alphaAt(it)) }
    drawLegs(TrackWidth.toPx()) { base.copy(alpha = alphaAt(it)) }
    // Older to newer, the core in the outline's shade so it stands off the line it runs along.
    val path = mutableListOf<Vec3>().apply { legs.forEach { extend(it) } }
    drawSparks(path, sparkSeconds, pose, focalPx, casing, base.copy(alpha = SPARK_GLOW_ALPHA))
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
