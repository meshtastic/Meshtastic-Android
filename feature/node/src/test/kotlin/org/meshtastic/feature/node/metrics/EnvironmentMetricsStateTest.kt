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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry

class EnvironmentMetricsStateTest {

    @Test
    fun `environmentMetricsFiltered correctly calculates times`() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val metrics =
            listOf(
                Telemetry(time = now - 100, environment_metrics = EnvironmentMetrics(temperature = 20f)),
                Telemetry(time = now - 50, environment_metrics = EnvironmentMetrics(temperature = 22f)),
                Telemetry(time = now, environment_metrics = EnvironmentMetrics(temperature = 21f)),
            )
        val state = EnvironmentMetricsState(metrics)
        val result = state.environmentMetricsFiltered(TimeFrame.TWENTY_FOUR_HOURS)

        assertEquals(now - 100, result.times.first)
        assertEquals(now, result.times.second)
    }

    @Test
    fun `environmentMetricsFiltered ignores invalid timestamps`() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val metrics =
            listOf(
                Telemetry(time = 0, environment_metrics = EnvironmentMetrics(temperature = 20f)),
                Telemetry(time = now, environment_metrics = EnvironmentMetrics(temperature = 21f)),
            )
        val state = EnvironmentMetricsState(metrics)
        val result = state.environmentMetricsFiltered(TimeFrame.TWENTY_FOUR_HOURS)

        // Only the valid timestamp should be considered for filters
        assertEquals(now, result.times.first)
        assertEquals(now, result.times.second)
        assertEquals(1, result.metrics.size)
    }

    @Test
    fun `environmentMetricsFiltered handles valid zero temperatures`() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val metrics = listOf(Telemetry(time = now, environment_metrics = EnvironmentMetrics(temperature = 0.0f)))
        val state = EnvironmentMetricsState(metrics)
        val result = state.environmentMetricsFiltered(TimeFrame.TWENTY_FOUR_HOURS)

        assertTrue(result.shouldPlot[Environment.TEMPERATURE.ordinal])
        assertEquals(0.0f, result.rightMinMax.first, 0.01f)
        assertEquals(0.0f, result.rightMinMax.second, 0.01f)
    }
}
