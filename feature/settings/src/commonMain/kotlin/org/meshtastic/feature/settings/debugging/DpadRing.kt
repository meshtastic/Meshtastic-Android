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
package org.meshtastic.feature.settings.debugging

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mirror_key_down
import org.meshtastic.core.resources.mirror_key_left
import org.meshtastic.core.resources.mirror_key_ok
import org.meshtastic.core.resources.mirror_key_right
import org.meshtastic.core.resources.mirror_key_up
import org.meshtastic.core.ui.icon.KeyboardArrowDown
import org.meshtastic.core.ui.icon.KeyboardArrowLeft
import org.meshtastic.core.ui.icon.KeyboardArrowRight
import org.meshtastic.core.ui.icon.KeyboardArrowUp
import org.meshtastic.core.ui.icon.MeshtasticIcons
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

// Geometry per the TV-remote research: ~220dp ring, 72-80dp center disc,
// a radial dead band between disc and ring so ambiguous presses do nothing.
private val RING_DIAMETER = 220.dp
private val OK_DISC_DIAMETER = 76.dp
private val DEAD_ZONE = 12.dp

private const val WEDGE_SWEEP_DEG = 90f
private const val FULL_CIRCLE_DEG = 360f
private const val RAD_TO_DEG = (180.0 / PI).toFloat()

// Minimum comfortable assistive-tech target (M3).
private val SEMANTICS_TARGET = 48.dp

// One direction wedge of the ring: hit-test bounds, highlight arc, icon placement.
private class Wedge(
    val eventCode: Int,
    val label: StringResource,
    val startAngleDeg: Float,
    val iconOffsetX: Dp,
    val iconOffsetY: Dp,
)

@Suppress("MagicNumber")
private fun buildWedges(iconRadius: Dp): List<Wedge> = listOf(
    Wedge(INPUT_RIGHT, Res.string.mirror_key_right, -45f, iconRadius, 0.dp),
    Wedge(INPUT_DOWN, Res.string.mirror_key_down, 45f, 0.dp, iconRadius),
    Wedge(INPUT_LEFT, Res.string.mirror_key_left, 135f, -iconRadius, 0.dp),
    Wedge(INPUT_UP, Res.string.mirror_key_up, 225f, 0.dp, -iconRadius),
)

@Composable
private fun wedgeIcon(eventCode: Int) = when (eventCode) {
    INPUT_UP -> MeshtasticIcons.KeyboardArrowUp
    INPUT_DOWN -> MeshtasticIcons.KeyboardArrowDown
    INPUT_LEFT -> MeshtasticIcons.KeyboardArrowLeft
    else -> MeshtasticIcons.KeyboardArrowRight
}

private fun List<Wedge>.at(angleDeg: Float): Wedge = first { wedge ->
    val start = (wedge.startAngleDeg + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
    val normalized = if (angleDeg < start) angleDeg + FULL_CIRCLE_DEG else angleDeg
    normalized >= start && normalized < start + WEDGE_SWEEP_DEG
}

/**
 * Circular 5-way remote cluster: four direction wedges around a center OK disc, the layout every TV remote converged
 * on. Directions fire on press and auto-repeat while held; OK taps SELECT and long-presses SELECT_LONG. Wedge
 * hit-testing is by angle with a dead band around the disc; each wedge also carries its own semantics node so screen
 * readers see four buttons, not one blob.
 */
@Composable
internal fun DpadRing(
    enabled: Boolean,
    onEvent: (Int) -> Unit,
    onSelect: () -> Unit,
    onSelectLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var pressedWedge by remember { mutableStateOf<Int?>(null) }
    // Icons and semantics targets sit on the ring stroke's center line.
    val wedges = remember { buildWedges((RING_DIAMETER / 2 + OK_DISC_DIAMETER / 2 + DEAD_ZONE) / 2) }

    val ringColor =
        if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val pressedColor = MaterialTheme.colorScheme.primaryContainer

    Box(modifier = modifier.size(RING_DIAMETER), contentAlignment = Alignment.Center) {
        Canvas(
            modifier =
            Modifier.size(RING_DIAMETER)
                .ringPresses(enabled, wedges, haptics, onPressChange = { pressedWedge = it }, onEvent = onEvent),
        ) {
            drawRing(ringColor, pressedColor, wedges, pressedWedge)
        }
        WedgeDecorations(wedges = wedges, enabled = enabled, onEvent = onEvent)
        OkDisc(enabled = enabled, haptics = haptics, onSelect = onSelect, onSelectLong = onSelectLong)
    }
}

/** Press handling for the ring: angle hit-testing outside the dead band, then fire + auto-repeat until release. */
private fun Modifier.ringPresses(
    enabled: Boolean,
    wedges: List<Wedge>,
    haptics: HapticFeedback,
    onPressChange: (Int?) -> Unit,
    onEvent: (Int) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val outerR = size.width / 2f
    val innerR = (OK_DISC_DIAMETER.toPx() / 2f) + DEAD_ZONE.toPx()
    val center = Offset(outerR, outerR)
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown()
            val d = down.position - center
            val r = hypot(d.x, d.y)
            if (r < innerR || r > outerR) return@awaitEachGesture
            down.consume()
            val angle = (atan2(d.y, d.x) * RAD_TO_DEG + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
            val wedge = wedges.at(angle)
            onPressChange(wedge.eventCode)
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onEvent(wedge.eventCode)
            val repeater = launch {
                delay(REPEAT_INITIAL_DELAY_MS)
                while (isActive) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    onEvent(wedge.eventCode)
                    delay(REPEAT_INTERVAL_MS)
                }
            }
            try {
                waitForUpOrCancellation()
            } finally {
                // The pointerInput coroutine can be cancelled mid-press (enabled
                // flip, node removal); never leave a wedge stuck highlighted.
                repeater.cancel()
                onPressChange(null)
            }
        }
    }
}

private fun DrawScope.drawRing(ringColor: Color, pressedColor: Color, wedges: List<Wedge>, pressedWedge: Int?) {
    val outerR = size.width / 2f
    val visualInnerR = (OK_DISC_DIAMETER.toPx() / 2f) + DEAD_ZONE.toPx()
    val strokeWidth = outerR - visualInnerR
    val strokeRadius = (outerR + visualInnerR) / 2f
    drawCircle(color = ringColor, radius = strokeRadius, style = Stroke(width = strokeWidth))
    pressedWedge?.let { pressed ->
        val wedge = wedges.first { it.eventCode == pressed }
        drawArc(
            color = pressedColor,
            startAngle = wedge.startAngleDeg,
            sweepAngle = WEDGE_SWEEP_DEG,
            useCenter = false,
            topLeft = Offset(outerR - strokeRadius, outerR - strokeRadius),
            size = Size(strokeRadius * 2f, strokeRadius * 2f),
            style = Stroke(width = strokeWidth),
        )
    }
}

/** Chevron icons plus one invisible semantics button per wedge so assistive tech sees four discrete controls. */
@Composable
private fun WedgeDecorations(wedges: List<Wedge>, enabled: Boolean, onEvent: (Int) -> Unit) {
    val iconColor =
        if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    wedges.forEach { wedge ->
        val label = stringResource(wedge.label)
        Icon(
            imageVector = wedgeIcon(wedge.eventCode),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.offset(x = wedge.iconOffsetX, y = wedge.iconOffsetY),
        )
        Box(
            modifier =
            Modifier.offset(x = wedge.iconOffsetX, y = wedge.iconOffsetY).size(SEMANTICS_TARGET).semantics {
                role = Role.Button
                contentDescription = label
                onClick(label = label) {
                    if (enabled) onEvent(wedge.eventCode)
                    enabled
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OkDisc(enabled: Boolean, haptics: HapticFeedback, onSelect: () -> Unit, onSelectLong: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier.size(OK_DISC_DIAMETER)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(
                enabled = enabled,
                onLongClick = onSelectLong,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSelect()
                },
            ),
    ) {
        val okColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = stringResource(Res.string.mirror_key_ok),
            style = MaterialTheme.typography.titleMedium,
            color = okColor,
        )
    }
}
