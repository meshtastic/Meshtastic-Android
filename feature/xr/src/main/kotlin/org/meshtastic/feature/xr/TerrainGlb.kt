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

package org.meshtastic.feature.xr

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * [mesh] with [image] draped over it as a binary glTF. SceneCore loads a texture only from the app's assets, but a
 * model from bytes, so an image made at run time reaches the scene inside a model. The material is unlit: the light is
 * already in the vertex colors, which glTF multiplies into the image.
 */
internal fun terrainGlb(mesh: TerrainMeshData, image: ByteArray, mimeType: String, cutout: Boolean = false): ByteArray {
    // An image with clear parts is cut out along them, and seen from both sides.
    val alpha = if (cutout) """"alphaMode":"MASK","alphaCutoff":0.5,"doubleSided":true,""" else ""
    val count = mesh.vertexCount
    val bin = ByteArrayOutputStream()
    val views = ArrayList<String>()

    fun view(bytes: ByteArray, target: Int?) {
        val offset = bin.size()
        bin.write(bytes)
        repeat((4 - bytes.size % 4) % 4) { bin.write(0) }
        val bound = target?.let { ""","target":$it""" }.orEmpty()
        views += """{"buffer":0,"byteOffset":$offset,"byteLength":${bytes.size}$bound}"""
    }

    fun floats(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
    view(floats(mesh.positions), ARRAY_BUFFER)
    view(floats(mesh.normals), ARRAY_BUFFER)
    view(ByteArray(count * 4) { (mesh.colors[it].coerceIn(0f, 1f) * 255f).roundToInt().toByte() }, ARRAY_BUFFER)
    view(floats(mesh.uvs), ARRAY_BUFFER)
    val indices = ByteBuffer.allocate(mesh.indices.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    mesh.indices.forEach { indices.putInt(it) }
    view(indices.array(), ELEMENT_ARRAY_BUFFER)
    view(image, null)

    val low = FloatArray(3) { Float.MAX_VALUE }
    val high = FloatArray(3) { -Float.MAX_VALUE }
    for (i in 0 until count * 3) {
        low[i % 3] = minOf(low[i % 3], mesh.positions[i])
        high[i % 3] = maxOf(high[i % 3], mesh.positions[i])
    }
    val json =
        """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],""" +
            """"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"COLOR_0":2,"TEXCOORD_0":3},""" +
            """"indices":4,"material":0}]}],""" +
            """"materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"metallicFactor":0,""" +
            """"roughnessFactor":1},$alpha"extensions":{"KHR_materials_unlit":{}}}],""" +
            """"extensionsUsed":["KHR_materials_unlit"],"textures":[{"sampler":0,"source":0}],""" +
            """"samplers":[{"magFilter":9729,"minFilter":9987,"wrapS":33071,"wrapT":33071}],""" +
            """"images":[{"bufferView":5,"mimeType":"$mimeType"}],"accessors":[""" +
            """{"bufferView":0,"componentType":5126,"count":$count,"type":"VEC3",""" +
            """"min":[${low.joinToString(",")}],"max":[${high.joinToString(",")}]},""" +
            """{"bufferView":1,"componentType":5126,"count":$count,"type":"VEC3"},""" +
            """{"bufferView":2,"componentType":5121,"normalized":true,"count":$count,"type":"VEC4"},""" +
            """{"bufferView":3,"componentType":5126,"count":$count,"type":"VEC2"},""" +
            """{"bufferView":4,"componentType":5125,"count":${mesh.indices.size},"type":"SCALAR"}],""" +
            """"bufferViews":[${views.joinToString(",")}],"buffers":[{"byteLength":${bin.size()}}]}"""
    val jsonBytes = json.toByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { SPACE } }
    val total = HEADER_BYTES + CHUNK_HEADER_BYTES + jsonBytes.size + CHUNK_HEADER_BYTES + bin.size()
    val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
    out.putInt(GLB_MAGIC).putInt(2).putInt(total)
    out.putInt(jsonBytes.size).putInt(JSON_CHUNK).put(jsonBytes)
    out.putInt(bin.size()).putInt(BIN_CHUNK).put(bin.toByteArray())
    return out.array()
}

private const val ARRAY_BUFFER = 34962
private const val ELEMENT_ARRAY_BUFFER = 34963
private const val HEADER_BYTES = 12
private const val CHUNK_HEADER_BYTES = 8
private const val GLB_MAGIC = 0x46546C67
private const val JSON_CHUNK = 0x4E4F534A
private const val BIN_CHUNK = 0x004E4942
private const val SPACE: Byte = 0x20
