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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.chirpy
import org.meshtastic.core.resources.compass_location_disabled
import org.meshtastic.core.resources.compass_no_location_fix
import org.meshtastic.core.resources.compass_no_magnetometer
import org.meshtastic.core.resources.img_chirpy
import org.meshtastic.core.resources.map_filter_title
import org.meshtastic.core.resources.mesh_hud_camera_unavailable
import org.meshtastic.core.resources.mesh_hud_chirpy_glyphs
import org.meshtastic.core.resources.mesh_hud_chirpy_says
import org.meshtastic.core.resources.mesh_hud_coach_dismiss
import org.meshtastic.core.resources.mesh_hud_coach_find
import org.meshtastic.core.resources.mesh_hud_coach_point
import org.meshtastic.core.resources.mesh_hud_coach_tap
import org.meshtastic.core.resources.mesh_hud_elevation_approximate
import org.meshtastic.core.resources.mesh_hud_finding
import org.meshtastic.core.resources.mesh_hud_heading
import org.meshtastic.core.resources.mesh_hud_heading_accuracy
import org.meshtastic.core.resources.mesh_hud_heading_low_accuracy
import org.meshtastic.core.resources.mesh_hud_inside_geofence
import org.meshtastic.core.resources.mesh_hud_inside_unnamed_geofence
import org.meshtastic.core.resources.mesh_hud_location_rationale
import org.meshtastic.core.resources.mesh_hud_nearby
import org.meshtastic.core.resources.mesh_hud_no_nodes
import org.meshtastic.core.resources.mesh_hud_on_target
import org.meshtastic.core.resources.mesh_hud_own_accuracy
import org.meshtastic.core.resources.mesh_hud_thermal_critical
import org.meshtastic.core.resources.mesh_hud_thermal_hot
import org.meshtastic.core.resources.mesh_hud_tilt_down
import org.meshtastic.core.resources.mesh_hud_tilt_up
import org.meshtastic.core.resources.mesh_hud_turn_left
import org.meshtastic.core.resources.mesh_hud_turn_right
import org.meshtastic.core.resources.mesh_hud_waiting_sensor
import org.meshtastic.core.resources.mesh_hud_watch_step
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.resources.traceroute_outgoing_route
import org.meshtastic.core.resources.traceroute_return_route
import org.meshtastic.core.resources.traceroute_showing_nodes
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation
import org.meshtastic.core.ui.icon.Tune
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.PermissionUiState

/** Dark enough that white label text keeps about 4.5:1 over a white sky. */
internal const val HUD_SCRIM_ALPHA = 0.8f
private val HudScrim = Color.Black.copy(alpha = HUD_SCRIM_ALPHA)
private const val COACH_BACKDROP_ALPHA = 0.5f
private val CoachMaxWidth = 360.dp

/** Where the finder's target is from the center of the view, in whole hint steps. */
internal sealed interface FinderHint {
    data object OnTarget : FinderHint

    data class Turn(val degrees: Int, val right: Boolean) : FinderHint

    data class Tilt(val degrees: Int, val up: Boolean) : FinderHint
}

/** What the sensors and the phone itself report, for the top bar. */
internal data class HudSensorStatus(
    val headingDeg: Int?,
    val headingAccuracyDeg: Int?,
    val hasSensor: Boolean,
    val needsCalibration: Boolean,
    val waitingForSensor: Boolean,
    val thermal: HudThermal,
)

/** The heading and the phone's own accuracy beside it; [onOpenFilters] is null in a view scoped to one purpose. */
@Composable
internal fun HudTopBar(
    status: HudSensorStatus,
    targets: MeshHudTargets,
    onNavigateUp: () -> Unit,
    onOpenFilters: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateUp) {
            Icon(MeshtasticIcons.ArrowBack, stringResource(Res.string.navigate_back), tint = Color.White)
        }
        status.headingDeg?.let { heading ->
            val headingText = formatString("%d°", heading)
            HudLabel(
                status.headingAccuracyDeg?.let {
                    stringResource(Res.string.mesh_hud_heading_accuracy, headingText, formatString("%d°", it))
                } ?: stringResource(Res.string.mesh_hud_heading, headingText),
            )
        }
        targets.ownAccuracyM?.let { accuracy ->
            Spacer(Modifier.width(6.dp))
            HudLabel(
                stringResource(
                    Res.string.mesh_hud_own_accuracy,
                    accuracy.toInt().coerceAtLeast(1).toDistanceString(targets.units),
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        onOpenFilters?.let { open ->
            IconButton(onClick = open) {
                Icon(MeshtasticIcons.Tune, stringResource(Res.string.map_filter_title), tint = Color.White)
            }
        }
    }
}

/** Sensor and phone notices, under the compass tape so they never push it down. */
@Composable
internal fun HudTopNotices(status: HudSensorStatus, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            !status.hasSensor -> HudLabel(stringResource(Res.string.compass_no_magnetometer))
            status.waitingForSensor -> HudLabel(stringResource(Res.string.mesh_hud_waiting_sensor))
            status.needsCalibration -> HudLabel(stringResource(Res.string.mesh_hud_heading_low_accuracy))
        }
        when (status.thermal) {
            HudThermal.NORMAL -> Unit
            HudThermal.HOT -> HudLabel(stringResource(Res.string.mesh_hud_thermal_hot))
            HudThermal.CRITICAL -> HudLabel(stringResource(Res.string.mesh_hud_thermal_critical))
        }
    }
}

/** What the bottom panel reports besides the targets themselves. */
internal data class HudNotices(val cameraUnavailable: Boolean, val walking: Boolean)

@Suppress("LongParameterList")
@Composable
internal fun HudBottomPanel(
    targets: MeshHudTargets,
    hint: FinderHint?,
    route: HudRoute?,
    notices: HudNotices,
    location: PermissionUiState,
    onOpenLocationSettings: () -> Unit,
    radar: @Composable () -> Unit,
    cameraContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (notices.walking) HudLabel(stringResource(Res.string.mesh_hud_watch_step))
        targets.finding?.let {
            HudLabel(
                stringResource(Res.string.mesh_hud_finding, it.subject.spokenName(), it.distanceText),
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
        hint?.let { FinderHintLabel(it, Modifier.align(Alignment.CenterHorizontally)) }
        route?.let { RouteLegend(it) }
        cameraContent()
        if (notices.cameraUnavailable) HudLabel(stringResource(Res.string.mesh_hud_camera_unavailable))
        LocationWarnings(targets.warnings, location, onOpenLocationSettings)
        targets.fences
            .filter { it.inside }
            .forEach { fence ->
                HudLabel(
                    if (fence.name.isNotBlank()) {
                        stringResource(Res.string.mesh_hud_inside_geofence, fence.name)
                    } else {
                        stringResource(Res.string.mesh_hud_inside_unnamed_geofence)
                    },
                )
            }
        if (targets.ready && route == null && targets.showsNothing) {
            HudLabel(stringResource(Res.string.mesh_hud_no_nodes))
        }
        if (targets.elevationApproximate && targets.targets.isNotEmpty()) {
            HudLabel(stringResource(Res.string.mesh_hud_elevation_approximate))
        }
        if (targets.directionUnknown.isNotEmpty()) {
            HudLabel(stringResource(Res.string.mesh_hud_nearby))
            radar()
        }
    }
}

/** Each location warning with the way out of it: the permission card, or the system's location settings. */
@Composable
private fun LocationWarnings(
    warnings: List<HudWarning>,
    location: PermissionUiState,
    onOpenLocationSettings: () -> Unit,
) {
    warnings.forEach { warning ->
        when (warning) {
            HudWarning.NO_LOCATION_PERMISSION ->
                PermissionRecoveryCard(location, stringResource(Res.string.mesh_hud_location_rationale))

            HudWarning.LOCATION_DISABLED ->
                Button(onClick = onOpenLocationSettings) {
                    Icon(MeshtasticIcons.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.compass_location_disabled))
                }

            HudWarning.NO_LOCATION_FIX -> HudLabel(stringResource(Res.string.compass_no_location_fix))
        }
    }
}

/**
 * The first open's coaching: how to hold the phone, and what a tap and a long press do. Shown once, over the view, and
 * gone at the first tap on its button.
 */
@Composable
internal fun HudCoach(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = COACH_BACKDROP_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
            Modifier.widthIn(max = CoachMaxWidth)
                .padding(24.dp)
                .background(HudScrim, MaterialTheme.shapes.large)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChirpyWarning(Modifier.align(Alignment.CenterHorizontally))
            listOf(Res.string.mesh_hud_coach_point, Res.string.mesh_hud_coach_tap, Res.string.mesh_hud_coach_find)
                .forEach { Text(stringResource(it), color = Color.White, style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.mesh_hud_coach_dismiss))
            }
        }
    }
}

/** Chirpy's word on what camera, GPS and sensors running together cost, as on the firmware update's warning. */
@Composable
private fun ChirpyWarning(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.mesh_hud_chirpy_glyphs),
                style = MaterialTheme.typography.headlineLarge,
            )
            Image(
                painter = painterResource(Res.drawable.img_chirpy),
                contentScale = ContentScale.Fit,
                contentDescription = stringResource(Res.string.chirpy),
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            text = stringResource(Res.string.mesh_hud_chirpy_says),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** The traceroute's two colors, as on the map, and how many of its nodes have a position to draw. */
@Composable
private fun RouteLegend(route: HudRoute) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HudLabel(stringResource(Res.string.traceroute_showing_nodes, route.mappable, route.total))
        HudLabel(
            stringResource(Res.string.traceroute_outgoing_route),
            Modifier.border(2.dp, TracerouteColors.OutgoingRoute, MaterialTheme.shapes.small),
        )
        if (route.back != null) {
            HudLabel(
                stringResource(Res.string.traceroute_return_route),
                Modifier.border(2.dp, TracerouteColors.ReturnRoute, MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun FinderHintLabel(hint: FinderHint, modifier: Modifier = Modifier) {
    val text =
        when (hint) {
            FinderHint.OnTarget -> stringResource(Res.string.mesh_hud_on_target)

            is FinderHint.Turn ->
                stringResource(
                    if (hint.right) Res.string.mesh_hud_turn_right else Res.string.mesh_hud_turn_left,
                    hint.degrees,
                )

            is FinderHint.Tilt ->
                stringResource(
                    if (hint.up) Res.string.mesh_hud_tilt_up else Res.string.mesh_hud_tilt_down,
                    hint.degrees,
                )
        }
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier =
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(HudScrim, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
internal fun HudLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier =
        modifier.background(HudScrim, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
