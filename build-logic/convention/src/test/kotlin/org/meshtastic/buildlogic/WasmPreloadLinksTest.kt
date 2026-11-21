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
package org.meshtastic.buildlogic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WasmPreloadLinksTest {
    @Test
    fun `links each hashed wasm module the loader names once and in order`(@TempDir dir: File) {
        val loader =
            File(dir, "webApp.js").apply {
                writeText(
                    """a.p+"0123456789abcdef0123.wasm";b.p+"fedcba98765432100123.wasm";c("0123456789abcdef0123.wasm")""",
                )
            }

        assertEquals(
            listOf(
                """    <link rel="preload" as="fetch" type="application/wasm" crossorigin href="0123456789abcdef0123.wasm">""",
                """    <link rel="preload" as="fetch" type="application/wasm" crossorigin href="fedcba98765432100123.wasm">""",
            ),
            wasmPreloadLinks(setOf(loader)).lines(),
        )
    }

    @Test
    fun `a loader naming no hashed wasm leaves the line empty`(@TempDir dir: File) {
        val loader = File(dir, "webApp.js").apply { writeText("""fetch("sqlite3.wasm")""") }

        assertEquals("", wasmPreloadLinks(setOf(loader)))
    }
}
