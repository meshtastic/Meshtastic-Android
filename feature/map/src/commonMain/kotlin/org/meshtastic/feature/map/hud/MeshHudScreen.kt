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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_hud_camera_rationale
import org.meshtastic.core.resources.mesh_hud_cardinal_east
import org.meshtastic.core.resources.mesh_hud_cardinal_north
import org.meshtastic.core.resources.mesh_hud_cardinal_south
import org.meshtastic.core.resources.mesh_hud_cardinal_west
import org.meshtastic.core.resources.mesh_hud_more
import org.meshtastic.core.resources.mesh_hud_snr
import org.meshtastic.core.resources.now
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.KeepScreenOn
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberCameraPermissionState
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.rememberOpenLocationSettings
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.MapFilterSheet
import org.meshtastic.feature.map.component.WaypointInfoDialog
import org.meshtastic.feature.map.component.mapFilterActions
import org.meshtastic.proto.Waypoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** Calibration status at or below this (SENSOR_STATUS_ACCURACY_LOW) asks the user to calibrate. */
private const val LOW_CALIBRATION = 1

/** A heading error estimate above this asks the user to calibrate too. */
private const val LOW_ACCURACY_DEG = 20.0
private const val FULL_CIRCLE_DEG = 360.0

/** How long the sensor may stay silent before the screen says it is waiting for it. */
private const val SENSOR_WAIT_MS = 2_000L
private const val MILLIS_PER_SECOND = 1_000.0

/** How often a hot phone moves the sparks along, rather than every frame. */
private const val HOT_SPARK_TICK_MS = 100L

/** The finder counts as on target within this many degrees, or the target's own spread when that is wider. */
private const val ON_TARGET_DEG = 5.0

/** Turn hints move in steps this coarse, so the label does not churn with every sensor sample. */
private const val HINT_STEP_DEG = 5
private const val TAPE_SHADOW_ALPHA = 0.9f
private const val TAPE_SHADOW_BLUR = 6f

/** Pitched further down than this, the user is looking for something close by, so the radar grows. */
private const val POINTING_DOWN_DEG = -45.0

/** The band under the top bar the compass tape and its caret take, which chips and notices stay out of. */
private val TapeClearance = 48.dp

/** Walking pace held this long brings up the reminder to look around, so a step or two does not. */
private const val WALK_NOTICE_MS = 3_000L

/** Neither turned nor carried for this long, the phone has likely been set down, so the screen may sleep. */
private const val IDLE_SCREEN_MS = 10 * 60_000L

/** Turning the view this far counts as the user still looking. */
private const val ACTIVITY_TURN_DEG = 15

@Suppress("LongMethod", "UnnecessaryLaunchedEffect", "CyclomaticComplexMethod")
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
    onEditWaypoint: ((Int) -> Unit)? = null,
    viewModel: MeshHudViewModel = koinViewModel(),
) {
    LaunchedEffect(tracerouteDestNum, tracerouteRequestId, tracerouteLogUuid) {
        viewModel.showTraceroute(tracerouteDestNum, tracerouteRequestId, tracerouteLogUuid)
    }
    LaunchedEffect(trackNodeNum) { viewModel.showTrack(trackNodeNum) }
    // Opened for a purpose (a node, its track, a traceroute) the view shows only that; from the map, the whole mesh.
    LaunchedEffect(focusNodeNum) { viewModel.openFor(focusNodeNum) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val alertOptIns by viewModel.geofenceAlertOptIns.collectAsStateWithLifecycle()
    val coachSeen by viewModel.coachSeen.collectAsStateWithLifecycle()
    var infoWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }
    val track by viewModel.track.collectAsStateWithLifecycle()
    val camera = rememberCameraPermissionState()
    val location = rememberLocationPermissionState()
    val openLocationSettings = rememberOpenLocationSettings()
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
    var still by remember { mutableStateOf<HudStill?>(null) }
    val overlayLayer = rememberGraphicsLayer()
    var topBarBottomPx by remember { mutableIntStateOf(0) }
    var noticesHeightPx by remember { mutableIntStateOf(0) }
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
    val thermal by remember { derivedStateOf { poseState.value.thermal } }
    // The sparks' clock, read only while drawing, so a tick redraws the canvas and recomposes nothing. It runs only
    // with
    // a path open: every frame on a cool phone, a few times a second on a hot one, which still shows the direction.
    val sparkMillis = remember { mutableLongStateOf(0L) }
    val sparksOpen = route != null || track != null
    val hotTickMs = if (thermal == HudThermal.NORMAL) 0L else HOT_SPARK_TICK_MS
    LaunchedEffect(sparksOpen, hotTickMs) {
        if (!sparksOpen) return@LaunchedEffect
        val start = withFrameMillis { it } - sparkMillis.longValue
        while (true) {
            if (hotTickMs > 0) delay(hotTickMs)
            withFrameMillis { sparkMillis.longValue = it - start }
        }
    }
    val waitingForSensor by remember {
        derivedStateOf { sensorSilent && poseState.value.hasSensor && poseState.value.pose == null }
    }
    val hint by remember { derivedStateOf { focused?.let { finderHint(it, poseState.value.pose) } } }
    val pointingDown by remember { derivedStateOf { (poseState.value.pose?.pitchDeg ?: 0.0) < POINTING_DOWN_DEG } }
    val northLabel = stringResource(Res.string.mesh_hud_cardinal_north)
    FinderHaptics(hint)

    // The screen stays on while the user is looking: turning the view or walking restarts the clock.
    val lookBucket by remember { derivedStateOf { headingDeg?.div(ACTIVITY_TURN_DEG) } }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(lookBucket, targets.moving) {
        idle = false
        delay(IDLE_SCREEN_MS)
        idle = true
    }
    KeepScreenOn(!idle)
    var walking by remember { mutableStateOf(false) }
    LaunchedEffect(targets.moving) {
        if (targets.moving) delay(WALK_NOTICE_MS)
        walking = targets.moving
    }
    // A critical thermal state gives the camera back to the system; the overlay carries on as a compass.
    val showCamera = camera.isGranted && thermal != HudThermal.CRITICAL

    val tapeClearancePx = with(LocalDensity.current) { TapeClearance.roundToPx() }
    val textMeasurer = rememberTextMeasurer()
    // A shadow rather than a solid band keeps the tape's figures legible over a bright sky.
    val tapeStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = Color.White,
            shadow = Shadow(Color.Black.copy(alpha = TAPE_SHADOW_ALPHA), blurRadius = TAPE_SHADOW_BLUR),
        )
    val routeLabels =
        RouteLabels(
            skippedTemplate = stringResource(Res.string.mesh_hud_more),
            snrTemplate = stringResource(Res.string.mesh_hud_snr),
        )
    val trackLabels = TrackLabels(unknown = stringResource(Res.string.unknown), now = stringResource(Res.string.now))
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

        if (showCamera) {
            HudCameraPreview(
                onIntrinsics = { intrinsics = it },
                onAvailable = { cameraAvailable = it },
                onStill = { still = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The overlay a still carries: what is drawn over the camera, without the panels and bars around it.
        Box(
            Modifier.fillMaxSize().drawWithContent {
                overlayLayer.record { this@drawWithContent.drawContent() }
                drawLayer(overlayLayer)
            },
        ) {
            // Decoration only: every target it marks is a chip or radar dot with its own semantics.
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val state = poseState.value
                val pose = state.pose ?: return@Canvas
                drawHorizon(pose, focalPx)
                targets.fences.forEach { drawGeofence(it, pose, focalPx) }
                val sparkSeconds = sparkMillis.longValue / MILLIS_PER_SECOND
                track?.let { drawTrack(it, pose, focalPx, textMeasurer, tapeStyle, trackLabels, sparkSeconds) }
                route?.let {
                    drawRoute(
                        it,
                        pose,
                        focalPx,
                        TracerouteColors.OutgoingRoute,
                        TracerouteColors.ReturnRoute,
                        textMeasurer,
                        tapeStyle,
                        routeLabels,
                        sparkSeconds,
                    )
                }
                if (targets.showPrecision) {
                    targets.targets.forEach { drawPrecisionZone(it, pose, focalPx, state.headingAccuracyDeg) }
                }
                focused?.let {
                    drawReticle(Color(it.subject.color), locked = finderHint(it, pose) == FinderHint.OnTarget)
                }
                drawCompassTape(
                    trueHeadingDeg = (pose.azimuthDeg + targets.declinationDeg + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG,
                    declinationDeg = targets.declinationDeg,
                    targets = targets.targets,
                    focalPx = focalPx,
                    topPx = topBarBottomPx.toFloat(),
                    cardinals = cardinals,
                    textMeasurer = textMeasurer,
                    labelStyle = tapeStyle,
                )
            }
            HudChipLayer(
                targets = targets.targets,
                currentPose = { poseState.value.pose },
                focalPx = focalPx,
                topLimitPx = { topBarBottomPx + tapeClearancePx + noticesHeightPx },
                bottomLimitPx = { bottomPanelTopPx },
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                HudChip(
                    target = target,
                    currentPose = { poseState.value.pose },
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
        }

        val sensorStatus =
            HudSensorStatus(
                headingDeg = headingDeg,
                headingAccuracyDeg = headingAccuracyDeg,
                hasSensor = hasSensor,
                needsCalibration = needsCalibration,
                waitingForSensor = waitingForSensor,
                thermal = thermal,
            )
        HudTopNotices(
            status = sensorStatus,
            modifier =
            Modifier.align(Alignment.TopStart)
                .offset { IntOffset(0, topBarBottomPx + tapeClearancePx) }
                .onPlaced { noticesHeightPx = it.size.height },
        )
        HudTopBar(
            status = sensorStatus,
            targets = targets,
            onNavigateUp = onNavigateUp,
            onOpenFilters = if (targets.ready && !targets.scoped) ({ showFilters = true }) else null,
            onCapture = rememberHudCapture(still, overlayLayer).takeIf { showCamera && cameraAvailable },
            modifier =
            Modifier.align(Alignment.TopStart).onPlaced {
                topBarBottomPx = (it.positionInParent().y + it.size.height).toInt()
            },
        )
        HudBottomPanel(
            targets = targets,
            hint = hint,
            route = route,
            track = track,
            notices = HudNotices(cameraUnavailable = showCamera && !cameraAvailable, walking = walking),
            location = location,
            onOpenLocationSettings = openLocationSettings,
            radar = {
                NearbyRadar(
                    nearby = targets.directionUnknown,
                    ownAccuracyM = targets.ownAccuracyM,
                    units = targets.units,
                    northLabel = northLabel,
                    declinationDeg = targets.declinationDeg,
                    currentHeadingDeg = { poseState.value.pose?.azimuthDeg },
                    expanded = pointingDown,
                    onOpen = { subject ->
                        when (subject) {
                            is HudSubject.NodeSubject -> onOpenNode(subject.node.num)
                            is HudSubject.WaypointSubject -> infoWaypoint = subject.waypoint
                        }
                    },
                    route = route,
                    track = track,
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
        if (!coachSeen && camera.isGranted) HudCoach(onDismiss = viewModel::dismissCoach)
    }

    if (showFilters) {
        // The map's own filters and sheet, so the two views always agree on what they show.
        val mapViewModel: SharedMapViewModel = koinViewModel()
        val filterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
        MapFilterSheet(
            onDismissRequest = { showFilters = false },
            filterState = filterState,
            actions = remember(mapViewModel) { mapViewModel.mapFilterActions() },
        )
    }

    // Editing hands off to the map, which has the pickers a waypoint needs.
    infoWaypoint?.let { waypoint ->
        WaypointInfoDialog(
            waypoint = waypoint,
            displayUnits = targets.units,
            alertsEnabled = waypoint.id in alertOptIns,
            onToggleAlerts = { viewModel.setGeofenceAlertOptIn(waypoint.id, it) },
            onDismissRequest = { infoWaypoint = null },
            onEdit =
            onEditWaypoint?.let { edit ->
                {
                    infoWaypoint = null
                    edit(waypoint.id)
                }
            },
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

/** True when this turn hint is a step closer than [other] on the same side, so the user is closing in. */
private fun FinderHint.Turn.closerThan(other: FinderHint?): Boolean =
    other is FinderHint.Turn && other.right == right && degrees < other.degrees

/**
 * A tick for each hint step closer while turning toward the target, silent when sweeping away, and one firm pulse as
 * the finder locks on, so the user can stop without looking at the screen. Tilt steps stay silent: they change with
 * every movement of the wrist.
 */
@Suppress("UnnecessaryLaunchedEffect") // One haptic per hint change, not one per recomposition.
@Composable
private fun FinderHaptics(hint: FinderHint?) {
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf<FinderHint?>(null) }
    LaunchedEffect(hint) {
        val last = previous
        previous = hint
        when {
            hint == FinderHint.OnTarget -> haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

            hint is FinderHint.Turn && hint.closerThan(last) ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}
