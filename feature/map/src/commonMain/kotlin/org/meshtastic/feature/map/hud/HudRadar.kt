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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.util.toCodePointString
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.model.util.waypointIconOrDefault
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_hud_radar_description
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

private const val MIN_SCALE_M = 10.0
private const val SCALE_HEADROOM = 1.15
private const val OWN_RING_SCALE = 1.5
private const val NICE_STEP_TWO = 2.0
private const val NICE_STEP_FIVE = 5.0
private const val TEN = 10.0
private const val RING_ALPHA = 0.5f
private const val OWN_FILL_ALPHA = 0.18f
private const val NODE_RING_ALPHA = 0.25f
private val CompactRadar = 168.dp
private val ExpandedRadar = 300.dp
private val RadarDot = 30.dp
private val RadarScrim = Color.Black.copy(alpha = HUD_SCRIM_ALPHA)

/** The smallest of 1, 2 or 5 times a power of ten that is at least [meters]. */
internal fun niceScaleM(meters: Double): Double {
    val magnitude = TEN.pow(floor(log10(meters)))
    return listOf(1.0, NICE_STEP_TWO, NICE_STEP_FIVE, TEN).map { it * magnitude }.first { it >= meters }
}

/**
 * Where a nearby target sits on a heading-up radar of [radiusPx], [scaleM] to the rim, as an offset from its center:
 * the camera's heading [headingDeg] points up the screen, like the view behind it.
 */
internal fun radarOffset(target: NearbyTarget, headingDeg: Double, scaleM: Double, radiusPx: Float): Offset {
    val rel = (target.bearingDeg - headingDeg).toRadians()
    val r = (target.distanceM / scaleM).coerceAtMost(1.0) * radiusPx
    return Offset((r * sin(rel)).toFloat(), (-r * cos(rel)).toFloat())
}

/**
 * A heading-up plan view of the targets too close to point at, with the phone at the center inside its own accuracy
 * ring and each target inside its precision ring. Tilting the phone toward the ground [expanded]s it, since that is the
 * posture of someone looking for something close by. The open [route] and [track] are drawn under the dots. The heading
 * is read in layout and draw, so it turns with the camera without recomposing.
 */
@Suppress("LongMethod")
@Composable
internal fun NearbyRadar(
    nearby: List<NearbyTarget>,
    ownAccuracyM: Float?,
    units: MeasurementSystem,
    northLabel: String,
    declinationDeg: Float,
    currentHeadingDeg: () -> Double?,
    expanded: Boolean,
    onOpen: (HudSubject) -> Unit,
    modifier: Modifier = Modifier,
    route: HudRoute? = null,
    track: HudTrack? = null,
) {
    val radarSize by animateDpAsState(if (expanded) ExpandedRadar else CompactRadar)
    val scaleM =
        remember(nearby, ownAccuracyM) {
            val farthest = nearby.maxOfOrNull { it.distanceM + it.radiusM } ?: 0.0
            niceScaleM(maxOf(MIN_SCALE_M, farthest * SCALE_HEADROOM, (ownAccuracyM ?: 0f) * OWN_RING_SCALE))
        }
    val scaleText = scaleM.toInt().toDistanceString(units)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White, textAlign = TextAlign.Center)
    Layout(
        content = {
            nearby.forEach {
                key(it.subject.key) {
                    val description =
                        stringResource(
                            Res.string.mesh_hud_radar_description,
                            it.subject.spokenName(),
                            it.distanceM.toInt().toDistanceString(units),
                        )
                    RadarDot(it.subject, description) { onOpen(it.subject) }
                }
            }
        },
        modifier =
        modifier.size(radarSize).clip(CircleShape).background(RadarScrim).drawBehind {
            val heading = currentHeadingDeg() ?: 0.0
            val radius = this.size.minDimension / 2
            val pxPerM = radius / scaleM.toFloat()
            drawCircle(Color.White.copy(alpha = RING_ALPHA), radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = RING_ALPHA), radius / 2, style = Stroke(1.dp.toPx()))
            ownAccuracyM?.let { drawCircle(Color.White.copy(alpha = OWN_FILL_ALPHA), it * pxPerM) }
            drawRadarPaths(route, track, heading, pxPerM)
            drawCircle(Color.White, 3.dp.toPx())
            nearby.forEach { target ->
                if (target.radiusM > 0) {
                    drawCircle(
                        Color(target.subject.color).copy(alpha = NODE_RING_ALPHA),
                        (target.radiusM * pxPerM).toFloat(),
                        center + radarOffset(target, heading, scaleM, radius),
                    )
                }
            }
            // True north on the rim, matching the tape and heading, so the plan view reads against a map. The radar
            // turns with the magnetic camera heading, and true north lies the declination west of magnetic.
            val north = textMeasurer.measure(northLabel, labelStyle)
            val northRad = (-declinationDeg - heading).toRadians()
            val northR = radius - north.size.height
            val northAt = center + Offset((northR * sin(northRad)).toFloat(), (-northR * cos(northRad)).toFloat())
            drawText(north, topLeft = northAt - Offset(north.size.width / 2f, north.size.height / 2f))
            val scale = textMeasurer.measure(scaleText, labelStyle)
            drawText(
                scale,
                topLeft = Offset(center.x - scale.size.width / 2f, this.size.height - scale.size.height - 2f),
            )
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val heading = currentHeadingDeg() ?: 0.0
            val radius = minOf(constraints.maxWidth, constraints.maxHeight) / 2f
            placeables.forEachIndexed { i, placeable ->
                val at = radarOffset(nearby[i], heading, scaleM, radius)
                placeable.place(
                    (radius + at.x - placeable.width / 2f).toInt(),
                    (radius + at.y - placeable.height / 2f).toInt(),
                )
            }
        }
    }
}

/** A compact marker for the radar: the node's color and short name, or the waypoint's emoji. */
@Composable
private fun RadarDot(subject: HudSubject, description: String, onActivate: () -> Unit) {
    val label =
        when (subject) {
            is HudSubject.NodeSubject -> subject.node.user.short_name.ifEmpty { "?" }
            is HudSubject.WaypointSubject -> subject.waypoint.icon.waypointIconOrDefault().toCodePointString()
        }
    val textColor =
        when (subject) {
            is HudSubject.NodeSubject -> Color(subject.node.colors.first)
            is HudSubject.WaypointSubject -> Color.Black
        }
    // Cleared semantics drop the click action too, so TalkBack's activation is set back explicitly.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier.minimumInteractiveComponentSize().clickable(onClick = onActivate).clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick {
                onActivate()
                true
            }
        },
    ) {
        when (subject) {
            is HudSubject.NodeSubject ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(RadarDot).clip(CircleShape).background(Color(subject.color)),
                ) {
                    Text(label, color = textColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

            // A place, not a node: the bare emoji, as in the camera view.
            is HudSubject.WaypointSubject -> Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
