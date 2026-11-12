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
package org.meshtastic.app.map

import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.feature.map.layers.LayerType
import org.meshtastic.feature.map.layers.MapLayerProblem
import org.meshtastic.feature.map.layers.MapLayerRefusal
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.layers.PickedMapFile
import org.meshtastic.feature.map.layers.maxSourceBytes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapLayerRefusalTest {

    private val layersDir: Path = createTempDirectory("map-layer-refusal")
    private val httpClient = HttpClient()

    @AfterTest
    fun tearDown() {
        httpClient.close()
        layersDir.toFile().deleteRecursively()
    }

    private fun TestScope.manager(): Pair<MapLayersManager, List<MapLayerProblem>> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager =
            MapLayersManager(
                dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
                httpClient = httpClient,
                mapPrefs = FakeMapPrefs(),
                layersDir = layersDir.toOkioPath(),
            )
        val problems = mutableListOf<MapLayerProblem>()
        backgroundScope.launch(dispatcher) { manager.layerProblems.toList(problems) }
        return manager to problems
    }

    private fun picked(name: String, text: String) =
        PickedMapFile(displayName = name, extensionOrMime = name.substringAfterLast('.'), read = { text.toByteArray() })

    @Test
    fun `a json file that is not GeoJSON is refused at import and never stored`() = runTest {
        val (manager, problems) = manager()

        manager.addMapLayer(picked("package.json", """{"name":"app","version":"1.0.0"}"""))

        assertTrue(manager.mapLayers.value.isEmpty())
        assertEquals(0, Files.list(layersDir).use { it.count() })
        val problem = problems.single()
        assertEquals(MapLayerRefusal.NOT_MAP_DATA, problem.refusal)
        assertTrue(problem.atImport)
        assertEquals("package", problem.layerName)
    }

    @Test
    fun `a GeoJSON file is imported and reads back`() = runTest {
        val (manager, problems) = manager()
        val geoJson = """{"type":"FeatureCollection","features":[]}"""

        manager.addMapLayer(picked("route.geojson", geoJson))

        val layer = manager.mapLayers.value.single()
        assertEquals(LayerType.GEOJSON, layer.layerType)
        assertContentEquals(geoJson.toByteArray(), manager.readLayerBytes(layer))
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `a stored json that is not GeoJSON is skipped and reported once`() = runTest {
        layersDir
            .resolve("firmware-2.7.26_00000000-0000-0000-0000-000000000000.geojson")
            .writeText("""{"version":"2.7.26"}""")
        val (manager, problems) = manager()
        val layer = manager.mapLayers.value.single()

        assertNull(manager.readLayerBytes(layer))
        assertNull(manager.readLayerBytes(layer))

        val problem = problems.single()
        assertEquals(MapLayerRefusal.NOT_MAP_DATA, problem.refusal)
        assertEquals(false, problem.atImport)
        assertEquals("firmware-2.7.26", problem.layerName)
    }

    @Test
    fun `a layer the renderer has no room for is reported once and then skipped`() = runTest {
        layersDir
            .resolve("dense_00000000-0000-0000-0000-000000000000.geojson")
            .writeText("""{"type":"FeatureCollection","features":[]}""")
        val (manager, problems) = manager()
        val layer = manager.mapLayers.value.single()
        assertNotNull(manager.readLayerBytes(layer))

        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED)
        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED)

        assertEquals(MapLayerRefusal.TOO_DETAILED, problems.single().refusal)
        assertNull(manager.readLayerBytes(layer))
    }

    @Test
    fun `refreshing a layer lets it be read and reported again`() = runTest {
        layersDir
            .resolve("dense_00000000-0000-0000-0000-000000000000.geojson")
            .writeText("""{"type":"FeatureCollection","features":[]}""")
        val (manager, problems) = manager()
        val layer = manager.mapLayers.value.single()
        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED)

        manager.refreshMapLayer(layer.id)

        assertNotNull(manager.readLayerBytes(manager.mapLayers.value.single()))
        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED)
        assertEquals(2, problems.size)
    }

    @Test
    fun `a layer that only lost out to the others drawn is reported once and still read`() = runTest {
        layersDir
            .resolve("dense_00000000-0000-0000-0000-000000000000.geojson")
            .writeText("""{"type":"FeatureCollection","features":[]}""")
        val (manager, problems) = manager()
        val layer = manager.mapLayers.value.single()

        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED, permanent = false)
        manager.reportUndrawable(layer, MapLayerRefusal.TOO_DETAILED, permanent = false)

        assertEquals(1, problems.size)
        assertNotNull(manager.readLayerBytes(layer))
    }

    @Test
    fun `a picked file is asked for one byte past its limit and refused when it has it`() = runTest {
        val (manager, problems) = manager()
        val limit = LayerType.GEOJSON.maxSourceBytes()
        var asked = 0L
        val oversized =
            PickedMapFile(
                displayName = "huge.geojson",
                extensionOrMime = "geojson",
                read = { maxBytes ->
                    asked = maxBytes
                    ByteArray(maxBytes.toInt())
                },
            )

        manager.addMapLayer(oversized)

        assertEquals(limit + 1, asked)
        assertTrue(manager.mapLayers.value.isEmpty())
        val problem = problems.single()
        assertEquals(MapLayerRefusal.TOO_LARGE, problem.refusal)
        assertTrue(problem.atImport)
    }
}
