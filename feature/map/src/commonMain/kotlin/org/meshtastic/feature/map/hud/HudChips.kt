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

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.util.toCodePointString
import org.meshtastic.core.model.util.waypointIconOrDefault
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.duration_ago
import org.meshtastic.core.resources.formatDuration
import org.meshtastic.core.resources.mesh_hud_chip_description
import org.meshtastic.core.resources.mesh_hud_distance_no_altitude
import org.meshtastic.core.resources.mesh_hud_find
import org.meshtastic.core.resources.mesh_hud_joined
import org.meshtastic.core.resources.mesh_hud_open_node
import org.meshtastic.core.resources.mesh_hud_open_waypoint
import org.meshtastic.core.resources.mesh_hud_rise_above
import org.meshtastic.core.resources.mesh_hud_rise_above_spoken
import org.meshtastic.core.resources.mesh_hud_rise_below
import org.meshtastic.core.resources.mesh_hud_rise_below_spoken
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.proto.Waypoint
import kotlin.math.roundToInt
import kotlin.time.DurationUnit

private const val FRESH_POSITION_SEC = 15 * 60L
private const val OLD_POSITION_SEC = 6 * 60 * 60L
private const val SECONDS_PER_HOUR = 60 * 60L
private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
private const val OLDEST_ALPHA = 0.45f
private const val WAYPOINT_SHADOW_ALPHA = 0.8f
private const val WAYPOINT_SHADOW_BLUR = 8f

/** Fully opaque while fresh, fading toward [OLDEST_ALPHA] as the position ages, so stale fixes read as stale. */
private fun ageAlpha(ageSec: Long?): Float {
    val age = ageSec ?: 0L
    val t = ((age - FRESH_POSITION_SEC).toFloat() / (OLD_POSITION_SEC - FRESH_POSITION_SEC)).coerceIn(0f, 1f)
    return 1f - t * (1f - OLDEST_ALPHA)
}

private const val HOURS_ON_DIAL = 12
private const val DEG_PER_HOUR = 30.0

/** Where [bearingDeg] lies on a clock face whose 12 is the camera's heading, 1 to 12. */
internal fun clockHour(bearingDeg: Double, headingDeg: Double): Int {
    val hour = (signedTurnDeg(headingDeg, bearingDeg) / DEG_PER_HOUR).roundToInt().mod(HOURS_ON_DIAL)
    return if (hour == 0) HOURS_ON_DIAL else hour
}

/**
 * What TalkBack calls a subject: a node's long name, else its short name, else its node id, since a node never heard
 * from has neither name; a waypoint's name, else its emoji.
 */
internal fun HudSubject.spokenName(): String = when (this) {
    is HudSubject.NodeSubject ->
        node.effectiveLongName.ifBlank { node.user.short_name.ifBlank { NodeAddress.numToDefaultId(node.num) } }

    is HudSubject.WaypointSubject ->
        waypoint.name.ifBlank { waypoint.icon.waypointIconOrDefault().toCodePointString() }
}

/**
 * Tap opens a node's detail; long press makes it, or a waypoint, the finder's target. TalkBack hears its name, distance
 * and clock position from the camera's heading, and reaches the chips nearest straight ahead first.
 */
@Composable
internal fun HudChip(
    target: HudTarget,
    currentPose: () -> CameraPose?,
    onOpen: () -> Unit,
    onToggleFocus: () -> Unit,
) {
    // Derived, so the chip recomposes when its clock hour changes rather than at the sensor rate.
    val hour by
        remember(target.bearingDeg) {
            derivedStateOf { currentPose()?.let { clockHour(target.bearingDeg, it.azimuthDeg) } }
        }
    val distanceText = chipDistanceText(target, details = target.focused)
    // TalkBack always hears the rise and age: speech does not crowd the view, and fade and placement are visual only.
    val spokenDistance = chipDistanceText(target, details = true, spoken = true)
    val description = hour?.let {
        stringResource(Res.string.mesh_hud_chip_description, target.subject.spokenName(), spokenDistance, it)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
        Modifier.alpha(ageAlpha(target.positionAgeSec))
            .clip(MaterialTheme.shapes.small)
            .minimumInteractiveComponentSize()
            .combinedClickable(
                onClickLabel =
                stringResource(
                    if (target.subject is HudSubject.WaypointSubject) {
                        Res.string.mesh_hud_open_waypoint
                    } else {
                        Res.string.mesh_hud_open_node
                    },
                ),
                onLongClickLabel = stringResource(Res.string.mesh_hud_find),
                onClick = onOpen,
                onLongClick = onToggleFocus,
            )
            .semantics(mergeDescendants = true) {
                description?.let { contentDescription = it }
                hour?.let { traversalIndex = minOf(it, HOURS_ON_DIAL - it).toFloat() }
            },
    ) {
        val chipModifier =
            if (target.focused) Modifier.border(2.dp, Color.White, MaterialTheme.shapes.small) else Modifier
        SubjectChip(target.subject, chipModifier)
        HudLabel(distanceText)
    }
}

/**
 * The distance under a chip, marked when the altitude is missing, then, with [details], how far above or below the
 * phone it is and how old its position is in its largest unit. [details] is false for an unfocused chip on screen:
 * there the fade marks staleness and the point's place marks height, and more text would widen every label in a crowded
 * view. [spoken] words the rise for TalkBack rather than with an arrow glyph.
 */
@Composable
private fun chipDistanceText(target: HudTarget, details: Boolean, spoken: Boolean = false): String {
    val showsMissingAltitude = target.subject is HudSubject.NodeSubject && !target.elevationKnown
    val distance =
        if (showsMissingAltitude) {
            stringResource(Res.string.mesh_hud_distance_no_altitude, target.distanceText)
        } else {
            target.distanceText
        }
    if (!details) return distance
    return listOfNotNull(distance, target.rise?.let { riseText(it, spoken) }, ageText(target.positionAgeSec)).reduce {
            text,
            part,
        ->
        stringResource(Res.string.mesh_hud_joined, text, part)
    }
}

@Composable
private fun riseText(rise: HudRise, spoken: Boolean): String = when {
    spoken && rise.above -> stringResource(Res.string.mesh_hud_rise_above_spoken, rise.text)
    spoken -> stringResource(Res.string.mesh_hud_rise_below_spoken, rise.text)
    rise.above -> stringResource(Res.string.mesh_hud_rise_above, rise.text)
    else -> stringResource(Res.string.mesh_hud_rise_below, rise.text)
}

/** A stale position's age in its largest unit, or null while it is fresh. */
@Composable
private fun ageText(ageSec: Long?): String? {
    val age = ageSec?.takeIf { it > FRESH_POSITION_SEC } ?: return null
    val unit =
        when {
            age >= SECONDS_PER_DAY -> DurationUnit.DAYS
            age >= SECONDS_PER_HOUR -> DurationUnit.HOURS
            else -> DurationUnit.MINUTES
        }
    return stringResource(Res.string.duration_ago, formatDuration(age, unit))
}

@Composable
internal fun SubjectChip(subject: HudSubject, modifier: Modifier = Modifier) {
    when (subject) {
        is HudSubject.NodeSubject -> NodeChip(modifier = modifier, node = subject.node)
        is HudSubject.WaypointSubject -> WaypointMarker(subject.waypoint, modifier)
    }
}

/**
 * A waypoint as a bare emoji over its name, with no chip behind it, so a place reads apart from the nodes. The shadow
 * keeps the emoji legible over sky and ground alike; the name sits on the same plate as every other label.
 */
@Composable
private fun WaypointMarker(waypoint: Waypoint, modifier: Modifier = Modifier) {
    val shadow = Shadow(color = Color.Black.copy(alpha = WAYPOINT_SHADOW_ALPHA), blurRadius = WAYPOINT_SHADOW_BLUR)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            waypoint.icon.waypointIconOrDefault().toCodePointString(),
            style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow),
        )
        if (waypoint.name.isNotBlank()) HudLabel(waypoint.name)
    }
}
