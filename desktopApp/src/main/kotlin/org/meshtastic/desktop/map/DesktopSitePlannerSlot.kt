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
package org.meshtastic.desktop.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.coverage.CoverageGrid
import org.meshtastic.feature.coverage.LocalCoverage
import org.meshtastic.feature.coverage.MapterhornElevation
import org.meshtastic.feature.coverage.Site
import org.meshtastic.feature.coverage.sweepGrid
import org.meshtastic.feature.coverage.toGeoJson
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.SitePlannerParams
import org.meshtastic.feature.map.component.SitePlannerSheet
import org.meshtastic.feature.map.component.toSitePlannerParams
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.maplibre.SitePlannerSession
import org.meshtastic.feature.map.maplibre.terrain.terrainStorageDirectory
import org.meshtastic.feature.map.terrain.TerrainTileStore
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Site Planner on the desktop — computed **in this app**, not in a browser.
 *
 * Previously this opened site.meshtastic.org in the system browser and asked the user to export a `.geojson` and
 * re-import it by hand: desktop has no embedded browser, and putting JCEF back into the jlink'd runtime measured at
 * roughly three and a half times the size of the whole application.
 *
 * Now `feature:coverage` runs ITU-R P.1812 (`org.meshtastic:kp1812`) against the same Mapterhorn elevation the map
 * already uses for hillshade and contours. No network call to the planner, no WebView, no export/re-import round trip.
 *
 * Note this is a different propagation model from the hosted planner's SPLAT!/ITM, so predictions will not match it
 * pixel for pixel.
 */
@Composable
fun DesktopSitePlannerSlot(session: SitePlannerSession) {
    val sharedViewModel: SharedMapViewModel = koinViewModel()
    val layersManager: MapLayersManager = koinInject()

    val ourNode by sharedViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val channelSet by sharedViewModel.channelSet.collectAsStateWithLifecycle()
    val nodes by sharedViewModel.nodes.collectAsStateWithLifecycle()

    // A deep link names the node to plan for; a toolbar launch plans for whatever we are connected to.
    val subject = session.nodeNum?.let { num -> nodes.firstOrNull { it.num == num } } ?: ourNode

    var params by remember(subject) { mutableStateOf(subject.toSitePlannerParams(channelSet)) }
    var running by remember { mutableStateOf<SitePlannerParams?>(null) }
    var result by remember { mutableStateOf<CoverageGrid?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Its own directory beside the map's offline regions, not inside one: coverage downloads should
    // not silently inflate the size and tile count a downloaded region reports.
    val terrainStore = remember { TerrainTileStore(FileSystem.SYSTEM, terrainStorageDirectory().resolve("coverage")) }

    val current = running
    val coverage = result

    when {
        coverage != null ->
            CoverageResultDialog(coverage) {
                result = null
                session.onDismiss()
            }

        current != null -> {
            ComputingDialog(current.name) {
                running = null
                session.onDismiss()
            }
            LaunchedEffect(current) {
                runCatching {
                    withContext(Dispatchers.Default) {
                        // A fresh source per estimate is free: decoded terrain lives in a shared
                        // cache, on disk under the store, and the HTTP client is shared too.
                        MapterhornElevation(store = terrainStore).use { source ->
                            source.prefetch(current.toSite())
                            LocalCoverage(source).sweepGrid(current.toSite(), resolution = GRID)
                        }
                    }
                }
                    .onSuccess { swept ->
                        // Persist and draw it on the map, the same path the F-Droid flavour uses for
                        // the WebView's GeoJSON — so the coverage survives the dialog closing and
                        // shows up in the layers list like any other import.
                        layersManager.addGeoJsonLayer(current.name, swept.toGeoJson())
                        session.moveTo(Position(longitude = current.longitude, latitude = current.latitude))
                        result = swept
                    }
                    .onFailure { failure = it.message ?: it::class.simpleName }
                running = null
            }
        }

        else ->
            SitePlannerSheet(
                initial = params,
                onSubmit = { submitted ->
                    params = submitted
                    running = submitted
                },
                onDismiss = session.onDismiss,
                note =
                failure?.let { "Coverage failed: $it" }
                    ?: "Computed on this device with ITU-R P.1812 — no browser, works offline once terrain is cached.",
                onUseNodeLocation =
                subject
                    ?.takeIf { it.validPosition != null }
                    ?.let { node ->
                        { params = params.copy(latitude = node.latitude, longitude = node.longitude) }
                    },
                onUseMapCenter = {
                    session.mapCenter().let { params = params.copy(latitude = it.latitude, longitude = it.longitude) }
                },
            )
    }
}

/** The planner's flat params, as the coverage module's site. */
private fun SitePlannerParams.toSite(): Site = Site(
    name = name,
    latitude = latitude,
    longitude = longitude,
    frequencyMhz = txFreqMhz,
    // The planner carries watts; P.1812 wants dBm.
    txPowerDbm = 10.0 * log10(txPowerWatts * MILLIWATTS_PER_WATT),
    rxSensitivityDbm = rxSensitivityDbm,
    txHeightM = txHeightMeters,
    rxHeightM = rxHeightMeters,
    txGainDbi = txGainDbi,
    radiusKm = maxRangeKm,
)

@Composable
private fun ComputingDialog(name: String, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.size(300.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator()
                Text("Estimating coverage for $name", style = MaterialTheme.typography.bodyLarge)
                Text("ITU-R P.1812 · on this device", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CoverageResultDialog(coverage: CoverageGrid, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(coverage.site.name, style = MaterialTheme.typography.titleMedium)
                Text("Added to the map as a coverage layer", style = MaterialTheme.typography.bodySmall)
                Text(
                    "ITU-R P.1812 · ${coverage.site.frequencyMhz.roundToInt()} MHz · " +
                        "${coverage.site.txPowerDbm.roundToInt()} dBm",
                    style = MaterialTheme.typography.bodySmall,
                )
                CoveragePlot(coverage, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${coverage.width}×${coverage.height} grid", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${(coverage.reachableFraction * PERCENT).roundToInt()}% reachable",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("max ${coverage.maxRangeKm.roundToInt()} km", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

/** Top-down preview of the grid, coloured by predicted signal. */
@Composable
private fun CoveragePlot(coverage: CoverageGrid, modifier: Modifier = Modifier) {
    val sensitivity = coverage.site.rxSensitivityDbm
    val strongest = coverage.dbm.filter { !it.isNaN() }.maxOrNull() ?: sensitivity

    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            drawRect(Color(0xFF14151C))
            val cw = size.width / coverage.width
            val ch = size.height / coverage.height
            for (y in 0 until coverage.height) {
                for (x in 0 until coverage.width) {
                    val v = coverage.at(x, y)
                    if (v.isNaN() || v < sensitivity) continue
                    drawRect(
                        signalColor(v, sensitivity, strongest),
                        topLeft = Offset(x * cw, y * ch),
                        size = androidx.compose.ui.geometry.Size(cw + 1, ch + 1),
                    )
                }
            }
        }
    }
}

private fun signalColor(dbm: Double, sensitivity: Double, strongest: Double): Color {
    if (dbm < sensitivity) return Color(0xFF22232C)
    val t = ((dbm - sensitivity) / (strongest - sensitivity)).coerceIn(0.0, 1.0).toFloat()
    val r = if (t < HALF) 1f else (1f - (t - HALF) * 2f).coerceIn(0f, 1f)
    val g = if (t < HALF) (t * 2f).coerceIn(0f, 1f) else 1f
    return Color(r, g, GREEN_FLOOR)
}

private const val GRID = 256
private const val MILLIWATTS_PER_WATT = 1000.0
private const val PERCENT = 100
private const val DEG_TO_RAD = 0.017453292519943295
private const val DOT_RADIUS = 2.5f
private const val TX_RADIUS = 5f
private const val HALF = 0.5f
private const val GREEN_FLOOR = 0.24f
