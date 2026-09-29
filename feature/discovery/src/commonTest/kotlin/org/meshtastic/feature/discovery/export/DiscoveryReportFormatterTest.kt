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
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import kotlin.test.Test
import kotlin.test.assertTrue

class DiscoveryReportFormatterTest {

    @Test
    fun nodeLineMarksAnUnheardNodeSnrAsUnknown() {
        // A node named only by NeighborInfo is stored with the entity's default SNR.
        val line = DiscoveryReportFormatter.formatNodeLine(node(), MeasurementSystem.METRIC)

        assertTrue("SNR: ${MetricFormatter.snr(null)} |" in line, line)
    }

    @Test
    fun nodeLinePrintsAZeroDbSnrAsAReading() {
        val line = DiscoveryReportFormatter.formatNodeLine(node().copy(snr = 0f), MeasurementSystem.METRIC)

        assertTrue("SNR: ${MetricFormatter.snr(0f)} |" in line, line)
    }

    private fun node() = DiscoveredNodeEntity(presetResultId = 1L, nodeNum = 0x1234L, longName = "Relay")
}
