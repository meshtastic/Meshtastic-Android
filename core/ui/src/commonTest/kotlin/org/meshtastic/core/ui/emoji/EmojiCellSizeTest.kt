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
package org.meshtastic.core.ui.emoji

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the emoji-picker grid against clipping its glyphs at large system font scales (meshtastic/design#86).
 *
 * The picker's own rendering is not asserted here: color emoji glyphs come from the host's emoji font, so a screenshot
 * golden of them is not reproducible between a dev machine and CI. The sizing rule is, so that is what is pinned.
 */
class EmojiCellSizeTest {

    /** 24.sp emoji at the font scales Android exposes, in dp. */
    private val glyphAtScale = mapOf(1.0f to 24.dp, 1.15f to 27.6.dp, 1.3f to 31.2.dp, 1.5f to 36.dp, 2.0f to 48.dp)

    @Test
    fun `cell always fits the glyph`() {
        glyphAtScale.forEach { (scale, glyph) ->
            val cell = emojiCellSizeFor(glyph)
            assertTrue(cell >= glyph, "at fontScale $scale a $glyph glyph would be clipped by a $cell cell")
        }
    }

    @Test
    fun `cell never drops below the minimum touch target`() {
        glyphAtScale.values.forEach { glyph -> assertTrue(emojiCellSizeFor(glyph) >= 44.dp) }
    }

    @Test
    fun `default font scale keeps the original cell size`() {
        assertEquals(44.dp, emojiCellSizeFor(24.dp))
    }

    @Test
    fun `cell grows once the glyph outgrows the minimum`() {
        assertTrue(emojiCellSizeFor(48.dp) > emojiCellSizeFor(24.dp))
    }
}
