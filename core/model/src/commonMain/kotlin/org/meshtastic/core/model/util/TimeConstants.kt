/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Common time-related constants. */
object TimeConstants {
    val ONE_HOUR = 1.hours
    val EIGHT_HOURS = 8.hours
    val ONE_DAY = 1.days
    val TWO_DAYS = 2.days

    const val HOURS_PER_DAY = 24
}
