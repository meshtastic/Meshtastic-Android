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
package org.meshtastic.core.ui.label

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.meshtastic.core.ble.niimbot.LabelBitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Draws with the bundled emoji font and the desktop's default text font, as the app does on a device. */
class LabelRasterizerTest {
    private val emojiFont =
        FontFamily(Font(File("../resources/src/commonMain/composeResources/font/NotoEmoji-Regular.ttf")))
    private val rasterizer =
        LabelRasterizer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), emojiFont)

    /** Ink in reading orientation, undoing the quarter turn onto the head. */
    private fun LabelBitmap.inkAt(x: Int, y: Int) = isInk(x = width - 1 - y, y = x)

    // design#185 item 7: text 2 dots inside the top and bottom of 12 mm stock and 3 from each end.
    @Test
    fun `text and the mark never print outside the margins even when a layout puts them there`() {
        val layout =
            LabelLayout(
                width = 120,
                height = 96,
                ops =
                listOf(
                    LabelOp.Text("MMMMMMMMMMMMMMMM", x = -4, yCenter = 4, size = 30),
                    LabelOp.Text("MMMMMMMMMMMMMMMM", x = 0, yCenter = 92, size = 30),
                    LabelOp.Band(x = 0, y = 40, width = 120, height = 22, text = "MMMM", size = 21),
                    LabelOp.Mark(right = 122, bottom = 97, height = 18),
                ),
            )

        val bitmap = assertNotNull(rasterizer.render(layout)).bitmap

        val outside =
            (0 until 120)
                .flatMap { x -> (0 until 96).map { y -> x to y } }
                .filter { (x, y) -> x < 3 || x >= 117 || y < 2 || y >= 94 }
        assertEquals(emptyList(), outside.filter { (x, y) -> bitmap.inkAt(x, y) })
        val inkedRows = (0 until 96).filter { y -> (3 until 117).any { x -> bitmap.inkAt(x, y) } }
        assertEquals(2 to 93, inkedRows.first() to inkedRows.last(), "the text itself is drawn up to the margin")
    }

    // design#185 item 4 and item 7: whole dots per module, and the QR may use the full head height.
    @Test
    fun `a QR module prints as a square of whole dots and may reach the edge of the head`() {
        val modules = QrModules(2, booleanArrayOf(true, false, false, true))
        val layout = LabelLayout(60, 96, listOf(LabelOp.Qr(modules, x = 5, y = 0, dotsPerModule = 3)))

        val bitmap = assertNotNull(rasterizer.render(layout)).bitmap

        val ink = (0 until 60).flatMap { x -> (0 until 96).map { y -> x to y } }.filter { (x, y) -> bitmap.inkAt(x, y) }
        val square = { left: Int, top: Int ->
            (left until left + 3).flatMap { x -> (top until top + 3).map { x to it } }
        }
        assertEquals((square(5, 0) + square(8, 3)).toSet(), ink.toSet())
    }
}
