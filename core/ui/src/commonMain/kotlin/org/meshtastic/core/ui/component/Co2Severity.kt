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

/**
 * CO₂ severity levels based on concentration thresholds (ppm).
 *
 * Thresholds per design/issues/53:
 * - Good: 0–1000 ppm
 * - Stuffy: 1000–2000 ppm
 * - Poor: 2000–5000 ppm
 * - Unsafe: 5000–30000 ppm
 * - Evacuate: 30000+ ppm
 */
@Suppress("MagicNumber")
enum class Co2Severity(val color: Color, val label: String) {
    GOOD(Color(0xFF4CAF50), "Good"),
    STUFFY(Color(0xFFFFC107), "Stuffy"),
    POOR(Color(0xFFFF9800), "Poor"),
    UNSAFE(Color(0xFFF44336), "Unsafe"),
    EVACUATE(Color(0xFFB71C1C), "Evacuate"),
    ;

    companion object {
        /** Returns the [Co2Severity] for the given [ppm] value, or null if ppm is 0 or negative. */
        fun fromPpm(ppm: Int): Co2Severity? = when {
            ppm <= 0 -> null
            ppm < 1000 -> GOOD
            ppm < 2000 -> STUFFY
            ppm < 5000 -> POOR
            ppm < 30000 -> UNSAFE
            else -> EVACUATE
        }
    }
}
