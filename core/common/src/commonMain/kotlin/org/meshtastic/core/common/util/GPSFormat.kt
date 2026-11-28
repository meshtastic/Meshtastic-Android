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
package org.meshtastic.core.common.util

import kotlin.math.pow

@Suppress("MagicNumber")
object GPSFormat {
    fun toDec(latitude: Double, longitude: Double): String {
        // Simple decimal formatting for KMP
        fun Double.format(digits: Int): String {
            val multiplier = 10.0.pow(digits)
            val rounded = (this * multiplier).toLong() / multiplier
            return rounded.toString()
        }
        return "${latitude.format(5)}, ${longitude.format(5)}"
    }
}
