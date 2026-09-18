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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.MapRuntimeOptions
import org.maplibre.compose.map.MapSnapshotRequest
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.maplibre.layers.NodeLayers
import org.meshtastic.feature.map.maplibre.style.Basemaps
import java.nio.file.Files

/**
 * Captures the real mesh map — the app's default basemap with its own [NodeLayers] chips over [SampleMesh] — through
 * maplibre-compose's snapshotter, with no window and no display. Sized to the map area between the app bar and the
 * bottom navigation at the screen density, so the screen composable can place it without rescaling.
 */
internal object MapSnapshot {
    private const val ZOOM = 11.5
    private const val CAPTURE_TIMEOUT_MS = 180_000L

    fun capture(widthDp: Int, heightDp: Int, density: Float): ImageBitmap = runBlocking {
        val cacheDir = Files.createTempDirectory("marketing-maplibre")
        val runtime = createMapRuntime(MapRuntimeOptions(cacheFile = Path(cacheDir.resolve("cache.db").toString())))
        try {
            val baseStyle = BaseStyle.Uri(Basemaps.Liberty.styleUri)
            // NodeLayers reads LocalMapState (for cluster clicks) and rasterizes chips with a TextMeasurer; the
            // snapshotter's own composition provides neither, so both are supplied here.
            val mapState = runtime.createMapState(baseStyle)
            val fontResolver = createFontFamilyResolver()
            val snapshotter =
                runtime.createSnapshotter(baseStyle) {
                    CompositionLocalProvider(
                        LocalMapState provides mapState,
                        LocalFontFamilyResolver provides fontResolver,
                    ) {
                        NodeLayers(
                            nodes = SampleMesh.nodes,
                            myNodeNum = SampleMesh.baseCamp.num,
                            showPrecisionCircles = false,
                            onNodeClick = {},
                            onClusterZoom = { _, _ -> },
                            onClusterMembers = {},
                            visibleBounds = null,
                            zoom = ZOOM.toInt(),
                        )
                    }
                }
            try {
                val request =
                    MapSnapshotRequest(
                        width = widthDp,
                        height = heightDp,
                        cameraPosition =
                        CameraPosition(
                            target = Position(longitude = SampleMesh.CENTER_LON, latitude = SampleMesh.CENTER_LAT),
                            zoom = ZOOM,
                        ),
                        density = density,
                    )
                withTimeout(CAPTURE_TIMEOUT_MS) { snapshotter.capture(request) }
            } finally {
                withContext(NonCancellable) {
                    snapshotter.close()
                    snapshotter.awaitClosed()
                }
            }
        } finally {
            withContext(NonCancellable) {
                runtime.close()
                runtime.awaitClosed()
            }
            cacheDir.toFile().deleteRecursively()
        }
    }
}
