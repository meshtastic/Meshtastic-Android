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

import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentMetricsStateTest {

    @Test
    fun environmentMetricsForGraphing_correctly_calculates_times() {
        val now = nowSeconds.toInt()
        val metrics =
            listOf(
                Telemetry(time = now - 100, environment_metrics = EnvironmentMetrics(temperature = 20f)),
                Telemetry(time = now - 50, environment_metrics = EnvironmentMetrics(temperature = 22f)),
                Telemetry(time = now, environment_metrics = EnvironmentMetrics(temperature = 21f)),
            )
        val state = EnvironmentMetricsState(metrics)
        val result = state.environmentMetricsForGraphing()

        assertEquals(now - 100, result.times.first)
        assertEquals(now, result.times.second)
    }

    @Test
    fun environmentMetricsForGraphing_handles_valid_zero_temperatures() {
        val now = nowSeconds.toInt()
        val metrics = listOf(Telemetry(time = now, environment_metrics = EnvironmentMetrics(temperature = 0.0f)))
        val state = EnvironmentMetricsState(metrics)
        val result = state.environmentMetricsForGraphing()

        assertTrue(result.shouldPlot[Environment.TEMPERATURE.ordinal])
        assertEquals(0.0f, result.rightMinMax.first, 0.01f)
        assertEquals(0.0f, result.rightMinMax.second, 0.01f)
    }
}
