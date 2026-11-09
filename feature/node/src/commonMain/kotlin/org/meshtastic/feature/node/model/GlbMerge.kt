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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Two GLBs as one, so a renderer that takes a single model can show both. The base file's JSON and binary data are kept
 * as they are, so its indices, extensions and the coordinates read from it stay valid; the added model's are appended
 * after them, under one new root node that carries its translation.
 */
object GlbMerge {
    /**
     * [base] with [added] moved by [translation], and [copyright] as the file's notice. Null when either is not a GLB 2
     * with one embedded buffer, or [added] uses something this does not carry over (extensions, skins, morph targets,
     * sparse data, files).
     */
    fun append(base: ByteArray, added: ByteArray, translation: FloatArray, copyright: String?): ByteArray? =
        runCatching {
            // The merged file has one buffer, the base's binary chunk followed by the added one's.
            val first = GlbFile.read(base)?.takeIf { it.hasOneEmbeddedBuffer() } ?: return null
            val second = GlbFile.read(added)?.takeIf { it.isPlain() } ?: return null
            val binOffset = first.bin.size.paddedTo4()
            val bin = first.bin.copyOf(binOffset) + second.bin
            val shift = { key: String -> first.json[key]?.jsonArray?.size ?: 0 }
            val moved = second.remapped(shift, binOffset)
            val root = first.json.arrayOf(NODES).size + moved.arrayOf(NODES).size
            val wrapper = buildJsonObject {
                put("translation", JsonArray(translation.map { JsonPrimitive(it) }))
                put("children", JsonArray(second.rootNodes().map { JsonPrimitive(it + shift(NODES)) }))
            }
            val json =
                JsonObject(
                    first.json +
                        MERGED_ARRAYS.associateWith { key ->
                            JsonArray(first.json.arrayOf(key) + moved.arrayOf(key))
                        }
                            .filterValues { it.isNotEmpty() } +
                        mapOf(
                            NODES to JsonArray(first.json.arrayOf(NODES) + moved.arrayOf(NODES) + wrapper),
                            "buffers" to JsonArray(listOf(JsonObject(mapOf("byteLength" to JsonPrimitive(bin.size))))),
                            "scenes" to first.scenesWith(root),
                            "asset" to first.assetWith(copyright),
                        ),
                )
            GlbFile(json, bin).write()
        }
            .getOrNull()

    private const val NODES = "nodes"
    private val MERGED_ARRAYS =
        listOf("accessors", "bufferViews", "images", "materials", "meshes", "samplers", "textures")
    private val TEXTURE_SLOTS =
        listOf("baseColorTexture", "metallicRoughnessTexture", "normalTexture", "occlusionTexture", "emissiveTexture")

    private fun JsonObject.arrayOf(key: String): List<JsonElement> = this[key]?.jsonArray.orEmpty()

    private fun GlbFile.isPlain(): Boolean {
        val unsupported =
            listOf("extensionsUsed", "skins", "animations", "cameras").any { json.arrayOf(it).isNotEmpty() }
        val morphs =
            json.arrayOf("meshes").any { mesh ->
                mesh.jsonObject.arrayOf("primitives").any { "targets" in it.jsonObject }
            }
        val external =
            json.arrayOf("images").any { "uri" in it.jsonObject } ||
                json.arrayOf("accessors").any { "sparse" in it.jsonObject }
        return hasOneEmbeddedBuffer() && !unsupported && !morphs && !external
    }

    private fun GlbFile.hasOneEmbeddedBuffer(): Boolean =
        json.arrayOf("buffers").singleOrNull()?.let { "uri" !in it.jsonObject } == true

    private fun GlbFile.rootNodes(): List<Int> {
        val scene = json["scene"]?.jsonPrimitive?.intOrNull ?: 0
        return json.arrayOf("scenes").getOrNull(scene)?.jsonObject?.arrayOf(NODES).orEmpty().map {
            it.jsonPrimitive.int
        }
    }

    private fun GlbFile.scenesWith(root: Int): JsonArray {
        val chosen = json["scene"]?.jsonPrimitive?.intOrNull ?: 0
        val scenes = json.arrayOf("scenes").ifEmpty { listOf(JsonObject(emptyMap())) }
        return JsonArray(
            scenes.mapIndexed { index, scene ->
                if (index != chosen) {
                    scene
                } else {
                    val nodes = scene.jsonObject.arrayOf(NODES) + JsonPrimitive(root)
                    JsonObject(scene.jsonObject + (NODES to JsonArray(nodes)))
                }
            },
        )
    }

    private fun GlbFile.assetWith(copyright: String?): JsonObject {
        val asset = json["asset"]?.jsonObject ?: JsonObject(mapOf("version" to JsonPrimitive("2.0")))
        return if (copyright == null) asset else JsonObject(asset + ("copyright" to JsonPrimitive(copyright)))
    }

    /** [added]'s arrays with every index into an array moved past [shift]'s count of the base's entries. */
    private fun GlbFile.remapped(shift: (String) -> Int, binOffset: Int): JsonObject {
        fun JsonObject.shifted(key: String, array: String): JsonObject =
            this[key]?.jsonPrimitive?.int?.let { JsonObject(this + (key to JsonPrimitive(it + shift(array)))) } ?: this

        fun list(key: String, transform: (JsonObject) -> JsonObject) =
            key to JsonArray(json.arrayOf(key).map { transform(it.jsonObject) })

        return JsonObject(
            mapOf(
                list("bufferViews") { view ->
                    val offset = (view["byteOffset"]?.jsonPrimitive?.int ?: 0) + binOffset
                    JsonObject(view + mapOf("buffer" to JsonPrimitive(0), "byteOffset" to JsonPrimitive(offset)))
                },
                list("accessors") { it.shifted("bufferView", "bufferViews") },
                list("images") { it.shifted("bufferView", "bufferViews") },
                list("samplers") { it },
                list("textures") { it.shifted("source", "images").shifted("sampler", "samplers") },
                list("materials") { it.withTextures { info -> info.shifted("index", "textures") } },
                list("meshes") { mesh ->
                    val primitives =
                        mesh.arrayOf("primitives").map { element ->
                            val primitive = element.jsonObject
                            val attributes =
                                primitive["attributes"]?.jsonObject.orEmpty().mapValues {
                                    JsonPrimitive(it.value.jsonPrimitive.int + shift("accessors"))
                                }
                            JsonObject(primitive + ("attributes" to JsonObject(attributes)))
                                .shifted("indices", "accessors")
                                .shifted("material", "materials")
                        }
                    JsonObject(mesh + ("primitives" to JsonArray(primitives)))
                },
                list(NODES) { node ->
                    val children = node.arrayOf("children").map { JsonPrimitive(it.jsonPrimitive.int + shift(NODES)) }
                    val moved = node.shifted("mesh", "meshes")
                    if (children.isEmpty()) moved else JsonObject(moved + ("children" to JsonArray(children)))
                },
            ),
        )
    }

    private fun JsonObject.withTextures(transform: (JsonObject) -> JsonObject): JsonObject {
        fun JsonObject.slots(): JsonObject =
            JsonObject(mapValues { (key, value) -> if (key in TEXTURE_SLOTS) transform(value.jsonObject) else value })
        val pbr = this["pbrMetallicRoughness"]?.jsonObject?.slots()
        val material = slots()
        return if (pbr == null) material else JsonObject(material + ("pbrMetallicRoughness" to pbr))
    }
}

/** GLB chunks start on four-byte boundaries. */
private const val CHUNK_ALIGN_MASK = 3

private fun Int.paddedTo4() = (this + CHUNK_ALIGN_MASK) and CHUNK_ALIGN_MASK.inv()

/** A GLB's JSON and its binary chunk, read and written whole. */
private class GlbFile(val json: JsonObject, val bin: ByteArray) {
    fun write(): ByteArray {
        val text = padded(Json.encodeToString(JsonObject.serializer(), json).encodeToByteArray(), SPACE)
        val data = padded(bin, 0)
        val total = HEADER_BYTES + CHUNK_HEADER_BYTES + text.size + CHUNK_HEADER_BYTES + data.size
        val out = ByteArray(total)
        var at = 0
        fun int(value: Int) {
            for (i in 0 until INT_BYTES) out[at + i] = (value ushr (BYTE_BITS * i)).toByte()
            at += INT_BYTES
        }
        fun bytes(value: ByteArray) {
            value.copyInto(out, at)
            at += value.size
        }
        int(GLB_MAGIC)
        int(GLB_VERSION)
        int(total)
        int(text.size)
        int(JSON_CHUNK)
        bytes(text)
        int(data.size)
        int(BIN_CHUNK)
        bytes(data)
        return out
    }

    companion object {
        private const val HEADER_BYTES = 12
        private const val CHUNK_HEADER_BYTES = 8
        private const val INT_BYTES = 4
        private const val BYTE_BITS = 8
        private const val GLB_MAGIC = 0x46546C67 // "glTF"
        private const val GLB_VERSION = 2
        private const val JSON_CHUNK = 0x4E4F534A // "JSON"
        private const val BIN_CHUNK = 0x004E4942 // "BIN\0"
        private const val SPACE: Byte = 0x20
        private val json = Json { ignoreUnknownKeys = true }

        private fun padded(bytes: ByteArray, fill: Byte): ByteArray {
            val size = bytes.size.paddedTo4()
            return if (size == bytes.size) bytes else bytes.copyOf(size).also { it.fill(fill, bytes.size) }
        }

        /** Null for anything but a GLB 2 with a JSON chunk and a binary chunk. */
        fun read(glb: ByteArray): GlbFile? {
            val textStart = HEADER_BYTES + CHUNK_HEADER_BYTES
            val isGlb =
                glb.size >= textStart && glb.readIntLe(0) == GLB_MAGIC && glb.readIntLe(INT_BYTES) == GLB_VERSION
            val textLength = if (isGlb) glb.readIntLe(HEADER_BYTES) else -1
            val binHeader = textStart + textLength
            val binStart = binHeader + CHUNK_HEADER_BYTES
            val readable =
                textLength in 0..glb.size &&
                    binStart <= glb.size &&
                    glb.readIntLe(HEADER_BYTES + INT_BYTES) == JSON_CHUNK &&
                    glb.readIntLe(binHeader + INT_BYTES) == BIN_CHUNK
            val binLength = if (readable) glb.readIntLe(binHeader) else -1
            return if (binLength in 0..glb.size - binStart) {
                GlbFile(
                    json.parseToJsonElement(glb.decodeToString(textStart, binHeader)).jsonObject,
                    glb.copyOfRange(binStart, binStart + binLength),
                )
            } else {
                null
            }
        }
    }
}
