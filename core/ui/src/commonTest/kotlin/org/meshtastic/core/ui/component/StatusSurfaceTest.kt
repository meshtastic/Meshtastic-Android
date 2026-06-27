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
import org.meshtastic.core.ui.theme.MIN_TEXT_CONTRAST
import org.meshtastic.core.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

class StatusSurfaceTest {

    // The actual StatusColors token values (kept in sync with CustomColors.StatusColors).
    private val statusColors =
        mapOf(
            "green" to Color(0xFF3FB86D),
            "yellow" to Color(0xFFE8A33E),
            "orange" to Color(0xFFFF8800),
            "red" to Color(0xFFE05252),
        )

    @Test
    fun everyStatusColorMeetsAaOnTheScrim() {
        for ((name, color) in statusColors) {
            val ratio = contrastRatio(color, StatusScrim)
            assertTrue(ratio >= MIN_TEXT_CONTRAST, "status '$name' must meet AA on StatusScrim (got $ratio)")
        }
    }
}
