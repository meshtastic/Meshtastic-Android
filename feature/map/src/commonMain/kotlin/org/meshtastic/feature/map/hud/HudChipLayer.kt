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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val OFFSCREEN_ALPHA = 0.7f
private const val STEM_ALPHA = 0.8f
private const val NEAR_SCALE_M = 1_000.0
private const val MID_SCALE_M = 10_000.0
private const val MID_SCALE = 0.9f
private const val FAR_SCALE = 0.8f
private val ChipGap = 4.dp

/** Chips scale about their foot, so the stem stays on the true point. */
private val BottomCenter = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
private val EdgeGutter = 28.dp
private val ArrowSize = 10.dp
private val AnchorRadius = 4.dp
private val StemWidth = 2.dp
private val StemEdge = Color.Black.copy(alpha = 0.4f)
private val OverflowPadding = 6.dp
private val OverflowBand = 28.dp
private val OverflowScrim = Color.Black.copy(alpha = 0.55f)

/** Where a chip went, so the draw pass can join it to its true point or point it toward the turn. */
private class PlacedChip(val rect: IntRect, val anchor: Offset?, val edge: PinEdge?, val color: Color)

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
 * Places each chip with its bottom center on the target's projected point, nearest first, lifting a chip above any
 * earlier one it would overlap. The lift comes from [stackLevels], which works in view angles and so does not change as
 * the phone moves. A lifted chip keeps a stem down to its true point. Chips that would leave the band between
 * [topLimitPx] and [bottomLimitPx] are dropped into a "+N" count for their column. Targets out of view are pinned to
 * the edge they lie beyond (see [pinEdge]) with an arrow toward them.
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
    Layout(
        content = { targets.forEach { key(it.subject.key) { chip(it) } } },
        modifier =
        modifier.drawBehind {
            if (currentPose() == null) return@drawBehind
            placement.chips.forEach { drawStemOrArrow(it) }
            placement.overflow.forEach { drawOverflow(it, topLimitPx(), textMeasurer, overflowStyle) }
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
            )
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
                        point != null -> point.x.toInt() - placeable.width / 2
                        edge == PinEdge.RIGHT -> width - placeable.width - gutter
                        edge == PinEdge.LEFT -> gutter
                        else -> alongX
                    }
                var top =
                    when {
                        point != null -> point.y.toInt() - placeable.height - levels[index] * rowHeight
                        edge == PinEdge.TOP -> topLimit + gutter
                        edge == PinEdge.BOTTOM -> bottomLimit - placeable.height - gutter
                        else -> alongY
                    }
                // Top-pinned chips stack downward, away from the edge; every other kind stacks upward.
                val step = if (edge == PinEdge.TOP) rowHeight else -rowHeight
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
                placement.chips +=
                    PlacedChip(rect, point?.let { Offset(it.x, it.y) }, edge, Color(target.subject.color))
                val scale = distanceScale(target.distanceM)
                placeable.placeWithLayer(left, top) {
                    alpha = if (edge == null) 1f else OFFSCREEN_ALPHA
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = BottomCenter
                }
            }
        }
    }
}

private fun DrawScope.drawStemOrArrow(chip: PlacedChip) {
    chip.anchor?.let { anchor ->
        val foot = Offset(chip.rect.center.x.toFloat(), chip.rect.bottom.toFloat())
        // In the node's color, over a dark edge so it holds up against a bright sky.
        drawLine(StemEdge, foot, anchor, strokeWidth = StemWidth.toPx() + 2.dp.toPx())
        drawLine(chip.color.copy(alpha = STEM_ALPHA), foot, anchor, strokeWidth = StemWidth.toPx())
        drawCircle(Color.White, radius = AnchorRadius.toPx() + 1.dp.toPx(), center = anchor)
        drawCircle(chip.color, radius = AnchorRadius.toPx(), center = anchor)
    }
    chip.edge?.let { drawEdgeArrow(chip.rect, it) }
}

/** A triangle just outside [rect], pointing past the edge the target lies beyond. */
private fun DrawScope.drawEdgeArrow(rect: IntRect, edge: PinEdge) {
    val size = ArrowSize.toPx()
    val gap = 2.dp.toPx()
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

private fun DrawScope.drawOverflow(overflow: Overflow, topLimit: Int, textMeasurer: TextMeasurer, style: TextStyle) {
    val layout = textMeasurer.measure("+${overflow.count}", style)
    val pad = OverflowPadding.toPx()
    val boxSize = Size(layout.size.width + 2 * pad, layout.size.height + pad)
    val topLeft = Offset(overflow.centerX - boxSize.width / 2, topLimit.toFloat())
    drawRoundRect(OverflowScrim, topLeft, boxSize, CornerRadius(pad))
    drawText(layout, topLeft = topLeft + Offset(pad, pad / 2))
}
