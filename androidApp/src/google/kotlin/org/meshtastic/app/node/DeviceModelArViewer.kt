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
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARCoachingOverlay
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.PlacementReticleVisual
import io.github.sceneview.ar.ReticlePhase
import io.github.sceneview.ar.arcore.rememberDetectedPlanes
import io.github.sceneview.ar.placementHit
import io.github.sceneview.ar.rememberArGuidanceState
import io.github.sceneview.ar.reticlePhaseFor
import io.github.sceneview.ar.scene.SceneUnderstanding
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.DeviceModelArViewer
import org.meshtastic.feature.node.component.ModelNotice
import java.nio.ByteBuffer

private const val SCRIM_ALPHA = 0.4f

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
    override fun Content(glb: ByteArray, title: String, notice: String?, onDismiss: () -> Unit) =
        DeviceModelAr(glb, title, notice, onDismiss)
}

/**
 * Tap a surface to stand the device on it at real size: the models are in meters and ARCore works in meters, so nothing
 * is scaled. A later tap moves it rather than adding another, and a twist turns it.
 */
@Composable
private fun DeviceModelAr(glb: ByteArray, title: String, notice: String?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
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
        if (cameraGranted) DeviceModelArScene(glb)
        ArHeader(title, onDismiss)
        notice?.let {
            ModelNotice(
                notice = it,
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
                contentColor = Color.White,
                containerColor = Color.Black.copy(alpha = SCRIM_ALPHA),
            )
        }
    }
}

/**
 * ARSceneView rather than SceneView's PlacementScene, which as of 4.56.0 does not pass depth occlusion through. This
 * keeps its tap-to-place, reticle, coaching and single-shadow-receiver behavior, built from the same public pieces.
 */
@Composable
@Suppress("LongMethod")
private fun DeviceModelArScene(glb: ByteArray) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val haptic = LocalHapticFeedback.current
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var reticlePhase by remember { mutableStateOf(ReticlePhase.SEARCHING) }
    var cameraReady by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var surfaceFound by remember { mutableStateOf(false) }
    var trackingFailure by remember { mutableStateOf<TrackingFailureReason?>(null) }
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
                if (!surfaceFound) {
                    surfaceFound =
                        updated.getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }
                }
            },
            onTrackingFailureChanged = { trackingFailure = it },
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
                        ModelNode(
                            modelInstance = instance,
                            isEditable = true,
                            apply = {
                                // Turn only: moving is a new tap, and scaling would break real size.
                                isPositionEditable = false
                                isScaleEditable = false
                                isRotationEditable = true
                            },
                        )
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
        ARCoachingOverlay(guidance)
    }
}

@Composable
private fun ArHeader(title: String, onDismiss: () -> Unit) {
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
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
