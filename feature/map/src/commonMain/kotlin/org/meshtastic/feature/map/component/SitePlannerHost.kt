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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.site_planner_estimating
import org.meshtastic.core.resources.site_planner_failed

/**
 * The Site Planner flow every map host shares: the [SitePlannerSheet] form, then [estimate] behind a progress dialog,
 * then [onImport] with the estimate's GeoJSON and the transmitter position so the host can add a map layer and move
 * the camera to it.
 *
 * Canceling the progress dialog stops the estimate and returns to the form with the same values. A failed estimate
 * returns there too, with an error note. The location shortcuts re-seed the coordinates from the device
 * ([onRequestCurrentLocation]), the node ([onUseNodeLocation]), or the map ([onUseMapCenter]) when a host offers them.
 */
@Composable
fun SitePlannerHost(
    initialParams: SitePlannerParams,
    estimate: suspend (SitePlannerParams) -> String,
    onDismiss: () -> Unit,
    onImport: (name: String, geoJson: String, latitude: Double, longitude: Double) -> Unit,
    onRequestCurrentLocation: (suspend () -> Pair<Double, Double>?)? = null,
    onUseNodeLocation: (() -> Pair<Double, Double>)? = null,
    onUseMapCenter: (() -> Pair<Double, Double>)? = null,
) {
    var params by remember(initialParams) { mutableStateOf(initialParams) }
    var running by remember { mutableStateOf<SitePlannerParams?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentEstimate by rememberUpdatedState(estimate)
    val currentOnImport by rememberUpdatedState(onImport)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val current = running
    if (current == null) {
        SitePlannerSheet(
            initial = params,
            onSubmit = { submitted ->
                params = submitted
                failed = false
                running = submitted
            },
            onDismiss = onDismiss,
            onUseCurrentLocation =
            onRequestCurrentLocation?.let { fetch ->
                {
                    scope.launch {
                        fetch()?.let { (lat, lon) -> params = params.copy(latitude = lat, longitude = lon) }
                    }
                }
            },
            onUseNodeLocation =
            onUseNodeLocation?.let { node ->
                {
                    val (lat, lon) = node()
                    params = params.copy(latitude = lat, longitude = lon)
                }
            },
            onUseMapCenter =
            onUseMapCenter?.let { center ->
                {
                    val (lat, lon) = center()
                    params = params.copy(latitude = lat, longitude = lon)
                }
            },
            note = if (failed) stringResource(Res.string.site_planner_failed) else null,
        )
    } else {
        EstimatingDialog(onCancel = { running = null })
        LaunchedEffect(current) {
            val geoJson =
                try {
                    currentEstimate(current)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.withTag("SitePlanner").e(e) { "Coverage estimate failed" }
                    null
                }
            if (geoJson == null) {
                failed = true
                running = null
            } else {
                currentOnImport(current.name, geoJson, current.latitude, current.longitude)
                currentOnDismiss()
            }
        }
    }
}

@Composable
private fun EstimatingDialog(onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.size(280.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                CircularWavyProgressIndicator()
                Text(stringResource(Res.string.site_planner_estimating))
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            }
        }
    }
}
