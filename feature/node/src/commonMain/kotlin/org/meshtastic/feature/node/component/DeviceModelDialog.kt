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
package org.meshtastic.feature.node.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.heroImageFile
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.chirpy
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.device_model_load_failed
import org.meshtastic.core.resources.device_model_view_in_space
import org.meshtastic.core.resources.info
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.ViewInAr
import org.meshtastic.core.ui.util.LocalDeviceModelArViewer

/** The hardware's real-size 3D model, orbitable, over the whole window; the illustration when it cannot be shown. */
@Composable
fun DeviceModelDialog(
    deviceHardware: DeviceHardware,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mirrorScreen: Boolean = false,
) {
    val viewModel = koinViewModel<DeviceModelViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screenFrame =
        rememberMirroredScreen(
            viewModel,
            mirrorScreen && deviceModelDrawsScreen && (state as? DeviceModelState.Ready)?.screen != null,
        )
    val imageFile = deviceHardware.heroImageFile
    var chirpy by rememberSaveable { mutableStateOf(false) }
    // Once per open: a failed load clears the view model's key, so a later open retries.
    DisposableEffect(imageFile, chirpy) {
        when {
            chirpy -> viewModel.showChirpy()
            imageFile != null -> viewModel.load(imageFile)
            else -> viewModel.markFailed()
        }
        onDispose {}
    }
    val title = if (chirpy) stringResource(Res.string.chirpy) else deviceHardware.displayName

    val arViewer = LocalDeviceModelArViewer.current?.takeIf { it.isAvailable() }
    var inAr by rememberSaveable { mutableStateOf(false) }
    val arModel = (state as? DeviceModelState.Ready)?.takeIf { inAr && arViewer != null }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The orbit view stays composed under AR with its surface dropped, so coming back does not reload the model.
        Box(modifier = modifier.fillMaxSize()) {
            OrbitContent(
                deviceHardware = deviceHardware,
                title = title,
                state = state,
                screenFrame = screenFrame,
                onError = viewModel::markFailed,
                onDismiss = onDismiss,
                onOpenAr = if (arViewer != null) ({ inAr = true }) else null,
                onToggleChirpy = { chirpy = !chirpy },
                visible = arModel == null,
            )
            arModel?.let {
                arViewer?.Content(
                    glb = it.glb,
                    title = title,
                    notice = it.copyright,
                    onDismiss = { inAr = false },
                    screenMin = it.screen?.min,
                    screenMax = it.screen?.max,
                    screenFrame = screenFrame,
                )
            }
        }
    }
}

@Composable
private fun OrbitContent(
    deviceHardware: DeviceHardware,
    title: String,
    state: DeviceModelState,
    screenFrame: MirrorFrame?,
    onError: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAr: (() -> Unit)?,
    onToggleChirpy: () -> Unit,
    visible: Boolean,
) {
    val ready = state as? DeviceModelState.Ready
    val toggleChirpy by rememberUpdatedState(onToggleChirpy)
    var titleTaps by remember { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    // The easter egg: no ripple and no semantics, so it stays hidden.
                    modifier =
                    Modifier.padding(start = 8.dp).weight(1f).pointerInput(Unit) {
                        detectTapGestures { if (++titleTaps % CHIRPY_TAPS == 0) toggleChirpy() }
                    },
                )
                if (onOpenAr != null && ready != null) {
                    IconButton(onClick = onOpenAr) {
                        Icon(
                            MeshtasticIcons.ViewInAr,
                            contentDescription = stringResource(Res.string.device_model_view_in_space),
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val current = state) {
                    DeviceModelState.Loading -> CircularProgressIndicator()

                    DeviceModelState.Failed -> ModelFallback(deviceHardware)

                    is DeviceModelState.Ready ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            DeviceModelScene(
                                glb = current.glb,
                                bounds = current.bounds,
                                onError = onError,
                                modifier = Modifier.fillMaxSize(),
                                visible = visible,
                                screen = current.screen,
                                screenFrame = screenFrame,
                            )
                            current.copyright?.let {
                                ModelNotice(it, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                            }
                        }
                }
            }
        }
    }
}

/** Arms the connected device's screen mirror while [enabled]; null when it is off. */
@Composable
private fun rememberMirroredScreen(viewModel: DeviceModelViewModel, enabled: Boolean): MirrorFrame? {
    val frame by viewModel.screenFrame.collectAsStateWithLifecycle()
    DisposableEffect(enabled) {
        viewModel.setMirror(enabled)
        onDispose { viewModel.setMirror(false) }
    }
    return frame.takeIf { enabled }
}

/**
 * The model file's own copyright notice behind an info button, shut until tapped, as the maps carry their credit. The
 * text is the owner's, so it is never translated or cut.
 */
@Composable
fun ModelNotice(
    notice: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // The toggle is last, so the notice opens inboard of it from the trailing corner, in RTL too.
    Row(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(containerColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = expanded, modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        IconButton(onClick = { expanded = !expanded }) {
            Icon(MeshtasticIcons.Info, contentDescription = stringResource(Res.string.info), tint = contentColor)
        }
    }
}

/** Taps on the title that swap Chirpy in or out. */
private const val CHIRPY_TAPS = 7

@Composable
private fun ModelFallback(deviceHardware: DeviceHardware) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DeviceHardwareImage(deviceHardware, Modifier.size(240.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.device_model_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
