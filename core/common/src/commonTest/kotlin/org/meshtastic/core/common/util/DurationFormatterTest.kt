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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.DurationUnit

class DurationFormatterTest {
    private val english = DurationUnitLabels.English

    @Test
    fun `every non-zero part is shown largest first`() {
        assertEquals("1d 2h 3m 4s", formatDuration(93_784, english))
    }

    @Test
    fun `zero parts in the middle are skipped`() {
        assertEquals("1h 5s", formatDuration(3_605, english))
    }

    @Test
    fun `zero prints zero of the smallest unit`() {
        assertEquals("0s", formatDuration(0, english))
        assertEquals("0m", formatDuration(59, english, smallest = DurationUnit.MINUTES))
    }

    @Test
    fun `parts finer than the smallest unit are dropped`() {
        assertEquals("1d 1h", formatDuration(90_059, english, smallest = DurationUnit.MINUTES))
        assertEquals("25m", formatDuration(1_530, english, smallest = DurationUnit.MINUTES))
    }

    @Test
    fun `negative input counts as zero`() {
        assertEquals("0s", formatDuration(-5, english))
    }

    @Test
    fun `labels are filled per unit`() {
        val labels = DurationUnitLabels(days = "%1\$d j", hours = "%1\$d h", minutes = "%1\$d min", seconds = "%1\$d s")

        assertEquals("2 j 3 min", formatDuration(2 * 86_400L + 180, labels))
    }
}
