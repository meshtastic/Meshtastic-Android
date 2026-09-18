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
@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package org.meshtastic.desktop.spike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.MapSnapshotRequest
import org.maplibre.compose.map.MapRuntimeOptions
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.maplibre.layers.NodeLayers
import org.meshtastic.feature.map.maplibre.style.Basemaps
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SPIKE (throwaway): can the maplibre native runtime capture the mesh map to a PNG on a JVM with no display?
 *
 * Writes `build/spike/map-<tag>-{1,2}.png` and `build/spike/map-<tag>.log`; the second capture is the warm one.
 */
class MapSnapshotterSpikeTest {

    private val log = StringBuilder()

    private fun note(line: String) {
        log.appendLine(line)
        System.err.println("SPIKE: $line")
    }

    @Test
    fun `capture the mesh map headless`() {
        val tag = SpikeMesh.runTag
        val logFile = File(SpikeMesh.outDir, "map-$tag.log")
        try {
            note("DISPLAY=${System.getenv("DISPLAY")} WAYLAND_DISPLAY=${System.getenv("WAYLAND_DISPLAY")}")
            note("LD_LIBRARY_PATH=${System.getenv("LD_LIBRARY_PATH")}")
            note("VK_DRIVER_FILES=${System.getenv("VK_DRIVER_FILES")} VK_ICD_FILENAMES=${System.getenv("VK_ICD_FILENAMES")}")
            note("java=${System.getProperty("java.version")} headless=${System.getProperty("java.awt.headless")}")
            assertNull(System.getenv("DISPLAY"))
            assertNull(System.getenv("WAYLAND_DISPLAY"))
            captureTwice(tag)
        } catch (t: Throwable) {
            note("FAILED: ${t::class.qualifiedName}: ${t.message}")
            note(t.stackTraceToString().lineSequence().take(25).joinToString("\n"))
            throw t
        } finally {
            logFile.writeText(log.toString())
        }
    }

    private fun captureTwice(tag: String) = runBlocking {
        val t0 = System.nanoTime()
        val cacheDir = Files.createTempDirectory("spike-maplibre")
        val runtime = createMapRuntime(MapRuntimeOptions(cacheFile = Path(cacheDir.resolve("cache.db").toString())))
        note("createMapRuntime: ${ms(t0)} ms (cache ${cacheDir})")
        try {
            val baseStyle = BaseStyle.Uri(Basemaps.Liberty.styleUri)
            // NodeLayers reads LocalMapState (for cluster clicks) and rasterizes chips with a TextMeasurer; the
            // snapshotter's own composition provides neither, so both are supplied here.
            val mapState = runtime.createMapState(baseStyle)
            val fontResolver = createFontFamilyResolver()
            val content: @Composable @MaplibreComposable () -> Unit = {
                CompositionLocalProvider(
                    LocalMapState provides mapState,
                    LocalFontFamilyResolver provides fontResolver,
                ) {
                    NodeLayers(
                        nodes = SpikeMesh.nodes,
                        myNodeNum = SpikeMesh.nodes.first().num,
                        showPrecisionCircles = false,
                        onNodeClick = {},
                        onClusterZoom = { _, _ -> },
                        onClusterMembers = {},
                        visibleBounds = null,
                        zoom = 11,
                    )
                }
            }
            val snapshotter = runtime.createSnapshotter(baseStyle, content)
            note("createSnapshotter: ${ms(t0)} ms")
            try {
                val request =
                    MapSnapshotRequest(
                        width = 432,
                        height = 864,
                        cameraPosition =
                        CameraPosition(
                            target = Position(longitude = SpikeMesh.DALLAS_LON, latitude = SpikeMesh.DALLAS_LAT),
                            zoom = 11.5,
                        ),
                        density = 2.5f,
                    )
                repeat(2) { i ->
                    val c0 = System.nanoTime()
                    val bitmap = withTimeout(180_000) { snapshotter.capture(request) }
                    val captureMs = ms(c0)
                    val file = File(SpikeMesh.outDir, "map-$tag-${i + 1}.png")
                    bitmap.writePng(file)
                    note("capture ${i + 1}: $captureMs ms, ${bitmap.width}x${bitmap.height} -> ${file.absolutePath}")
                    assertEquals(1080, bitmap.width)
                    assertEquals(2160, bitmap.height)
                }
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
            note("total: ${ms(t0)} ms")
        }
    }

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000

    private fun ImageBitmap.writePng(file: File) {
        val data = checkNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG))
        file.writeBytes(data.bytes)
    }
}
