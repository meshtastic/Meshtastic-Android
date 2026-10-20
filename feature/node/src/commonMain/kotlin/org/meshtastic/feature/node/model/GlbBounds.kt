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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        private const val INT_BYTES = 4
        private const val BYTE_BITS = 8
        private const val BYTE_MASK = 0xFF
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

        private fun boundsOf(root: JsonObject): GlbBounds? {
            val accessors = root["accessors"]?.jsonArray.orEmpty()
            val positions =
                root["meshes"]?.jsonArray.orEmpty().flatMap { mesh ->
                    mesh.jsonObject["primitives"]?.jsonArray.orEmpty().mapNotNull { primitive ->
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

        private fun ByteArray.readIntLe(offset: Int): Int = (0 until INT_BYTES).fold(0) { acc, i ->
            acc or ((this[offset + i].toInt() and BYTE_MASK) shl (BYTE_BITS * i))
        }
    }
}
