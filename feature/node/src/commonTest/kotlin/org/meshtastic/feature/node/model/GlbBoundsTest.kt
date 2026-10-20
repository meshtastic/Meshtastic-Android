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

    private fun glb(json: String): ByteArray {
        val body = json.trimIndent().encodeToByteArray()
        val header = ByteArray(20)
        header.putIntLe(0, 0x46546C67)
        header.putIntLe(4, 2)
        header.putIntLe(8, header.size + body.size)
        header.putIntLe(12, body.size)
        header.putIntLe(16, 0x4E4F534A)
        return header + body
    }

    private fun ByteArray.putIntLe(offset: Int, value: Int) {
        for (i in 0 until 4) this[offset + i] = (value shr (8 * i)).toByte()
    }
}
