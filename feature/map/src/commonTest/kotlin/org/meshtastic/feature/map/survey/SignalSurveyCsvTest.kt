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
package org.meshtastic.feature.map.survey

import org.meshtastic.core.model.SignalSample
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalSurveyCsvTest {

    @Test
    fun `each reading is one row under the header`() {
        val readings =
            listOf(
                SignalSample(1000, 30.5, -95.25, 5f, 0x1234abcd, -101, 4.5f, direct = true),
                SignalSample(2000, 30.5, -95.25, null, 0xabcd, -120, null, direct = false),
            )

        val lines = readings.toSurveyCsv().trim().lines()

        assertEquals(SURVEY_CSV_HEADER, lines[0])
        assertEquals("1000,30.5,-95.25,5.0,!1234abcd,-101,4.5,true", lines[1])
        assertEquals("2000,30.5,-95.25,,!0000abcd,-120,,false", lines[2])
    }

    @Test
    fun `a node number above the signed range keeps its hex id`() {
        val reading = SignalSample(0, 0.0, 0.0, null, 0xdeadbeef.toInt(), -100, null, direct = true)

        assertEquals("!deadbeef", reading.let { listOf(it).toSurveyCsv().lines()[1].split(",")[4] })
    }
}
