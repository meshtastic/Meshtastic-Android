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
package org.meshtastic.feature.settings.util

import org.meshtastic.proto.FieldMetadata
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The inclusive range a proto field declares for presentation, or null when the schema leaves either bound open.
 * Fractional bounds round inward, so the integer range never admits a value the schema excludes.
 */
val FieldMetadata.intRange: IntRange?
    get() {
        val min = min_value ?: return null
        val max = max_value ?: return null
        val low = ceil(min)
        val high = floor(max)
        // Double.toInt() saturates, so a range that misses the Int domain would read as a single bogus value.
        if (high < Int.MIN_VALUE.toDouble() || low > Int.MAX_VALUE.toDouble()) return null
        return low.coerceAtLeast(Int.MIN_VALUE.toDouble()).toInt()..high.coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }
