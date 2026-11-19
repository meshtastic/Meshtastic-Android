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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import kotlinx.coroutines.test.runTest
import qrcode.QRCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrDecoderTest {

    @Test
    fun readsTheTextOfAQrCodeDrawnOnACanvas() = runTest {
        val text = "https://meshtastic.org/e/#CgMSAQESBggBOAFAAw"

        assertEquals(text, QrDecoder.create().decode(qrCanvas(text), cropToSquare = false))
    }

    @Test
    fun findsNothingOnABlankCanvas() = runTest {
        assertNull(QrDecoder.create().decode(blankCanvas(), cropToSquare = false))
    }

    private fun qrCanvas(text: String): JsAny {
        val matrix = QRCode.ofSquares().build(text).rawData
        val canvas = blankCanvas()
        matrix.forEachIndexed { row, squares ->
            squares.forEachIndexed { col, square -> if (square.dark) fillModule(canvas, row, col) }
        }
        return canvas
    }
}

private fun blankCanvas(): JsAny = js(
    """{
    const c = document.createElement('canvas');
    c.width = 400;
    c.height = 400;
    const ctx = c.getContext('2d');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, 400, 400);
    return c;
}""",
)

private fun fillModule(canvas: JsAny, row: Int, col: Int) {
    js(
        """{
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#000';
        ctx.fillRect((col + 4) * 8, (row + 4) * 8, 8, 8);
    }""",
    )
}
