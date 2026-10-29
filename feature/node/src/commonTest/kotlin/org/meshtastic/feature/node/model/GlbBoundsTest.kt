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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GlbBoundsTest {

    @Test
    fun unionsEveryPrimitivesPositionAccessor() {
        val glb =
            glb(
                """
                {"accessors":[
                  {"min":[-0.0275,0,-0.00325],"max":[0.0275,0.0858,0.00325]},
                  {"min":[0.01,0.08,-0.009],"max":[0.026,0.0935,0.009]},
                  {"min":[-9,-9,-9],"max":[9,9,9]}
                ],
                "meshes":[
                  {"primitives":[{"attributes":{"POSITION":0,"NORMAL":2}}]},
                  {"primitives":[{"attributes":{"POSITION":1}}]}
                ]}
                """,
            )

        val bounds = assertNotNull(GlbBounds.parse(glb))

        assertContentEquals(floatArrayOf(-0.0275f, 0f, -0.009f), bounds.min)
        assertContentEquals(floatArrayOf(0.0275f, 0.0935f, 0.009f), bounds.max)
        assertEquals(0.0935f, bounds.extent)
        assertContentEquals(floatArrayOf(0f, 0.04675f, 0f), bounds.center)
    }

    @Test
    fun readsTheModelsOwnCopyrightNotice() {
        val glb = glb("""{"asset":{"version":"2.0","copyright":"Copyright © 2026 Meshtastic LLC."}}""")

        assertEquals("Copyright © 2026 Meshtastic LLC.", GlbBounds.copyright(glb))
    }

    @Test
    fun noCopyrightWhenTheModelCarriesNone() {
        assertNull(GlbBounds.copyright(glb("""{"asset":{"version":"2.0"}}""")))
        assertNull(GlbBounds.copyright("not a model".encodeToByteArray()))
    }

    @Test
    fun screenIsTheMeshOrMaterialNamedScreen() {
        val glb =
            glb(
                """
                {"accessors":[
                  {"min":[-0.026,0,-0.015],"max":[0.026,0.138,0.017]},
                  {"min":[-0.015,0.032,0.0158],"max":[0.015,0.047,0.016]},
                  {"min":[0.02,0.1,0],"max":[0.021,0.11,0.001]}
                ],
                "materials":[{"name":"case"},{"name":"Screen"}],
                "meshes":[
                  {"name":"Body","primitives":[{"attributes":{"POSITION":0},"material":0}]},
                  {"name":"Glass","primitives":[{"attributes":{"POSITION":1},"material":1}]},
                  {"name":"screen","primitives":[{"attributes":{"POSITION":2},"material":0}]}
                ]}
                """,
            )

        val screen = assertNotNull(GlbBounds.screen(glb))

        assertContentEquals(floatArrayOf(-0.015f, 0.032f, 0f), screen.min)
        assertContentEquals(floatArrayOf(0.021f, 0.11f, 0.016f), screen.max)
    }

    @Test
    fun displayNamesTheScreenWhenNothingIsNamedScreen() {
        val glb =
            glb(
                """
                {"accessors":[
                  {"min":[-0.026,0,-0.015],"max":[0.026,0.138,0.017]},
                  {"min":[-0.015,0.032,0.0158],"max":[0.015,0.047,0.016]},
                  {"min":[-0.012,0.035,0.016],"max":[0.012,0.045,0.0162]}
                ],
                "materials":[{"name":"c_0c0c0e"}],
                "meshes":[
                  {"name":"Body","primitives":[{"attributes":{"POSITION":0},"material":0}]},
                  {"name":"Glass","primitives":[{"attributes":{"POSITION":1},"material":0}]},
                  {"name":"Display","primitives":[{"attributes":{"POSITION":2},"material":0}]}
                ]}
                """,
            )

        val screen = assertNotNull(GlbBounds.screen(glb))

        assertContentEquals(floatArrayOf(-0.012f, 0.035f, 0.016f), screen.min)
        assertContentEquals(floatArrayOf(0.012f, 0.045f, 0.0162f), screen.max)
    }

    @Test
    fun screenOutranksDisplayWhenAModelNamesBoth() {
        val glb =
            glb(
                """
                {"accessors":[
                  {"min":[0,0,0],"max":[0.05,0.05,0.003]},
                  {"min":[0.01,0.01,0.003],"max":[0.04,0.04,0.0032]}
                ],
                "meshes":[
                  {"name":"Display","primitives":[{"attributes":{"POSITION":0}}]},
                  {"name":"Screen","primitives":[{"attributes":{"POSITION":1}}]}
                ]}
                """,
            )

        val screen = assertNotNull(GlbBounds.screen(glb))

        assertContentEquals(floatArrayOf(0.01f, 0.01f, 0.003f), screen.min)
        assertContentEquals(floatArrayOf(0.04f, 0.04f, 0.0032f), screen.max)
    }

    @Test
    fun screenFrontRisesToTheFaceDrawnOverIt() {
        // An indexed display quad topping out at z 0.0035, an unindexed face quad at 0.0038 that covers its centre,
        // and a taller ridge beside the glass that must not count.
        val vertices =
            floats(
                0f,
                0f,
                0.0035f,
                0.01f,
                0f,
                0.0035f,
                0.01f,
                0.005f,
                0.0035f,
                0f,
                0.005f,
                0.0035f,
                -0.02f,
                -0.02f,
                0.0038f,
                0.02f,
                -0.02f,
                0.0038f,
                0.02f,
                0.02f,
                0.0038f,
                -0.02f,
                -0.02f,
                0.0038f,
                0.02f,
                0.02f,
                0.0038f,
                -0.02f,
                0.02f,
                0.0038f,
                0.02f,
                0f,
                0.009f,
                0.03f,
                0f,
                0.009f,
                0.03f,
                0.005f,
                0.009f,
            )
        val glb =
            glb(
                """
                {"accessors":[
                  {"bufferView":0,"byteOffset":0,"componentType":5126,"count":4,"type":"VEC3",
                   "min":[0,0,0.0035],"max":[0.01,0.005,0.0035]},
                  {"bufferView":1,"componentType":5123,"count":6,"type":"SCALAR"},
                  {"bufferView":0,"byteOffset":48,"componentType":5126,"count":6,"type":"VEC3",
                   "min":[-0.02,-0.02,0.0038],"max":[0.02,0.02,0.0038]},
                  {"bufferView":0,"byteOffset":120,"componentType":5126,"count":3,"type":"VEC3",
                   "min":[0.02,0,0.009],"max":[0.03,0.005,0.009]}
                ],
                "bufferViews":[
                  {"buffer":0,"byteOffset":0,"byteLength":${vertices.size}},
                  {"buffer":0,"byteOffset":${vertices.size},"byteLength":12}
                ],
                "meshes":[
                  {"name":"Display","primitives":[{"attributes":{"POSITION":0},"indices":1}]},
                  {"name":"Face_2b2b2b","primitives":[{"attributes":{"POSITION":2}}]},
                  {"name":"Ridge","primitives":[{"attributes":{"POSITION":3}}]}
                ]}
                """,
                bin = vertices + u16(0, 1, 2, 0, 2, 3),
            )

        val screen = assertNotNull(GlbBounds.screen(glb))

        assertContentEquals(floatArrayOf(0f, 0f, 0.0035f), screen.min)
        assertContentEquals(floatArrayOf(0.01f, 0.005f, 0.0038f), screen.max)
    }

    @Test
    fun noScreenWhenNothingIsNamedScreen() {
        val glb =
            glb(
                """
                {"accessors":[{"min":[0,0,0],"max":[1,1,1]}],
                "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}]}
                """,
            )

        assertNull(GlbBounds.screen(glb))
    }

    @Test
    fun rejectsNonGlbBytes() {
        assertNull(GlbBounds.parse("not a model".encodeToByteArray()))
        assertNull(GlbBounds.parse(ByteArray(4)))
    }

    @Test
    fun rejectsJsonChunkLengthPastTheEnd() {
        val model = glb("""{"asset":{"version":"2.0"}}""")
        for (length in listOf(model.size, Int.MAX_VALUE - 10, Int.MAX_VALUE)) {
            val bad = model.copyOf().apply { putIntLe(12, length) }
            assertNull(GlbBounds.parse(bad))
            assertNull(GlbBounds.copyright(bad))
        }
    }

    @Test
    fun rejectsModelWithoutPositions() {
        assertNull(GlbBounds.parse(glb("""{"accessors":[],"meshes":[{"primitives":[{"attributes":{}}]}]}""")))
    }

    /** A GLB with the JSON chunk padded to four bytes as the format requires, and an optional BIN chunk after it. */
    private fun glb(json: String, bin: ByteArray? = null): ByteArray {
        val text = json.trimIndent().encodeToByteArray()
        val body = text + ByteArray((4 - text.size % 4) % 4) { ' '.code.toByte() }
        val binChunk =
            bin?.let {
                val chunkHeader = ByteArray(8)
                chunkHeader.putIntLe(0, it.size)
                chunkHeader.putIntLe(4, 0x004E4942)
                chunkHeader + it
            } ?: ByteArray(0)
        val header = ByteArray(20)
        header.putIntLe(0, 0x46546C67)
        header.putIntLe(4, 2)
        header.putIntLe(8, header.size + body.size + binChunk.size)
        header.putIntLe(12, body.size)
        header.putIntLe(16, 0x4E4F534A)
        return header + body + binChunk
    }

    private fun floats(vararg values: Float): ByteArray =
        ByteArray(values.size * 4).also { bytes -> values.forEachIndexed { i, v -> bytes.putIntLe(i * 4, v.toBits()) } }

    private fun u16(vararg values: Int): ByteArray = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { i, v ->
            bytes[i * 2] = v.toByte()
            bytes[i * 2 + 1] = (v shr 8).toByte()
        }
    }

    private fun ByteArray.putIntLe(offset: Int, value: Int) {
        for (i in 0 until 4) this[offset + i] = (value shr (8 * i)).toByte()
    }
}
