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
package org.meshtastic.feature.node.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/** Axis-aligned bounds of a model in its own units (meters for the flasher's models), Y up. */
data class GlbBounds(val min: FloatArray, val max: FloatArray) {
    val center: FloatArray
        get() = FloatArray(AXES) { (min[it] + max[it]) / 2f }

    val extent: Float
        get() = (0 until AXES).maxOf { max[it] - min[it] }

    override fun equals(other: Any?): Boolean =
        this === other || (other is GlbBounds && min.contentEquals(other.min) && max.contentEquals(other.max))

    override fun hashCode(): Int = 31 * min.contentHashCode() + max.contentHashCode()

    companion object {
        private const val AXES = 3
        private const val HEADER_BYTES = 12
        private const val CHUNK_TYPE_OFFSET = 4
        private const val CHUNK_HEADER_BYTES = 8
        private const val GLB_MAGIC = 0x46546C67 // "glTF"
        private const val JSON_CHUNK = 0x4E4F534A // "JSON"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The union of every mesh primitive's `POSITION` accessor bounds, which glTF requires each accessor to carry.
         * Node transforms are ignored: the flasher's models bake theirs into the vertices. Null for anything that is
         * not a GLB with at least one positioned primitive.
         */
        fun parse(glb: ByteArray): GlbBounds? = jsonChunk(glb)?.let { chunk ->
            runCatching { boundsOf(json.parseToJsonElement(chunk).jsonObject) }.getOrNull()
        }

        /** The notice the model carries in glTF's `asset.copyright`, or null when it has none. */
        fun copyright(glb: ByteArray): String? = jsonChunk(glb)?.let { chunk ->
            runCatching {
                json
                    .parseToJsonElement(chunk)
                    .jsonObject["asset"]
                    ?.jsonObject
                    ?.get("copyright")
                    ?.jsonPrimitive
                    ?.content
            }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        private fun jsonChunk(glb: ByteArray): String? {
            val start = HEADER_BYTES + CHUNK_HEADER_BYTES
            if (glb.size < start) return null
            val length = glb.readIntLe(HEADER_BYTES)
            val valid =
                glb.readIntLe(0) == GLB_MAGIC &&
                    glb.readIntLe(HEADER_BYTES + CHUNK_TYPE_OFFSET) == JSON_CHUNK &&
                    length >= 0 &&
                    length <= glb.size - start
            return if (valid) glb.decodeToString(start, start + length) else null
        }

        /**
         * Bounds of the display glass: primitives of a mesh named `screen`, or using a material named `screen`, else
         * the same for `display`, the name most of the generated models use. Null when the model does not mark its
         * display. The front (+Z) is raised to the front-most triangle drawn over the glass's centre: some models carry
         * the display as a module under the board face and draw its outline on that face.
         */
        fun screen(glb: ByteArray): GlbBounds? = jsonChunk(glb)?.let { chunk ->
            runCatching {
                val root = json.parseToJsonElement(chunk).jsonObject
                val named = namedScreen(root) ?: return@runCatching null
                val front = binChunk(glb)?.let { frontMostOver(root, it, named.center[0], named.center[1]) }
                if (front != null && front > named.max[2]) {
                    GlbBounds(named.min, floatArrayOf(named.max[0], named.max[1], front))
                } else {
                    named
                }
            }
                .getOrNull()
        }

        private fun namedScreen(root: JsonObject): GlbBounds? {
            val materials = root["materials"]?.jsonArray.orEmpty().map { it.jsonObject["name"]?.jsonPrimitive?.content }
            return SCREEN_NAMES.firstNotNullOfOrNull { name ->
                boundsOf(root) { mesh, primitive ->
                    mesh["name"]?.jsonPrimitive?.content.equals(name, ignoreCase = true) ||
                        primitive["material"]
                            ?.jsonPrimitive
                            ?.int
                            ?.let { materials.getOrNull(it) }
                            .equals(name, ignoreCase = true)
                }
            }
        }

        /** The highest Z of any triangle whose XY footprint contains ([x], [y]); null when none does. */
        private fun frontMostOver(root: JsonObject, bin: ByteArray, x: Float, y: Float): Float? {
            val accessors = root["accessors"]?.jsonArray.orEmpty()
            val views = root["bufferViews"]?.jsonArray.orEmpty()
            var front: Float? = null
            for (mesh in root["meshes"]?.jsonArray.orEmpty()) {
                for (primitive in mesh.jsonObject["primitives"]?.jsonArray.orEmpty()) {
                    val reader = TriangleReader.of(primitive.jsonObject, accessors, views, bin) ?: continue
                    for (triangle in 0 until reader.triangles) {
                        val z = reader.zAt(triangle, x, y) ?: continue
                        if (front == null || z > front) front = z
                    }
                }
            }
            return front
        }

        private fun binChunk(glb: ByteArray): ByteArray? {
            val jsonLength = glb.readIntLe(HEADER_BYTES)
            val header = HEADER_BYTES + CHUNK_HEADER_BYTES + jsonLength
            if (header + CHUNK_HEADER_BYTES > glb.size || glb.readIntLe(header + CHUNK_TYPE_OFFSET) != BIN_CHUNK) {
                return null
            }
            val length = glb.readIntLe(header)
            val start = header + CHUNK_HEADER_BYTES
            return if (length in 0..glb.size - start) glb.copyOfRange(start, start + length) else null
        }

        private val SCREEN_NAMES = listOf("screen", "display")
        private const val BIN_CHUNK = 0x004E4942 // "BIN\0"

        private fun boundsOf(
            root: JsonObject,
            include: (mesh: JsonObject, primitive: JsonObject) -> Boolean = { _, _ -> true },
        ): GlbBounds? {
            val accessors = root["accessors"]?.jsonArray.orEmpty()
            val positions =
                root["meshes"]?.jsonArray.orEmpty().flatMap { meshElement ->
                    val mesh = meshElement.jsonObject
                    mesh["primitives"]
                        ?.jsonArray
                        .orEmpty()
                        .filter { include(mesh, it.jsonObject) }
                        .mapNotNull { primitive ->
                            primitive.jsonObject["attributes"]
                                ?.jsonObject
                                ?.get("POSITION")
                                ?.jsonPrimitive
                                ?.int
                                ?.let { accessors[it].jsonObject }
                                ?.takeIf { "min" in it && "max" in it }
                        }
                }
            if (positions.isEmpty()) return null
            fun axis(key: String, axis: Int) = positions.map { it.getValue(key).jsonArray[axis].jsonPrimitive.float }
            return GlbBounds(
                min = FloatArray(AXES) { axis("min", it).min() },
                max = FloatArray(AXES) { axis("max", it).max() },
            )
        }
    }
}

/**
 * The triangles of one glTF primitive, read from the GLB's binary chunk; node transforms are ignored as in [GlbBounds].
 */
private class TriangleReader
private constructor(
    private val bin: ByteArray,
    private val positionOffset: Int,
    private val positionStride: Int,
    private val vertexCount: Int,
    private val indexOffset: Int,
    private val indexBytes: Int,
    val triangles: Int,
) {
    /** Z of [triangle]'s plane at ([x], [y]), or null when the point is outside its XY footprint. */
    fun zAt(triangle: Int, x: Float, y: Float): Float? {
        val a = vertex(triangle * CORNERS)
        val b = vertex(triangle * CORNERS + 1)
        val c = vertex(triangle * CORNERS + 2)
        return if (a == null || b == null || c == null) null else planeZ(a, b, c, x, y)
    }

    private fun planeZ(a: FloatArray, b: FloatArray, c: FloatArray, x: Float, y: Float): Float? {
        val d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if (abs(d) < DEGENERATE) return null
        val u = ((b[1] - c[1]) * (x - c[0]) + (c[0] - b[0]) * (y - c[1])) / d
        val v = ((c[1] - a[1]) * (x - c[0]) + (a[0] - c[0]) * (y - c[1])) / d
        val w = 1f - u - v
        return if (u < 0f || v < 0f || w < 0f) null else u * a[2] + v * b[2] + w * c[2]
    }

    private fun vertex(corner: Int): FloatArray? {
        val index = if (indexOffset < 0) corner else readIndex(corner)
        val at = positionOffset + index * positionStride
        return if (index !in 0 until vertexCount || at < 0 || at + VERTEX_BYTES > bin.size) {
            null
        } else {
            FloatArray(AXES) { Float.fromBits(bin.readIntLe(at + it * FLOAT_BYTES)) }
        }
    }

    private fun readIndex(corner: Int): Int {
        val at = indexOffset + corner * indexBytes
        if (at < 0 || at + indexBytes > bin.size) return -1
        return when (indexBytes) {
            1 -> bin[at].toInt() and BYTE_MASK
            2 -> (bin[at].toInt() and BYTE_MASK) or ((bin[at + 1].toInt() and BYTE_MASK) shl BYTE_BITS)
            else -> bin.readIntLe(at)
        }
    }

    companion object {
        private const val AXES = 3
        private const val CORNERS = 3
        private const val FLOAT_BYTES = 4
        private const val VERTEX_BYTES = AXES * FLOAT_BYTES
        private const val MODE_TRIANGLES = 4
        private const val COMPONENT_FLOAT = 5126
        private val INDEX_BYTES = mapOf(5121 to 1, 5123 to 2, 5125 to 4)
        private const val DEGENERATE = 1e-12f

        /** Null for anything but an indexed or plain triangle list of float VEC3 positions in buffer 0. */
        fun of(
            primitive: JsonObject,
            accessors: List<JsonElement>,
            views: List<JsonElement>,
            bin: ByteArray,
        ): TriangleReader? {
            val mode = primitive["mode"]?.jsonPrimitive?.int ?: MODE_TRIANGLES
            val position = primitive["attributes"]?.jsonObject?.get("POSITION")?.accessor(accessors)
            val positionView = position?.takeIf { it.isFloatVec3() }?.view(views)
            val vertexCount = position?.get("count")?.jsonPrimitive?.int
            val indices = primitive["indices"]?.accessor(accessors)
            val indexBytes = indices?.let { INDEX_BYTES[it["componentType"]?.jsonPrimitive?.int] }
            val indexView = indices?.view(views)
            val indexed = indices != null
            val usable =
                mode == MODE_TRIANGLES &&
                    positionView != null &&
                    vertexCount != null &&
                    (!indexed || (indexBytes != null && indexView != null))
            return if (!usable) {
                null
            } else {
                TriangleReader(
                    bin = bin,
                    positionOffset = positionView.offset,
                    positionStride = positionView.stride ?: VERTEX_BYTES,
                    vertexCount = vertexCount,
                    indexOffset = indexView?.offset ?: -1,
                    indexBytes = indexBytes ?: 0,
                    triangles = (indices?.get("count")?.jsonPrimitive?.int ?: vertexCount) / CORNERS,
                )
            }
        }

        private class View(val offset: Int, val stride: Int?)

        private fun JsonElement.accessor(accessors: List<JsonElement>): JsonObject? =
            jsonPrimitive.int.let { accessors.getOrNull(it) }?.jsonObject

        private fun JsonObject.isFloatVec3(): Boolean = this["componentType"]?.jsonPrimitive?.int == COMPONENT_FLOAT &&
            this["type"]?.jsonPrimitive?.content == "VEC3"

        private fun JsonObject.view(views: List<JsonElement>): View? = this["bufferView"]
            ?.accessor(views)
            ?.takeIf { (it["buffer"]?.jsonPrimitive?.int ?: 0) == 0 }
            ?.let { view ->
                val offset = view["byteOffset"]?.jsonPrimitive?.int ?: 0
                View(offset + (this["byteOffset"]?.jsonPrimitive?.int ?: 0), view["byteStride"]?.jsonPrimitive?.int)
            }
    }
}

private const val INT_BYTES = 4
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF

private fun ByteArray.readIntLe(offset: Int): Int = (0 until INT_BYTES).fold(0) { acc, i ->
    acc or ((this[offset + i].toInt() and BYTE_MASK) shl (BYTE_BITS * i))
}
