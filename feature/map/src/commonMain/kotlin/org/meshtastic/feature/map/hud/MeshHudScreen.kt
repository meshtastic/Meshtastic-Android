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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.compass_location_disabled
import org.meshtastic.core.resources.compass_no_location_fix
import org.meshtastic.core.resources.compass_no_location_permission
import org.meshtastic.core.resources.compass_no_magnetometer
import org.meshtastic.core.resources.mesh_hud_aligned
import org.meshtastic.core.resources.mesh_hud_camera_rationale
import org.meshtastic.core.resources.mesh_hud_cardinal_east
import org.meshtastic.core.resources.mesh_hud_cardinal_north
import org.meshtastic.core.resources.mesh_hud_cardinal_south
import org.meshtastic.core.resources.mesh_hud_cardinal_west
import org.meshtastic.core.resources.mesh_hud_drag_to_align
import org.meshtastic.core.resources.mesh_hud_elevation_approximate
import org.meshtastic.core.resources.mesh_hud_heading
import org.meshtastic.core.resources.mesh_hud_heading_accuracy
import org.meshtastic.core.resources.mesh_hud_heading_low_accuracy
import org.meshtastic.core.resources.mesh_hud_inside_geofence
import org.meshtastic.core.resources.mesh_hud_inside_unnamed_geofence
import org.meshtastic.core.resources.mesh_hud_nearby
import org.meshtastic.core.resources.mesh_hud_no_nodes
import org.meshtastic.core.resources.mesh_hud_on_target
import org.meshtastic.core.resources.mesh_hud_own_accuracy
import org.meshtastic.core.resources.mesh_hud_range_all
import org.meshtastic.core.resources.mesh_hud_range_within
import org.meshtastic.core.resources.mesh_hud_range_within_hidden
import org.meshtastic.core.resources.mesh_hud_tilt_down
import org.meshtastic.core.resources.mesh_hud_tilt_up
import org.meshtastic.core.resources.mesh_hud_turn_left
import org.meshtastic.core.resources.mesh_hud_turn_right
import org.meshtastic.core.resources.mesh_hud_waiting_sensor
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.resources.traceroute_outgoing_route
import org.meshtastic.core.resources.traceroute_return_route
import org.meshtastic.core.resources.traceroute_showing_nodes
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.KeepScreenOn
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberCameraPermissionState
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.proto.Waypoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/** Calibration status at or below this (SENSOR_STATUS_ACCURACY_LOW) asks the user to calibrate. */
private const val LOW_CALIBRATION = 1

/** A heading error estimate above this asks the user to calibrate too. */
private const val LOW_ACCURACY_DEG = 20.0
private const val FULL_CIRCLE_DEG = 360.0

/** How long the sensor may stay silent before the screen says it is waiting for it. */
private const val SENSOR_WAIT_MS = 2_000L

/** The finder counts as on target within this many degrees, or the target's own spread when that is wider. */
private const val ON_TARGET_DEG = 5.0

/** Turn hints move in steps this coarse, so the label does not churn with every sensor sample. */
private const val HINT_STEP_DEG = 5
private val HudScrim = Color.Black.copy(alpha = 0.55f)
private val TapeGap = 4.dp
private const val HALF_TURN_DEG = 180.0

/** A heading correction smaller than this is not worth a reset control. */
private const val MIN_SHOWN_OFFSET_DEG = 0.5

/** Pitched further down than this, the user is looking for something close by, so the radar grows. */
private const val POINTING_DOWN_DEG = -45.0

/** The band under the top bar the compass tape takes, which chips stay out of. */
private val TapeClearance = 52.dp

/** Where the finder's target is from the center of the view, rounded to [HINT_STEP_DEG]. */
private sealed interface FinderHint {
    data object OnTarget : FinderHint

    data class Turn(val degrees: Int, val right: Boolean) : FinderHint

    data class Tilt(val degrees: Int, val up: Boolean) : FinderHint
}

@Suppress("LongMethod", "UnnecessaryLaunchedEffect")
@Composable
fun MeshHudScreen(
    focusNodeNum: Int?,
    onNavigateUp: () -> Unit,
    onOpenNode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tracerouteDestNum: Int? = null,
    tracerouteRequestId: Int? = null,
    tracerouteLogUuid: String? = null,
    trackNodeNum: Int? = null,
    viewModel: MeshHudViewModel = koinViewModel(),
) {
    LaunchedEffect(tracerouteDestNum, tracerouteRequestId, tracerouteLogUuid) {
        viewModel.showTraceroute(tracerouteDestNum, tracerouteRequestId, tracerouteLogUuid)
    }
    LaunchedEffect(trackNodeNum) { viewModel.showTrack(trackNodeNum) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val alertOptIns by viewModel.geofenceAlertOptIns.collectAsStateWithLifecycle()
    var infoWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    val track by viewModel.track.collectAsStateWithLifecycle()
    KeepScreenOn(true)
    // Keyed, not a SideEffect: the user's own focus choices must survive recomposition.
    LaunchedEffect(focusNodeNum) { viewModel.setFocusNode(focusNodeNum) }
    val camera = rememberCameraPermissionState()
    val location = rememberLocationPermissionState()
    // One system dialog at a time, and only ever asked once: a recreated screen must not prompt again. These stay
    // keyed effects because each must fire once per key change, which SideEffect does not do.
    LaunchedEffect(Unit) { if (camera.status == PermissionStatus.NOT_REQUESTED) camera.request() }
    LaunchedEffect(camera.status) {
        if (camera.status != PermissionStatus.NOT_REQUESTED && location.status == PermissionStatus.NOT_REQUESTED) {
            location.request()
        }
    }
    SideEffect { viewModel.setLocationPermissionGranted(location.isGranted) }
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val poseState = viewModel.pose.collectAsStateWithLifecycle()
    var intrinsics by remember { mutableStateOf<CameraIntrinsics?>(null) }
    var topBarBottomPx by remember { mutableIntStateOf(0) }
    var bottomPanelTopPx by remember { mutableIntStateOf(0) }
    var sensorSilent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SENSOR_WAIT_MS)
        sensorSilent = true
    }

    val currentTargets by rememberUpdatedState(targets)
    val focused by remember { derivedStateOf { currentTargets.targets.firstOrNull { it.focused } } }
    // Derived, so the labels recompose on a whole-degree change rather than at the sensor rate.
    val headingDeg by remember {
        derivedStateOf {
            poseState.value.pose?.let {
                ((it.azimuthDeg + currentTargets.declinationDeg + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG).roundToInt()
            }
        }
    }
    val headingAccuracyDeg by remember { derivedStateOf { poseState.value.headingAccuracyDeg?.roundToInt() } }
    val needsCalibration by remember {
        derivedStateOf {
            val pose = poseState.value
            pose.pose != null &&
                (pose.calibration in 0..LOW_CALIBRATION || (pose.headingAccuracyDeg ?: 0.0) > LOW_ACCURACY_DEG)
        }
    }
    val hasSensor by remember { derivedStateOf { poseState.value.hasSensor } }
    val headingOffsetDeg by viewModel.headingOffsetDeg.collectAsStateWithLifecycle()
    val waitingForSensor by remember {
        derivedStateOf { sensorSilent && poseState.value.hasSensor && poseState.value.pose == null }
    }
    val hint by remember { derivedStateOf { focused?.let { finderHint(it, poseState.value.pose) } } }
    val pointingDown by remember { derivedStateOf { (poseState.value.pose?.pitchDeg ?: 0.0) < POINTING_DOWN_DEG } }
    val northLabel = stringResource(Res.string.mesh_hud_cardinal_north)
    FinderHaptics(locked = hint == FinderHint.OnTarget)

    val tapeClearancePx = with(LocalDensity.current) { TapeClearance.roundToPx() }
    val textMeasurer = rememberTextMeasurer()
    val tapeStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White)
    val cardinals =
        Cardinals(
            north = stringResource(Res.string.mesh_hud_cardinal_north),
            east = stringResource(Res.string.mesh_hud_cardinal_east),
            south = stringResource(Res.string.mesh_hud_cardinal_south),
            west = stringResource(Res.string.mesh_hud_cardinal_west),
        )

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val focalPx = intrinsics?.focalPx(width, height) ?: fallbackFocalPx(width, height)

        if (camera.isGranted) HudCameraPreview(onIntrinsics = { intrinsics = it }, modifier = Modifier.fillMaxSize())

        val pxPerDeg = focalPx * (PI / HALF_TURN_DEG).toFloat()
        // Dragging the empty view sideways lines the overlay up with a landmark, correcting a compass that metal or a
        // fixed accuracy report leaves off. Chips sit above and take their own taps.
        Canvas(
            Modifier.fillMaxSize().pointerInput(pxPerDeg) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    viewModel.nudgeHeading(-dragAmount / pxPerDeg.toDouble())
                }
            },
        ) {
            val state = poseState.value
            val pose = state.pose ?: return@Canvas
            drawHorizon(pose, focalPx)
            targets.fences.forEach { drawGeofence(it, pose, focalPx) }
            track?.let { drawTrack(it, pose, focalPx) }
            route?.let {
                drawRoute(
                    it,
                    pose,
                    focalPx,
                    TracerouteColors.OutgoingRoute,
                    TracerouteColors.ReturnRoute,
                    textMeasurer,
                    tapeStyle,
                )
            }
            targets.targets.forEach { drawPrecisionZone(it, pose, focalPx, state.headingAccuracyDeg) }
            focused?.let { drawReticle(Color(it.subject.color), locked = finderHint(it, pose) == FinderHint.OnTarget) }
            drawCompassTape(
                trueHeadingDeg = (pose.azimuthDeg + targets.declinationDeg + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG,
                declinationDeg = targets.declinationDeg,
                targets = targets.targets,
                focalPx = focalPx,
                topPx = topBarBottomPx + TapeGap.toPx(),
                cardinals = cardinals,
                textMeasurer = textMeasurer,
                labelStyle = tapeStyle,
            )
        }
        HudChipLayer(
            targets = targets.targets,
            currentPose = { poseState.value.pose },
            focalPx = focalPx,
            topLimitPx = { topBarBottomPx + tapeClearancePx },
            bottomLimitPx = { bottomPanelTopPx },
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            HudChip(
                target = target,
                // A node opens its detail and a waypoint its info, as on the map; long press focuses either.
                onOpen = {
                    when (val subject = target.subject) {
                        is HudSubject.NodeSubject -> onOpenNode(subject.node.num)
                        is HudSubject.WaypointSubject -> infoWaypoint = subject.waypoint
                    }
                },
                onToggleFocus = { viewModel.toggleFocus(target.subject) },
            )
        }

        HudTopBar(
            headingDeg = headingDeg,
            headingAccuracyDeg = headingAccuracyDeg,
            hasSensor = hasSensor,
            headingOffsetDeg = headingOffsetDeg,
            onResetHeading = viewModel::resetHeadingOffset,
            needsCalibration = needsCalibration,
            waitingForSensor = waitingForSensor,
            targets = targets,
            onNavigateUp = onNavigateUp,
            onToggleRange = viewModel::toggleRangeLimit,
            modifier =
            Modifier.align(Alignment.TopStart).onPlaced {
                topBarBottomPx = (it.positionInParent().y + it.size.height).toInt()
            },
        )
        HudBottomPanel(
            targets = targets,
            hint = hint,
            route = route,
            radar = {
                NearbyRadar(
                    nearby = targets.directionUnknown,
                    ownAccuracyM = targets.ownAccuracyM,
                    units = targets.units,
                    northLabel = northLabel,
                    declinationDeg = targets.declinationDeg,
                    currentHeadingDeg = { poseState.value.pose?.azimuthDeg },
                    expanded = pointingDown,
                    onOpen = { subject -> (subject as? HudSubject.NodeSubject)?.let { onOpenNode(it.node.num) } },
                )
            },
            cameraContent = {
                if (!camera.isGranted) {
                    PermissionRecoveryCard(camera, stringResource(Res.string.mesh_hud_camera_rationale))
                }
            },
            modifier =
            Modifier.align(Alignment.BottomStart).onPlaced { bottomPanelTopPx = it.positionInParent().y.toInt() },
        )
    }

    // Read-only here: editing a waypoint re-broadcasts it, which belongs on the map with its pickers.
    infoWaypoint?.let { waypoint ->
        WaypointInfoDialog(
            waypoint = waypoint,
            displayUnits = targets.units,
            alertsEnabled = waypoint.id in alertOptIns,
            onToggleAlerts = { viewModel.setGeofenceAlertOptIn(waypoint.id, it) },
            onDismissRequest = { infoWaypoint = null },
        )
    }
}

/** Where [target] is from the center of the view, or null before the first pose. */
private fun finderHint(target: HudTarget, pose: CameraPose?): FinderHint? {
    pose ?: return null
    val yaw = signedTurnDeg(pose.azimuthDeg, target.bearingDeg)
    val pitch = target.elevationDeg - pose.pitchDeg
    val tolerance = maxOf(ON_TARGET_DEG, target.precisionHalfAngleDeg ?: 0.0)
    fun step(deg: Double) = (abs(deg) / HINT_STEP_DEG).roundToInt().coerceAtLeast(1) * HINT_STEP_DEG
    return when {
        abs(yaw) > tolerance -> FinderHint.Turn(step(yaw), right = yaw > 0)
        abs(pitch) > ON_TARGET_DEG -> FinderHint.Tilt(step(pitch), up = pitch > 0)
        else -> FinderHint.OnTarget
    }
}

/** One firm pulse as the finder locks on, so the user can stop sweeping without looking at the screen. */
@Suppress("UnnecessaryLaunchedEffect") // One pulse per lock, not one per recomposition.
@Composable
private fun FinderHaptics(locked: Boolean) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(locked) { if (locked) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
}

@Suppress("LongParameterList")
@Composable
private fun HudTopBar(
    headingDeg: Int?,
    headingAccuracyDeg: Int?,
    hasSensor: Boolean,
    headingOffsetDeg: Double,
    onResetHeading: () -> Unit,
    needsCalibration: Boolean,
    waitingForSensor: Boolean,
    targets: MeshHudTargets,
    onNavigateUp: () -> Unit,
    onToggleRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateUp) {
                Icon(MeshtasticIcons.ArrowBack, stringResource(Res.string.navigate_back), tint = Color.White)
            }
            headingDeg?.let { heading ->
                val headingText = formatString("%d°", heading)
                HudLabel(
                    headingAccuracyDeg?.let {
                        stringResource(Res.string.mesh_hud_heading_accuracy, headingText, formatString("%d°", it))
                    } ?: stringResource(Res.string.mesh_hud_heading, headingText),
                )
            }
            Spacer(Modifier.width(8.dp))
            HudRangeToggle(targets, onToggleRange)
        }
        targets.ownAccuracyM?.let { accuracy ->
            HudLabel(
                stringResource(
                    Res.string.mesh_hud_own_accuracy,
                    accuracy.toInt().coerceAtLeast(1).toDistanceString(targets.units),
                ),
            )
        }
        if (abs(headingOffsetDeg) >= MIN_SHOWN_OFFSET_DEG) {
            HudLabel(
                text =
                stringResource(
                    Res.string.mesh_hud_aligned,
                    (if (headingOffsetDeg >= 0) "+" else "-") + formatString("%.0f°", abs(headingOffsetDeg)),
                ),
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onResetHeading),
            )
        }
        when {
            !hasSensor -> HudLabel(stringResource(Res.string.compass_no_magnetometer))

            waitingForSensor -> HudLabel(stringResource(Res.string.mesh_hud_waiting_sensor))

            needsCalibration -> {
                HudLabel(stringResource(Res.string.mesh_hud_heading_low_accuracy))
                HudLabel(stringResource(Res.string.mesh_hud_drag_to_align))
            }
        }
    }
}

@Composable
private fun HudBottomPanel(
    targets: MeshHudTargets,
    hint: FinderHint?,
    route: HudRoute?,
    radar: @Composable () -> Unit,
    cameraContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hint?.let { FinderHintLabel(it, Modifier.align(Alignment.CenterHorizontally)) }
        route?.let { RouteLegend(it) }
        cameraContent()
        targets.warnings.forEach { HudLabel(stringResource(it.label())) }
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
        if (targets.warnings.isEmpty() && targets.targets.isEmpty() && targets.directionUnknown.isEmpty()) {
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

private fun HudWarning.label() = when (this) {
    HudWarning.NO_LOCATION_PERMISSION -> Res.string.compass_no_location_permission
    HudWarning.LOCATION_DISABLED -> Res.string.compass_location_disabled
    HudWarning.NO_LOCATION_FIX -> Res.string.compass_no_location_fix
}

@Composable
private fun HudRangeToggle(targets: MeshHudTargets, onToggle: () -> Unit) {
    val text =
        when {
            !targets.rangeLimited -> stringResource(Res.string.mesh_hud_range_all)

            targets.hiddenByRange > 0 ->
                stringResource(Res.string.mesh_hud_range_within_hidden, targets.nearRangeText, targets.hiddenByRange)

            else -> stringResource(Res.string.mesh_hud_range_within, targets.nearRangeText)
        }
    HudLabel(
        text = text,
        modifier =
        Modifier.clip(MaterialTheme.shapes.small)
            .toggleable(value = targets.rangeLimited, role = Role.Switch, onValueChange = { onToggle() }),
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
