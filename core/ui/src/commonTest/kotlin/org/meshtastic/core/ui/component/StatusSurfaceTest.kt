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
package org.meshtastic.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.meshtastic.core.ui.theme.MIN_GRAPHICAL_CONTRAST
import org.meshtastic.core.ui.theme.MIN_TEXT_CONTRAST
import org.meshtastic.core.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

class StatusSurfaceTest {

    // The actual StatusColors token values (kept in sync with CustomColors.StatusColors).
    private val green = Color(0xFF3FB86D)
    private val statusColors =
        mapOf(
            "green" to green,
            "yellow" to Color(0xFFE8A33E),
            "orange" to Color(0xFFFF8800),
            "red" to Color(0xFFE05252),
        )

    // StatusScrim is translucent, so test the effective color over the lightest surface it can sit on (worst case).
    private val effectiveScrim = StatusScrim.compositeOver(Color.White)

    @Test
    fun goodGreenMeetsTextContrast() {
        // Green carries the common cases (signed shield, good signal) and must clear AA text contrast.
        assertTrue(
            contrastRatio(green, effectiveScrim) >= MIN_TEXT_CONTRAST,
            "green must meet AA text on the scrim (got ${contrastRatio(green, effectiveScrim)})",
        )
    }

    @Test
    fun everyStatusColorClearsGraphicalContrast() {
        // Translucency trades some headroom: the darkest token (red) holds the 3:1 graphical floor, not full text AA.
        for ((name, color) in statusColors) {
            val ratio = contrastRatio(color, effectiveScrim)
            assertTrue(ratio >= MIN_GRAPHICAL_CONTRAST, "status '$name' must clear 3:1 on the scrim (got $ratio)")
        }
    }
}
