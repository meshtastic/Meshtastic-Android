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
package org.meshtastic.feature.xr

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.device_model_in_space_hint
import org.meshtastic.core.resources.device_model_load_failed
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.DeviceModelArViewer
import org.meshtastic.feature.node.component.BananaToggle
import org.meshtastic.feature.node.component.toBitmap
import org.meshtastic.feature.node.model.BananaForScale
import org.meshtastic.feature.node.model.GlbBounds
import kotlin.math.roundToInt

/** On an XR headset, the device model stands in the room instead of in [fallback]'s phone AR view. */
fun spatialDeviceModelViewer(fallback: DeviceModelArViewer?): DeviceModelArViewer = SpatialOrFallbackViewer(fallback)

private class SpatialOrFallbackViewer(private val fallback: DeviceModelArViewer?) : DeviceModelArViewer {
    @Composable override fun isAvailable(): Boolean = LocalSession.current != null || fallback?.isAvailable() == true

    @Composable
    override fun Content(
        glb: ByteArray,
        title: String,
        notice: String?,
        onDismiss: () -> Unit,
        screenMin: FloatArray?,
        screenMax: FloatArray?,
        screenFrame: MirrorFrame?,
        banana: ByteArray?,
        bananaShown: Boolean,
        onBananaShownChange: (Boolean) -> Unit,
    ) {
        val session = LocalSession.current
        if (session == null) {
            fallback?.Content(
                glb,
                title,
                notice,
                onDismiss,
                screenMin,
                screenMax,
                screenFrame,
                banana,
                bananaShown,
                onBananaShownChange,
            )
        } else {
            DeviceModelInSpace(
                session,
                glb,
                title,
                notice,
                onDismiss,
                screen = if (screenMin != null && screenMax != null) GlbBounds(screenMin, screenMax) else null,
                screenFrame = screenFrame,
                banana = banana,
                bananaShown = bananaShown,
                onBananaShownChange = onBananaShownChange,
            )
        }
    }
}

/**
 * The model at real size beside the app panel: the flasher's models are in meters, as is the activity space, so nothing
 * is scaled beyond undoing the main panel's own scale. 3D content shows only in Full Space, so opening it asks for it.
 */
@Composable
@Suppress("LongParameterList")
private fun DeviceModelInSpace(
    session: Session,
    glb: ByteArray,
    title: String,
    notice: String?,
    onDismiss: () -> Unit,
    screen: GlbBounds?,
    screenFrame: MirrorFrame?,
    banana: ByteArray?,
    bananaShown: Boolean,
    onBananaShownChange: (Boolean) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(session) {
        session.scene.requestFullSpace()
        onDispose {}
    }
    var failed by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<GltfModelEntity?>(null) }
    if (LocalSpatialCapabilities.current.isContent3dEnabled) {
        LaunchedEffect(session, glb) {
            failed = false
            val model =
                loadModel(session, glb, MODEL_NAME)
                    ?: run {
                        failed = true
                        return@LaunchedEffect
                    }
            val panel = session.scene.mainPanelEntity
            val size = panel.size
            // The system scales the main panel with its distance; undo that so the model is its real size.
            val scale = panel.getScale(Space.ACTIVITY)
            // In the panel's plane past its right edge, with room between for the banana on the device's left.
            val beside = Vector3(size.width / 2f + CLEARANCE_METERS / scale, -size.height * DROP_FRACTION, 0f)
            val entity = GltfModelEntity.create(session, model, Pose(beside), panel)
            entity.setScale(1f / scale)
            entity.contentDescription = title
            entity.addComponent(MovableComponent.createSystemMovable(session))
            device = entity
            try {
                awaitCancellation()
            } finally {
                device = null
                release(entity, model)
            }
        }
        DeviceScreenPanel(session, device, screen, screenFrame)
        DeviceBanana(session, device, glb, banana?.takeIf { bananaShown })
    }
    InSpaceChrome(
        title = title,
        notice = notice,
        failed = failed,
        onDismiss = onDismiss,
        bananaShown = bananaShown,
        onBananaShownChange = onBananaShownChange,
    )
}

/** The live screen on the display glass, a child of [device] so it moves and turns with it. */
@Composable
private fun DeviceScreenPanel(session: Session, device: GltfModelEntity?, glass: GlbBounds?, frame: MirrorFrame?) {
    val context = LocalContext.current
    // A panel keeps its view parented, so each panel gets its own.
    val view = remember(session, device, glass) { ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY } }
    // Nearest neighbor keeps the display's pixels square instead of smearing them; decoded once per frame.
    val screen =
        remember(frame, view) {
            frame?.toBitmap()?.let { bitmap -> BitmapDrawable(view.resources, bitmap).apply { setFilterBitmap(false) } }
        }
    SideEffect { screen?.let(view::setImageDrawable) }
    LaunchedEffect(session, device, glass) {
        val parent = device ?: return@LaunchedEffect
        val bounds = glass ?: return@LaunchedEffect
        val width = bounds.max[0] - bounds.min[0]
        val height = bounds.max[1] - bounds.min[1]
        if (width <= 0f || height <= 0f) return@LaunchedEffect
        val at = Vector3(bounds.center[0], bounds.center[1], bounds.max[2] + SCREEN_LIFT_METERS)
        // Made in pixels, so the glass gets a sharp texture, then shrunk to the glass's real size.
        val pixels = IntSize2d(SCREEN_PIXELS_WIDE, (SCREEN_PIXELS_WIDE * height / width).roundToInt())
        val panel = PanelEntity.create(session, view, pixels, SCREEN_NAME, Pose(at), parent)
        panel.setScale(width / panel.size.width)
        try {
            awaitCancellation()
        } finally {
            // Disposing the device disposes its children, so the panel may already be gone.
            if (!panel.isDisposed) panel.parent = null
        }
    }
}

/** The banana on the device's left, a child of [device] so toggling it leaves the device loaded. */
@Composable
private fun DeviceBanana(session: Session, device: GltfModelEntity?, glb: ByteArray, banana: ByteArray?) {
    LaunchedEffect(session, device, banana) {
        val parent = device ?: return@LaunchedEffect
        val bananaGlb = banana ?: return@LaunchedEffect
        val at =
            GlbBounds.parse(glb)?.let { d -> GlbBounds.parse(bananaGlb)?.let { BananaForScale.beside(d, it) } }
                ?: return@LaunchedEffect
        val model = loadModel(session, bananaGlb, BANANA_NAME) ?: return@LaunchedEffect
        val entity = GltfModelEntity.create(session, model, Pose(Vector3(at[0], at[1], at[2])), parent)
        try {
            awaitCancellation()
        } finally {
            release(entity, model)
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun InSpaceChrome(
    title: String,
    notice: String?,
    failed: Boolean,
    onDismiss: () -> Unit,
    bananaShown: Boolean,
    onBananaShownChange: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(
                        if (failed) Res.string.device_model_load_failed else Res.string.device_model_in_space_hint,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BananaToggle(checked = bananaShown, onCheckedChange = onBananaShownChange)
                    FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(XrTargetSize)) {
                        Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close))
                    }
                }
                notice?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The entity goes before the model it borrows: the native side aborts if the model is released while it is held. */
@Suppress("DEPRECATION")
private fun release(entity: GltfModelEntity, model: GltfModel) {
    entity.dispose()
    model.close()
}

private suspend fun loadModel(session: Session, glb: ByteArray, name: String): GltfModel? = try {
    GltfModel.create(session, glb, name)
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Logger.w(e) { "Model $name failed to load in space" }
    null
}

private const val MODEL_NAME = "device"
private const val BANANA_NAME = "banana"
private const val SCREEN_NAME = "screen"
private const val SCREEN_PIXELS_WIDE = 512
private const val SCREEN_LIFT_METERS = 0.0005f
private const val CLEARANCE_METERS = 0.25f
private const val DROP_FRACTION = 0.25f
