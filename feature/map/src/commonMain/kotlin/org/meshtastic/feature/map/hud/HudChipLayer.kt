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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_hud_more
import kotlin.math.abs

private const val OFFSCREEN_ALPHA = 0.7f
private const val LEADER_ALPHA = 0.8f

/** Rows a chip may be lifted to clear nearer ones; the rest of a crowded column is counted, not drawn. */
private const val MAX_CHIP_LEVEL = 3
private const val NEAR_SCALE_M = 1_000.0
private const val MID_SCALE_M = 10_000.0
private const val MID_SCALE = 0.9f
private const val FAR_SCALE = 0.8f
private val ChipGap = 4.dp

/** A chip on its point scales about its foot; a lifted one about the corner its leader meets. */
private val BottomCenter = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
private val BottomLeft = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 1f)
private val BottomRight = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)

/** How far past its point a lifted chip's near corner sits, so the leader leans and never reads as a height. */
private val LeaderRun = 16.dp
private val LeaderWidth = 1.dp
private val LeaderEdgeWidth = 3.dp
private val LeaderDashes = floatArrayOf(10f, 8f)
private val EdgeGutter = 28.dp
private val ArrowSize = 10.dp
private val ArrowGap = 2.dp
private val AnchorRadius = 4.dp
private val LeaderEdge = Color.Black.copy(alpha = 0.4f)
private val OverflowPadding = 6.dp
private val OverflowBand = 28.dp
private val OverflowScrim = Color.Black.copy(alpha = HUD_SCRIM_ALPHA)

/**
 * Where a chip went, so the draw pass can mark its true point, lead a lifted chip back to it from [port], or point an
 * off-screen chip toward the turn. [dashed] is true when the point sits level only as a guess, with no altitude.
 */
private class PlacedChip(
    val rect: IntRect,
    val anchor: Offset?,
    val port: Offset?,
    val edge: PinEdge?,
    val color: Color,
    val dashed: Boolean,
)

/** A column of chips cut off by the top limit: its center and how many did not fit. */
private class Overflow(val centerX: Int, var count: Int)

/** Written by the placement pass and read by the draw pass of the same frame. */
private class ChipPlacement {
    val chips = mutableListOf<PlacedChip>()
    val overflow = mutableListOf<Overflow>()

    /** The edge each off-screen target was last pinned to; kept across frames, unlike the rest. */
    val lastEdge = mutableMapOf<String, PinEdge>()

    fun clear() {
        chips.clear()
        overflow.clear()
    }

    fun addOverflow(centerX: Int, width: Int) {
        overflow.firstOrNull { abs(it.centerX - centerX) < width }?.let { it.count++ }
            ?: overflow.add(Overflow(centerX, 1))
    }
}

/** Nearer chips draw larger, so depth reads at a glance without reading the distance label. */
private fun distanceScale(distanceM: Double): Float = when {
    distanceM < NEAR_SCALE_M -> 1f
    distanceM < MID_SCALE_M -> MID_SCALE
    else -> FAR_SCALE
}

/**
 * Places each chip with its bottom center on the target's projected point, focused then nearest first, lifting a chip
 * above any earlier one it would overlap. The lift comes from [stackLevels], which works in view angles and so does not
 * change as the phone moves. A lifted chip steps aside, alternating right and left, and a thin neutral leader runs from
 * its near bottom corner to the point: a callout, not a height. Chips lifted past [MAX_CHIP_LEVEL] rows, or that would
 * leave the band between [topLimitPx] and [bottomLimitPx] are dropped into a "+N" count for their column. Targets out
 * of view are pinned to the edge they lie beyond (see [pinEdge]) with an arrow toward them.
 *
 * The pose is read in the placement and draw blocks, so sensor updates relayout and redraw without recomposing.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun HudChipLayer(
    targets: List<HudTarget>,
    currentPose: () -> CameraPose?,
    focalPx: Float,
    topLimitPx: () -> Int,
    bottomLimitPx: () -> Int,
    modifier: Modifier = Modifier,
    chip: @Composable (HudTarget) -> Unit,
) {
    val placement = remember { ChipPlacement() }
    val textMeasurer = rememberTextMeasurer()
    val overflowStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White)
    val overflowTemplate = stringResource(Res.string.mesh_hud_more)
    Layout(
        content = { targets.forEach { key(it.subject.key) { chip(it) } } },
        // A traversal group, so TalkBack reads the chips in the order each sets, nearest the center first.
        modifier =
        modifier
            .semantics { isTraversalGroup = true }
            .drawBehind {
                if (currentPose() == null) return@drawBehind
                placement.chips.forEach { drawLeaderOrArrow(it) }
                placement.overflow.forEach {
                    drawOverflow(it, topLimitPx(), textMeasurer, overflowStyle, overflowTemplate)
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = ChipGap.roundToPx()
        val gutter = EdgeGutter.roundToPx()
        val overflowBand = OverflowBand.roundToPx()
        val keys = targets.mapTo(HashSet()) { it.subject.key }
        val levels =
            stackLevels(
                targets.mapIndexed { i, target ->
                    AngularBox(
                        bearingDeg = target.bearingDeg,
                        elevationDeg = target.elevationDeg,
                        halfWidthDeg = pxToDeg(placeables[i].width, focalPx) / 2,
                        heightDeg = pxToDeg(placeables[i].height, focalPx),
                    )
                },
                gapDeg = pxToDeg(gap, focalPx),
                leaderRunDeg = pxToDeg(LeaderRun.roundToPx(), focalPx),
                maxLevel = MAX_CHIP_LEVEL,
            )
        val leaderRun = LeaderRun.roundToPx()
        val arrowRoomPx = (ArrowSize + ArrowGap).roundToPx()
        layout(width, height) {
            placement.clear()
            placement.lastEdge.keys.retainAll(keys)
            val pose = currentPose() ?: return@layout
            // The overflow badges get their own row under the limit, so no chip is placed over them.
            val topLimit = topLimitPx() + overflowBand
            val bottomLimit = bottomLimitPx().takeIf { it > topLimit } ?: height
            // Only the edge-pinned chips stack on screen; they sit at fixed places, so that holds still too.
            val edgeOccupied = mutableListOf<IntRect>()
            placeables.forEachIndexed { index, placeable ->
                val target = targets[index]
                val direction = directionEnu(target.bearingDeg, target.elevationDeg)
                val projected = project(direction, pose, focalPx, width.toFloat(), height.toFloat())
                val point = projected?.takeIf { it.x in 0f..width.toFloat() && it.y in 0f..height.toFloat() }
                val key = target.subject.key
                val yaw = signedTurnDeg(pose.azimuthDeg, target.bearingDeg)
                val pitchDelta = target.elevationDeg - pose.pitchDeg
                val edge =
                    if (point != null) {
                        null
                    } else {
                        pinEdge(projected, width.toFloat(), yaw, pitchDelta, placement.lastEdge[key]).also {
                            placement.lastEdge[key] = it
                        }
                    }
                val rowHeight = placeable.height + gap
                if (point != null && levels[index] == OVERFLOW_LEVEL) {
                    placement.addOverflow(point.x.toInt(), placeable.width)
                    return@forEachIndexed
                }
                val level = if (point != null) levels[index] else 0
                // A chip that would step off the screen steps to the other side of its point instead.
                val side =
                    leaderSide(level).let { preferred ->
                        val x = point?.x?.toInt() ?: 0
                        when {
                            preferred > 0 && x + leaderRun + placeable.width > width - gutter -> -1
                            preferred < 0 && x - leaderRun - placeable.width < gutter -> 1
                            else -> preferred
                        }
                    }
                // Pinned chips spread along their edge by where the target lies, rather than piling in one place.
                val along = edge?.let { edgeOffsetPx(it, yaw, pitchDelta, focalPx) } ?: 0f
                val alongX =
                    (width / 2 + along.toInt() - placeable.width / 2).coerceIn(
                        gutter,
                        maxOf(gutter, width - placeable.width - gutter),
                    )
                val alongY =
                    (height / 2 - along.toInt() - placeable.height / 2).coerceIn(
                        topLimit,
                        maxOf(topLimit, bottomLimit - placeable.height),
                    )
                val left =
                    when {
                        point != null && side > 0 -> point.x.toInt() + leaderRun
                        point != null && side < 0 -> point.x.toInt() - leaderRun - placeable.width
                        point != null -> point.x.toInt() - placeable.width / 2
                        edge == PinEdge.RIGHT -> width - placeable.width - gutter
                        edge == PinEdge.LEFT -> gutter
                        else -> alongX
                    }
                var top =
                    when {
                        point != null -> point.y.toInt() - placeable.height - level * rowHeight
                        edge == PinEdge.TOP -> topLimit + gutter
                        edge == PinEdge.BOTTOM -> bottomLimit - placeable.height - gutter
                        else -> alongY
                    }
                // Top-pinned chips stack downward, away from the edge; every other kind stacks upward. A top or bottom
                // pin's arrow sits outside its chip, so those rows leave room for it.
                val arrowRoom = if (edge == PinEdge.TOP || edge == PinEdge.BOTTOM) arrowRoomPx else 0
                val step = if (edge == PinEdge.TOP) rowHeight + arrowRoom else -(rowHeight + arrowRoom)
                var rect = IntRect(left, top, left + placeable.width, top + placeable.height)
                while (edge != null && edgeOccupied.any { it.overlaps(rect) }) {
                    top += step
                    rect = IntRect(left, top, left + placeable.width, top + placeable.height)
                }
                if (rect.top < topLimit || rect.bottom > bottomLimit) {
                    placement.addOverflow(rect.center.x, placeable.width)
                    return@forEachIndexed
                }
                if (edge != null) edgeOccupied += rect
                val port =
                    when {
                        point == null || side == 0 -> null
                        side > 0 -> Offset(rect.left.toFloat(), rect.bottom.toFloat())
                        else -> Offset(rect.right.toFloat(), rect.bottom.toFloat())
                    }
                placement.chips +=
                    PlacedChip(
                        rect = rect,
                        anchor = point?.let { Offset(it.x, it.y) },
                        port = port,
                        edge = edge,
                        color = Color(target.subject.color),
                        dashed = !target.elevationKnown,
                    )
                val scale = distanceScale(target.distanceM)
                placeable.placeWithLayer(left, top) {
                    alpha = if (edge == null) 1f else OFFSCREEN_ALPHA
                    scaleX = scale
                    scaleY = scale
                    transformOrigin =
                        when {
                            side > 0 -> BottomLeft
                            side < 0 -> BottomRight
                            else -> BottomCenter
                        }
                }
            }
        }
    }
}

private fun DrawScope.drawLeaderOrArrow(chip: PlacedChip) {
    chip.anchor?.let { anchor ->
        chip.port?.let { port ->
            // Neutral white over a dark edge: the node's color stays on the chip and the point it marks.
            val effect = if (chip.dashed) PathEffect.dashPathEffect(LeaderDashes) else null
            drawLine(LeaderEdge, port, anchor, LeaderEdgeWidth.toPx(), pathEffect = effect)
            drawLine(Color.White.copy(alpha = LEADER_ALPHA), port, anchor, LeaderWidth.toPx(), pathEffect = effect)
        }
        drawCircle(Color.White, radius = AnchorRadius.toPx() + 1.dp.toPx(), center = anchor)
        drawCircle(chip.color, radius = AnchorRadius.toPx(), center = anchor)
    }
    chip.edge?.let { drawEdgeArrow(chip.rect, it) }
}

/** A triangle just outside [rect], pointing past the edge the target lies beyond. */
private fun DrawScope.drawEdgeArrow(rect: IntRect, edge: PinEdge) {
    val size = ArrowSize.toPx()
    val gap = ArrowGap.toPx()
    val cx = rect.center.x.toFloat()
    val cy = rect.center.y.toFloat()
    val arrow =
        Path().apply {
            when (edge) {
                PinEdge.LEFT -> {
                    val base = rect.left - gap
                    moveTo(base - size, cy)
                    lineTo(base, cy - size)
                    lineTo(base, cy + size)
                }

                PinEdge.RIGHT -> {
                    val base = rect.right + gap
                    moveTo(base + size, cy)
                    lineTo(base, cy - size)
                    lineTo(base, cy + size)
                }

                PinEdge.TOP -> {
                    val base = rect.top - gap
                    moveTo(cx, base - size)
                    lineTo(cx - size, base)
                    lineTo(cx + size, base)
                }

                PinEdge.BOTTOM -> {
                    val base = rect.bottom + gap
                    moveTo(cx, base + size)
                    lineTo(cx - size, base)
                    lineTo(cx + size, base)
                }
            }
            close()
        }
    drawPath(arrow, Color.White)
}

private fun DrawScope.drawOverflow(
    overflow: Overflow,
    topLimit: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    template: String,
) {
    val layout = textMeasurer.measure(formatString(template, overflow.count), style)
    val pad = OverflowPadding.toPx()
    val boxSize = Size(layout.size.width + 2 * pad, layout.size.height + pad)
    val topLeft = Offset(overflow.centerX - boxSize.width / 2, topLimit.toFloat())
    drawRoundRect(OverflowScrim, topLeft, boxSize, CornerRadius(pad))
    drawText(layout, topLeft = topLeft + Offset(pad, pad / 2))
}
