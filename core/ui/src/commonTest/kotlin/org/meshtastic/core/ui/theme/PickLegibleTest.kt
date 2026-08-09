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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PickLegibleTest {

    private val fallback = Color(0xFF888888)

    // DEF CON 34's published palette, in authored order.
    private val defconNavy = Color(0xFF0D294A)
    private val defconTeal = Color(0xFF017FA4)
    private val defconPink = Color(0xFFE0004E)
    private val defconMint = Color(0xFF6CCDB8)

    @Test
    fun picksFirstCandidateThatClearsTheThreshold() {
        // On a dark surface the navy primary is invisible, so the first legible color wins instead.
        val onDark = pickLegible(listOf(defconNavy, defconPink, defconTeal), background = Color.Black, fallback)
        assertEquals(defconPink, onDark)
        assertTrue(contrastRatio(onDark, Color.Black) >= MIN_GRAPHICAL_CONTRAST)
    }

    @Test
    fun picksDifferentColorsForLightAndDarkBackgrounds() {
        // Same palette, opposite surfaces: mint reads on black but not on white; navy is the reverse.
        assertEquals(defconMint, pickLegible(listOf(defconMint, defconNavy), background = Color.Black, fallback))
        assertEquals(defconNavy, pickLegible(listOf(defconMint, defconNavy), background = Color.White, fallback))
    }

    @Test
    fun fallsBackWhenNoCandidateReads() {
        assertEquals(fallback, pickLegible(listOf(Color.White), background = Color.White, fallback))
        assertEquals(fallback, pickLegible(emptyList(), background = Color.Black, fallback))
    }

    @Test
    fun honoursACustomMinimumRatio() {
        // Pink on black (~4.3:1) clears the graphical bar but not the stricter text bar.
        assertEquals(defconPink, pickLegible(listOf(defconPink), Color.Black, fallback))
        assertEquals(fallback, pickLegible(listOf(defconPink), Color.Black, fallback, minRatio = MIN_TEXT_CONTRAST))
    }
}
