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
package org.meshtastic.feature.node.metrics

import org.meshtastic.proto.HostMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class HostMetricsTest {

    private fun telemetry(time: Int, hostMetrics: HostMetrics? = null) =
        Telemetry(time = time, host_metrics = hostMetrics)

    @Test
    fun buildHostMetricsChartData_filters_missing_and_non_positive_values() {
        val chartData =
            buildHostMetricsChartData(
                listOf(
                    telemetry(
                        time = 100,
                        hostMetrics = HostMetrics(load1 = 150, load5 = 0, load15 = 225, freemem_bytes = 2_097_152L),
                    ),
                    telemetry(time = 200, hostMetrics = HostMetrics(load1 = 0, load5 = 320, freemem_bytes = 0L)),
                    telemetry(time = 300, hostMetrics = null),
                ),
            )

        assertTrue(chartData.hasLoad)
        assertEquals(listOf(HostMetricsChartPoint(time = 100, value = 1.5)), chartData.load1)
        assertEquals(listOf(HostMetricsChartPoint(time = 200, value = 3.2)), chartData.load5)
        assertEquals(listOf(HostMetricsChartPoint(time = 100, value = 2.25)), chartData.load15)
        assertEquals(listOf(HostMetricsChartPoint(time = 100, value = 2.0)), chartData.freeMemoryMb)
    }

    @Test
    fun buildHostMetricsChartData_returns_empty_series_when_no_plottable_metrics_exist() {
        val chartData =
            buildHostMetricsChartData(
                listOf(
                    telemetry(
                        time = 100,
                        hostMetrics = HostMetrics(load1 = 0, load5 = 0, load15 = 0, freemem_bytes = 0L),
                    ),
                    telemetry(time = 200, hostMetrics = HostMetrics()),
                    telemetry(time = 300, hostMetrics = null),
                ),
            )

        assertFalse(chartData.hasLoad)
        assertTrue(chartData.load1.isEmpty())
        assertTrue(chartData.load5.isEmpty())
        assertTrue(chartData.load15.isEmpty())
        assertTrue(chartData.freeMemoryMb.isEmpty())
    }
}
