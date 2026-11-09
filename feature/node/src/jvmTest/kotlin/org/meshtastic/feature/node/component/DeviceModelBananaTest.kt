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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.DeviceModelRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.node.model.BananaForScale
import org.meshtastic.feature.node.model.GlbBounds
import org.meshtastic.feature.node.model.GlbMerge
import org.meshtastic.feature.node.model.RestPose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The banana for scale: bundled, real size, merged beside any model with the device's own file left as it was. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceModelBananaTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun viewModel(models: Map<String, ByteArray> = emptyMap()) = DeviceModelViewModel(
        repository =
        object : DeviceModelRepository {
            override suspend fun hasModel(imageFile: String) = imageFile in models

            override suspend fun getModel(imageFile: String) = models[imageFile]
        },
        displayMirrorManager = FakeDisplayMirrorManager(),
        adminController = FakeRadioController(),
        nodeRepository = FakeNodeRepository(),
        radioConfigRepository = FakeRadioConfigRepository(),
        dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the bundled banana is real size and carries its CC0 credit`() = runTest {
        val banana = Res.readBytes(BananaForScale.MODEL)

        val bounds = assertNotNull(GlbBounds.parse(banana))
        assertEquals(0.185f, bounds.max[1] - bounds.min[1], 0.002f)
        assertEquals(0.100f, bounds.max[0] - bounds.min[0], 0.002f)
        assertEquals(0.044f, bounds.max[2] - bounds.min[2], 0.002f)
        assertEquals(0f, bounds.min[1])
        val notice = GlbBounds.copyright(banana).orEmpty()
        assertTrue("Keistutis" in notice && "CC0" in notice, "notice was $notice")
    }

    @Test
    fun `the merged file keeps the device and stands the banana on its left`() = runTest {
        val device = Res.readBytes(DeviceModelViewModel.CHIRPY_MODEL)
        val banana = Res.readBytes(BananaForScale.MODEL)
        val deviceBounds = assertNotNull(GlbBounds.parse(device))
        val bananaBounds = assertNotNull(GlbBounds.parse(banana))

        val staged = assertNotNull(BananaForScale.stage(device, deviceBounds, GlbBounds.copyright(device), banana))

        val merged = gltf(staged.glb)
        val original = gltf(device)
        // The device's entries come first and unchanged, so its indices and coordinates still hold.
        for (key in listOf("accessors", "bufferViews", "meshes", "materials", "nodes")) {
            // Through the same encoder: kotlinx writes numbers back as doubles, so 0.0009 comes out 9.0E-4.
            assertEquals(
                original.array(key).map { it.toString() }.map(::reencoded),
                merged.array(key).take(original.array(key).size).map { it.toString() }.map(::reencoded),
                key,
            )
        }
        val wrapper = merged.array("nodes").last().jsonObject
        val translation = wrapper.getValue("translation").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
        assertContentEquals(BananaForScale.beside(deviceBounds, bananaBounds), translation)
        assertTrue(translation[0] + bananaBounds.max[0] < deviceBounds.min[0], "banana overlaps the device")
        assertEquals(deviceBounds.min[1], translation[1] + bananaBounds.min[1])
        assertEquals(
            deviceBounds.min[0] - 0.03f - (bananaBounds.max[0] - bananaBounds.min[0]),
            staged.bounds.min[0],
            1e-5f,
        )
        assertEquals(maxOf(deviceBounds.max[1], deviceBounds.min[1] + 0.185f), staged.bounds.max[1], 0.002f)
        assertTrue("Chirpy" in staged.copyright.orEmpty() && "Keistutis" in staged.copyright.orEmpty())
        assertEquals(staged.copyright, GlbBounds.copyright(staged.glb))
        assertEveryIndexInRange(merged, staged.glb)
    }

    @Test
    fun `the banana stays clear of the device in AR however it rests`() = runTest {
        val device = assertNotNull(GlbBounds.parse(Res.readBytes(DeviceModelViewModel.CHIRPY_MODEL)))
        val banana = assertNotNull(GlbBounds.parse(Res.readBytes(BananaForScale.MODEL)))

        for (pose in RestPose.entries) {
            val footprint = pose.rotatedBounds(device)
            val at = BananaForScale.besideOnSurface(device, pose, banana)
            // The footprint's far corner sweeps this circle as the device turns about its center.
            val turning =
                kotlin.math.hypot(footprint.max[0] - footprint.center[0], footprint.max[2] - footprint.center[2])
            assertTrue(at[0] + banana.max[0] < -turning, "$pose: banana inside the device's turning circle")
            assertEquals(0f, at[1] + banana.min[1], 1e-6f)
        }
    }

    @Test
    fun `the banana stands beside each model shown and never comes from the network`() = runTest {
        val chirpy = Res.readBytes(DeviceModelViewModel.CHIRPY_MODEL)
        val viewModel = viewModel(mapOf("other.png" to chirpy))
        viewModel.showChirpy()
        viewModel.state.first { it is DeviceModelState.Ready }

        viewModel.setBanana(true)

        val first = assertNotNull(viewModel.withBanana.first { it != null })
        assertEquals("chirpy", first.imageFile)
        assertNotNull(viewModel.banana.value)

        viewModel.load("other.png")
        val second = assertNotNull(viewModel.withBanana.first { it?.imageFile == "other.png" })
        assertTrue("Keistutis" in second.copyright.orEmpty())
        // The device on its own is still what AR gets.
        val ready = assertIs<DeviceModelState.Ready>(viewModel.state.value)
        assertEquals(chirpy.size, ready.glb.size)

        viewModel.setBanana(false)
        assertNull(viewModel.withBanana.value)
    }

    @Test
    fun `a file the merge cannot carry over is refused rather than half merged`() = runTest {
        val device = Res.readBytes(DeviceModelViewModel.CHIRPY_MODEL)
        val banana = Res.readBytes(BananaForScale.MODEL)
        val at = floatArrayOf(0f, 0f, 0f)
        assertNotNull(GlbMerge.append(device, banana, at, null))

        val version1 = { glb: ByteArray -> glb.copyOf().also { it[4] = 1 } }
        assertNull(GlbMerge.append(version1(device), banana, at, null), "base GLB 1")
        assertNull(GlbMerge.append(device, version1(banana), at, null), "added GLB 1")

        val twoBuffers =
            withJson(device) { json ->
                val buffers = json.getValue("buffers").jsonArray
                JsonObject(json + ("buffers" to JsonArray(buffers + buffers.first())))
            }
        assertNull(GlbMerge.append(twoBuffers, banana, at, null), "base with a second buffer")

        val morphing =
            withJson(banana) { json ->
                val meshes =
                    json.getValue("meshes").jsonArray.map { mesh ->
                        val primitives =
                            mesh.jsonObject.getValue("primitives").jsonArray.map {
                                JsonObject(
                                    it.jsonObject + ("targets" to Json.parseToJsonElement("""[{"POSITION":0}]""")),
                                )
                            }
                        JsonObject(mesh.jsonObject + ("primitives" to JsonArray(primitives)))
                    }
                JsonObject(json + ("meshes" to JsonArray(meshes)))
            }
        assertNull(GlbMerge.append(device, morphing, at, null), "added with morph targets")

        val skinned =
            withJson(banana) { json -> JsonObject(json + ("skins" to Json.parseToJsonElement("""[{"joints":[0]}]"""))) }
        assertNull(GlbMerge.append(device, skinned, at, null), "added with a skin")

        val sparse =
            withJson(banana) { json ->
                val accessors = json.getValue("accessors").jsonArray
                val first = accessors.first().jsonObject + ("sparse" to Json.parseToJsonElement("""{"count":1}"""))
                JsonObject(json + ("accessors" to JsonArray(listOf(JsonObject(first)) + accessors.drop(1))))
            }
        assertNull(GlbMerge.append(device, sparse, at, null), "added with sparse data")
    }

    /** [glb] with its JSON chunk replaced by [transform] of it, the binary chunk kept. */
    private fun withJson(glb: ByteArray, transform: (JsonObject) -> JsonObject): ByteArray {
        val le = { offset: Int -> ByteBuffer.wrap(glb, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int }
        val jsonLength = le(12)
        val bin = glb.copyOfRange(20 + jsonLength, glb.size)
        val text =
            transform(gltf(glb)).toString().encodeToByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { 0x20 } }
        val out = ByteBuffer.allocate(20 + text.size + bin.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(le(0)).putInt(le(4)).putInt(20 + text.size + bin.size).putInt(text.size).putInt(le(16))
        return out.put(text).put(bin).array()
    }

    private fun gltf(glb: ByteArray): JsonObject {
        val length = ByteBuffer.wrap(glb, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return Json.parseToJsonElement(glb.decodeToString(20, 20 + length)).jsonObject
    }

    private fun JsonObject.array(key: String) = this[key]?.jsonArray.orEmpty()

    private fun reencoded(json: String): String =
        Json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(json))

    private fun assertEveryIndexInRange(gltf: JsonObject, glb: ByteArray) {
        val jsonLength = ByteBuffer.wrap(glb, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val binLength = ByteBuffer.wrap(glb, 20 + jsonLength, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(glb.size, ByteBuffer.wrap(glb, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(binLength, gltf.array("buffers").single().jsonObject.getValue("byteLength").jsonPrimitive.int)
        for (view in gltf.array("bufferViews")) {
            val offset = view.jsonObject["byteOffset"]?.jsonPrimitive?.int ?: 0
            assertTrue(offset + view.jsonObject.getValue("byteLength").jsonPrimitive.int <= binLength)
        }
        val views = gltf.array("bufferViews").size
        gltf.array("accessors").forEach { assertTrue(it.jsonObject.getValue("bufferView").jsonPrimitive.int < views) }
        gltf.array("images").forEach { assertTrue(it.jsonObject.getValue("bufferView").jsonPrimitive.int < views) }
        val images = gltf.array("images").size
        gltf.array("textures").forEach { assertTrue(it.jsonObject.getValue("source").jsonPrimitive.int < images) }
        val accessors = gltf.array("accessors").size
        val materials = gltf.array("materials").size
        for (mesh in gltf.array("meshes")) {
            for (primitive in mesh.jsonObject.getValue("primitives").jsonArray.map { it.jsonObject }) {
                primitive.getValue("attributes").jsonObject.values.forEach {
                    assertTrue(it.jsonPrimitive.int < accessors)
                }
                primitive["indices"]?.let { assertTrue(it.jsonPrimitive.int < accessors) }
                primitive["material"]?.let { assertTrue(it.jsonPrimitive.int < materials) }
            }
        }
        val nodes = gltf.array("nodes").size
        val meshes = gltf.array("meshes").size
        for (node in gltf.array("nodes").map { it.jsonObject }) {
            node["mesh"]?.let { assertTrue(it.jsonPrimitive.int < meshes) }
            node["children"]?.jsonArray?.forEach { assertTrue(it.jsonPrimitive.int < nodes) }
        }
        gltf.array("scenes").single().jsonObject.getValue("nodes").jsonArray.forEach {
            assertTrue(it.jsonPrimitive.int < nodes)
        }
    }
}
