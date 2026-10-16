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
package org.meshtastic.app.node

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARCoachingOverlay
import io.github.sceneview.ar.ARMotionSensorHealth
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.ARSessionFailure
import io.github.sceneview.ar.PlacementReticleVisual
import io.github.sceneview.ar.ReticlePhase
import io.github.sceneview.ar.arcore.rememberDetectedPlanes
import io.github.sceneview.ar.placementHit
import io.github.sceneview.ar.probeARMotionSensors
import io.github.sceneview.ar.rememberArGuidanceState
import io.github.sceneview.ar.reticlePhaseFor
import io.github.sceneview.ar.scene.SceneUnderstanding
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.device_model_ar_camera_unavailable
import org.meshtastic.core.resources.device_model_ar_failed
import org.meshtastic.core.resources.device_model_ar_restart_phone
import org.meshtastic.core.resources.device_model_capture
import org.meshtastic.core.resources.device_model_capture_failed
import org.meshtastic.core.resources.device_model_capture_saved
import org.meshtastic.core.resources.device_model_rest_face_down
import org.meshtastic.core.resources.device_model_rest_face_up
import org.meshtastic.core.resources.device_model_rest_left_side
import org.meshtastic.core.resources.device_model_rest_right_side
import org.meshtastic.core.resources.device_model_rest_upright
import org.meshtastic.core.resources.info
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PhotoCamera
import org.meshtastic.core.ui.util.DeviceModelArViewer
import org.meshtastic.feature.node.component.DeviceScreenNode
import org.meshtastic.feature.node.model.GlbBounds
import org.meshtastic.feature.node.model.RestPose
import java.nio.ByteBuffer

private const val SCRIM_ALPHA = 0.4f

// Darker than the chrome's scrim: a sentence over a live camera image needs the contrast.
private const val NOTICE_ALPHA = 0.7f
private const val YAW_DEGREES_PER_PX = 0.4f

val deviceModelArViewer: DeviceModelArViewer? = GoogleDeviceModelArViewer

/**
 * The google flavor's AR viewer. A phone ARCore is still checking, or that only lacks the ARCore app, counts as
 * available: the viewer's own overlay offers the install.
 */
private object GoogleDeviceModelArViewer : DeviceModelArViewer {
    @Composable
    override fun isAvailable(): Boolean {
        val context = LocalContext.current
        return remember(context) { !ArCoreApk.getInstance().checkAvailability(context).isUnsupported }
    }

    @Composable
    override fun Content(
        glb: ByteArray,
        title: String,
        notice: String?,
        onDismiss: () -> Unit,
        screenMin: FloatArray?,
        screenMax: FloatArray?,
        screenFrame: MirrorFrame?,
    ) = DeviceModelAr(glb, title, notice, onDismiss, ArScreen(screenMin, screenMax, screenFrame))
}

/**
 * Tap a surface to stand the device on it at real size: the models are in meters and ARCore works in meters, so nothing
 * is scaled. A later tap moves it rather than adding another, and a twist turns it.
 */
@Composable
private fun DeviceModelAr(glb: ByteArray, title: String, notice: String?, onDismiss: () -> Unit, screen: ArScreen) {
    BackHandler(onBack = onDismiss)
    LockOrientationWhileShown()
    // Asked here rather than left to the AR view, whose own request never appears from inside a dialog.
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestCamera =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) cameraGranted = true else onDismiss()
        }
    var asked by rememberSaveable { mutableStateOf(false) }
    SideEffect {
        if (!cameraGranted && !asked) {
            asked = true
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraGranted) DeviceModelArScene(glb, screen, onCapture = rememberArCapture(title))
        ArHeader(title, notice, onDismiss)
    }
}

/**
 * ARSceneView rather than SceneView's PlacementScene, which as of 4.56.0 does not pass depth occlusion through. This
 * keeps its tap-to-place, reticle, coaching and single-shadow-receiver behavior, built from the same public pieces.
 */
@Composable
@Suppress("LongMethod")
private fun DeviceModelArScene(glb: ByteArray, screen: ArScreen, onCapture: (() -> Unit)?) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val haptic = LocalHapticFeedback.current
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    // Saved across a rotation; the anchor cannot be, since the activity's new ARCore session starts unplaced.
    var yawDegrees by rememberSaveable { mutableFloatStateOf(0f) }
    var pose by rememberSaveable { mutableStateOf(RestPose.Upright) }
    val bounds = remember(glb) { GlbBounds.parse(glb) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var reticlePhase by remember { mutableStateOf(ReticlePhase.SEARCHING) }
    var cameraReady by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var surfaceFound by remember { mutableStateOf(false) }
    var trackingFailure by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var everTracked by remember { mutableStateOf(false) }
    var sessionFailure by remember { mutableStateOf<ARSessionFailure?>(null) }
    val placed = anchor != null
    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP,
            sessionConfiguration = { arSession, config ->
                // Real objects in front of the model hide it; phones without depth just skip it.
                if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
            },
            // The grid shows surfaces until the device stands on one, then gets out of the way.
            sceneUnderstanding = SceneUnderstanding(occlusion = true, planeVisualization = !placed),
            onSessionUpdated = { updated, frame ->
                latestFrame = frame
                if (session !== updated) session = updated
                cameraReady = true
                isTracking = frame.camera.trackingState == TrackingState.TRACKING
                if (isTracking) everTracked = true
                if (!surfaceFound) {
                    surfaceFound =
                        updated.getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }
                }
            },
            onTrackingFailureChanged = { trackingFailure = it },
            // A refused resume (camera held by another app) leaves the session paused; the next resume tries again.
            onSessionResumed = { sessionFailure = null },
            onSessionFailure = { sessionFailure = it },
            onGestureListener =
            rememberOnGestureListener(
                onSingleTapConfirmed = { event, node ->
                    // A tap on the model is a gesture on it, not a new placement.
                    val frame = latestFrame
                    if (node == null && frame != null) {
                        placementHit(frame, event.x, event.y, instantPlacement = true)?.let { hit ->
                            anchor?.detach()
                            anchor = hit.createAnchor()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                // A one-finger drag anywhere turns it: a twist on the node itself needs both fingers on a few
                // centimeters of device. GestureDetector's distance is previous minus current, so negate it.
                onScroll = { _, _, _, distance -> yawDegrees -= distance.x * YAW_DEGREES_PER_PX },
            ),
        ) {
            if (!placed && isTracking && viewport != IntSize.Zero) {
                PlacementReticle(
                    xPx = viewport.width / 2f,
                    yPx = viewport.height / 2f,
                    onHitResultChanged = { hit -> reticlePhase = reticlePhaseFor(hasHit = hit != null) },
                ) {
                    PlacementReticleVisual(materialLoader = materialLoader, phase = reticlePhase)
                }
            }
            // The plane grid carries its own shadow receiver while it shows; one per plane once it is hidden.
            if (placed) {
                val planes by rememberDetectedPlanes(session)
                planes.forEach { plane -> key(plane) { ShadowReceiverPlane(plane = plane) } }
            }
            anchor?.let { placedAnchor ->
                key(placedAnchor) {
                    val instance = remember(modelLoader) { modelLoader.createModelInstance(ByteBuffer.wrap(glb)) }
                    DisposableEffect(instance) { onDispose { modelLoader.destroyModel(instance.model) } }
                    AnchorNode(anchor = placedAnchor) {
                        // Moving is a new tap and scaling would break real size, so the drag's yaw and the rest pose
                        // are the only edits. The yaw wraps the pose so it always turns about the surface's up.
                        val offset = bounds?.let(pose::offset) ?: floatArrayOf(0f, 0f, 0f)
                        Node(rotation = Rotation(y = yawDegrees)) {
                            ModelNode(
                                modelInstance = instance,
                                position = Position(offset[0], offset[1], offset[2]),
                                rotation = Rotation(x = pose.pitchDegrees, z = pose.rollDegrees),
                            ) {
                                val glassMin = screen.glassMin
                                val glassMax = screen.glassMax
                                val frame = screen.frame
                                if (glassMin != null && glassMax != null && frame != null) {
                                    DeviceScreenNode(glassMin, glassMax, frame)
                                }
                            }
                        }
                    }
                }
            }
        }
        val guidance =
            rememberArGuidanceState(
                cameraReady = cameraReady,
                isTracking = isTracking,
                surfaceFound = surfaceFound,
                trackingFailureReason = trackingFailure,
            )
        val stalled = arStartupStalled(cameraReady, everTracked, trackingFailure)
        val notice = arNotice(sessionFailure, stalled, rememberMotionSensorHealth(stalled))
        if (notice == ArNotice.NONE) ARCoachingOverlay(guidance) else ArNoticeCard(notice)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (placed && bounds != null) RestPoseChips(selected = pose, onSelect = { pose = it })
            onCapture?.let { ShutterButton(it) }
        }
    }
}

/**
 * A gyroscope that delivers nothing leaves ARCore paused with no reason and no failure over a live camera image, and
 * only a restart fixes it. Listens once the session has shown frames for ten seconds without tracking, in the
 * foreground only: a paused session is not a stalled one, and a backgrounded app gets no sensor events.
 */
@Composable
private fun rememberMotionSensorHealth(stalled: Boolean): ARMotionSensorHealth? {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var probes by remember { mutableIntStateOf(0) }
    var health by remember { mutableStateOf<ARMotionSensorHealth?>(null) }
    LaunchedEffect(stalled, resumed, probes) {
        if (!resumed || !arMotionProbeDue(stalled, probes, health)) return@LaunchedEffect
        delay(AR_NEVER_TRACKED_MS)
        val sample = probeARMotionSensors(context)
        health = sample?.health
        // A phone with no gyroscope is final too: nothing left to measure.
        probes = if (sample == null) AR_MOTION_PROBES_MAX else probes + 1
    }
    return health.takeIf { stalled }
}

/** Takes the coaching card's place over the camera while the session cannot start. */
@Composable
private fun BoxScope.ArNoticeCard(notice: ArNotice) {
    val text =
        when (notice) {
            ArNotice.CAMERA_UNAVAILABLE -> Res.string.device_model_ar_camera_unavailable
            ArNotice.SESSION_FAILED -> Res.string.device_model_ar_failed
            ArNotice.RESTART_PHONE -> Res.string.device_model_ar_restart_phone
            ArNotice.NONE -> return
        }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier =
        Modifier.align(Alignment.Center)
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = NOTICE_ALPHA))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

/** Which face of the device lies on the surface. */
@Composable
private fun RestPoseChips(selected: RestPose, onSelect: (RestPose) -> Unit, modifier: Modifier = Modifier) {
    val colors =
        FilterChipDefaults.filterChipColors(
            labelColor = Color.White,
            selectedContainerColor = Color.White.copy(alpha = SCRIM_ALPHA),
            selectedLabelColor = Color.White,
        )
    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RestPose.entries.forEach { pose ->
            FilterChip(
                selected = pose == selected,
                onClick = { onSelect(pose) },
                label = { Text(stringResource(pose.label)) },
                colors = colors,
            )
        }
    }
}

private val RestPose.label: StringResource
    get() =
        when (this) {
            RestPose.Upright -> Res.string.device_model_rest_upright
            RestPose.FaceUp -> Res.string.device_model_rest_face_up
            RestPose.FaceDown -> Res.string.device_model_rest_face_down
            RestPose.LeftSide -> Res.string.device_model_rest_left_side
            RestPose.RightSide -> Res.string.device_model_rest_right_side
        }

/** Title bar over the camera; the model's notice opens under it, clear of the controls at the bottom. */
@Composable
private fun ArHeader(title: String, notice: String?, onDismiss: () -> Unit) {
    var noticeShown by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = SCRIM_ALPHA)).safeDrawingPadding().padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close), tint = Color.White)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            if (notice != null) {
                IconButton(onClick = { noticeShown = !noticeShown }) {
                    Icon(MeshtasticIcons.Info, contentDescription = stringResource(Res.string.info), tint = Color.White)
                }
            }
        }
        AnimatedVisibility(visible = noticeShown && notice != null) {
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** A camera-app shutter: the one control you reach for with a thumb, so it sits bottom center. */
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(MeshtasticIcons.PhotoCamera, contentDescription = stringResource(Res.string.device_model_capture))
    }
}

/** Saves the AR view to Pictures and says so; Android 10 and later only, where that needs no storage permission. */
@Composable
private fun rememberArCapture(title: String): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dispatchers = koinInject<CoroutineDispatchers>()
    val haptic = LocalHapticFeedback.current
    val saved = stringResource(Res.string.device_model_capture_saved)
    val failed = stringResource(Res.string.device_model_capture_failed)
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val uri = captureArScene(view.rootView, title, dispatchers.io)
            Toast.makeText(view.context, if (uri != null) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Holds the activity at its current orientation while AR is up. A rotation recreates the activity, and the new ARCore
 * session starts without the planes it found or the device placed on one; turning the phone here moves the camera.
 */
@Composable
private fun LockOrientationWhileShown() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { if (activity != null && previous != null) activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The display glass in model coordinates and the live frame to draw on it; all null when there is nothing to show. */
private class ArScreen(val glassMin: FloatArray?, val glassMax: FloatArray?, val frame: MirrorFrame?)
