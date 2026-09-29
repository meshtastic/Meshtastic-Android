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
package org.meshtastic.feature.discovery.export

import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import kotlin.test.Test
import kotlin.test.assertTrue

class DiscoveryReportNodeIdTest {

    @Test
    fun `an unnamed node is listed by its padded node id`() {
        val line =
            DiscoveryReportFormatter.formatNodeLine(
                DiscoveredNodeEntity(presetResultId = 1L, nodeNum = 0x1234L),
                MeasurementSystem.METRIC,
            )

        assertTrue(line.startsWith("!00001234 |"), line)
    }

    @Test
    fun `a node number above the signed range keeps its unsigned id`() {
        val line =
            DiscoveryReportFormatter.formatNodeLine(
                DiscoveredNodeEntity(presetResultId = 1L, nodeNum = 0xFFFF_FFFEL),
                MeasurementSystem.METRIC,
            )

        assertTrue(line.startsWith("!fffffffe |"), line)
    }
}
