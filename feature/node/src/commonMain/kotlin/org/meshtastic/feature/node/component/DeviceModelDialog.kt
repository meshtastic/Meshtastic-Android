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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.heroImageFile
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.device_model_load_failed
import org.meshtastic.core.resources.device_model_view_in_space
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.ViewInAr
import org.meshtastic.core.ui.util.LocalDeviceModelArViewer

/** The hardware's real-size 3D model, orbitable, over the whole window; the illustration when it cannot be shown. */
@Composable
fun DeviceModelDialog(deviceHardware: DeviceHardware, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<DeviceModelViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imageFile = deviceHardware.heroImageFile
    // Both calls are idempotent, so running on every composition is safe.
    SideEffect { if (imageFile != null) viewModel.load(imageFile) else viewModel.markFailed() }

    val arViewer = LocalDeviceModelArViewer.current?.takeIf { it.isAvailable() }
    var inAr by rememberSaveable { mutableStateOf(false) }
    val ready = state as? DeviceModelState.Ready

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (inAr && arViewer != null && ready != null) {
            arViewer.Content(ready.glb, deviceHardware.displayName, ready.copyright) { inAr = false }
            return@Dialog
        }
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close))
                    }
                    Text(
                        text = deviceHardware.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    if (arViewer != null && ready != null) {
                        IconButton(onClick = { inAr = true }) {
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
                            Column(modifier = Modifier.fillMaxSize()) {
                                DeviceModelScene(
                                    glb = current.glb,
                                    bounds = current.bounds,
                                    onError = viewModel::markFailed,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                                current.copyright?.let { ModelNotice(it, Modifier.padding(16.dp)) }
                            }
                    }
                }
            }
        }
    }
}

/** The model file's own copyright notice, verbatim: it is the owner's text, so it is never translated or cut. */
@Composable
internal fun ModelNotice(notice: String, modifier: Modifier = Modifier) {
    Text(
        text = notice,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

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
