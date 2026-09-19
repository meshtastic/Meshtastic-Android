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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.MapRuntime
import org.maplibre.compose.map.MapRuntimeOptions
import org.maplibre.compose.map.MapSnapshotRequest
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.maplibre.layers.NodeLayers
import org.meshtastic.feature.map.maplibre.style.Basemaps
import java.nio.file.Files

/** One capture: the map area in dp at the screen density, and the zoom the form factor asks for. */
internal data class MapArea(val widthDp: Int, val heightDp: Int, val density: Float, val zoom: Double)

/**
 * Captures the real mesh map — the app's default basemap with its own [NodeLayers] chips over [SampleMesh] — through
 * maplibre-compose's snapshotter, with no window and no display. Each [MapArea] is the map area between a form factor's
 * app bar and its navigation at that screen's density, so the screen composable places it without rescaling.
 */
internal object MapSnapshot {
    private const val CAPTURE_TIMEOUT_MS = 180_000L
    private const val MAX_SETTLE_RUNTIMES = 4

    fun capture(areas: List<MapArea>, mesh: SampleMesh): Map<MapArea, ImageBitmap> =
        areas.associateWith { area -> captureUntilStable(area, mesh) }

    /**
     * Label and glyph placement depends on the order tiles arrive, which the network decides on the first load, so a
     * capture can differ from the next run's by a few pixels; a second capture from the same runtime always matches the
     * first, because that runtime already holds the placement. So each capture comes from a fresh runtime over one
     * shared tile cache: the first fills the cache from the network, the ones after it read the cache in a stable
     * order, and two consecutive runtimes agreeing means the run is reproducible. A warm runtime costs about a second.
     */
    private fun captureUntilStable(area: MapArea, mesh: SampleMesh): ImageBitmap = runBlocking {
        val cacheDir = Files.createTempDirectory("marketing-maplibre")
        val cacheFile = Path(cacheDir.resolve("cache.db").toString())
        try {
            var previous = captureOnce(cacheFile, area, mesh)
            repeat(MAX_SETTLE_RUNTIMES - 1) {
                val next = captureOnce(cacheFile, area, mesh)
                if (next.toPixelMap().buffer.contentEquals(previous.toPixelMap().buffer)) return@runBlocking next
                previous = next
            }
            System.err.println(
                "[marketing-screenshots] warning: map ${area.widthDp}x${area.heightDp}@${area.density} never settled",
            )
            previous
        } finally {
            cacheDir.toFile().deleteRecursively()
        }
    }

    private suspend fun captureOnce(cacheFile: Path, area: MapArea, mesh: SampleMesh): ImageBitmap {
        val runtime = createMapRuntime(MapRuntimeOptions(cacheFile = cacheFile))
        return try {
            withTimeout(CAPTURE_TIMEOUT_MS) { runtime.capture(area, mesh) }
        } finally {
            withContext(NonCancellable) {
                runtime.close()
                runtime.awaitClosed()
            }
        }
    }

    /**
     * The app's default basemap, labels included: a street map with no names is not the in-app experience Play asks
     * for. MapLibre packs glyphs into an atlas in the order tiles arrive, so on the wide layouts a label's antialiased
     * edge can land one level off between generations - a dozen pixels, invisible - which is why [captureUntilStable]
     * warns rather than fails when two runtimes disagree, and why a regenerated wide map may not `cmp` the last one.
     */
    private val baseStyle: BaseStyle = BaseStyle.Uri(Basemaps.Liberty.styleUri)

    private suspend fun MapRuntime.capture(area: MapArea, mesh: SampleMesh): ImageBitmap {
        // NodeLayers reads LocalMapState (for cluster clicks) and rasterizes chips with a TextMeasurer; the
        // snapshotter's own composition provides neither, so both are supplied here.
        val mapState = createMapState(baseStyle)
        val fontResolver = createFontFamilyResolver()
        val snapshotter =
            createSnapshotter(baseStyle) {
                CompositionLocalProvider(
                    LocalMapState provides mapState,
                    LocalFontFamilyResolver provides fontResolver,
                ) {
                    NodeLayers(
                        nodes = mesh.nodes,
                        myNodeNum = mesh.baseCamp.num,
                        showPrecisionCircles = false,
                        onNodeClick = {},
                        onClusterZoom = { _, _ -> },
                        onClusterMembers = {},
                        visibleBounds = null,
                        zoom = area.zoom.toInt(),
                    )
                }
            }
        return try {
            val request =
                MapSnapshotRequest(
                    width = area.widthDp,
                    height = area.heightDp,
                    cameraPosition =
                    CameraPosition(
                        target = Position(longitude = SampleMesh.CENTER_LON, latitude = SampleMesh.CENTER_LAT),
                        zoom = area.zoom,
                    ),
                    density = area.density,
                )
            snapshotter.capture(request)
        } finally {
            withContext(NonCancellable) {
                snapshotter.close()
                snapshotter.awaitClosed()
            }
        }
    }
}
