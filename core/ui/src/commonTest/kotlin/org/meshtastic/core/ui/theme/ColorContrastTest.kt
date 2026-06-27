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
package org.meshtastic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorContrastTest {

    private val statusGreen = Color(0xFF3FB86D) // Green 600 — the shared "good" token
    private val periwinkleBubble = Color(0xFF8D8DCC) // representative light node-identity bubble

    @Test
    fun raisesContrastOnLowContrastBackground() {
        // Green-on-periwinkle is the reported readability problem.
        assertTrue(contrastRatio(statusGreen, periwinkleBubble) < MIN_TEXT_CONTRAST, "fixture should start below AA")
        val adjusted = statusGreen.ensureContrastOn(periwinkleBubble)
        assertTrue(
            contrastRatio(adjusted, periwinkleBubble) >= MIN_TEXT_CONTRAST,
            "adjusted color must meet AA against the bubble",
        )
    }

    @Test
    fun darkensRatherThanWashesOutOnMidToneBackground() {
        // A mid-tone bubble affords more contrast by darkening, so the green must stay recognizably green, not go
        // white.
        val adjusted = statusGreen.ensureContrastOn(periwinkleBubble)
        assertTrue(adjusted.green > adjusted.red && adjusted.green > adjusted.blue, "should remain green-dominant")
        assertTrue(adjusted.luminance() < periwinkleBubble.luminance(), "should darken on a mid-tone bg")
    }

    @Test
    fun leavesAlreadyContrastingColorUnchanged() {
        val onDark = statusGreen.ensureContrastOn(Color.Black)
        assertEquals(statusGreen, onDark, "already-contrasting color must be returned untouched")
    }
}
