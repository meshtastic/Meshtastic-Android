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
package org.meshtastic.feature.node.model

import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsStateTest {

    @Test
    fun hasLocalStatsReturnsTrueWhenLocalStatsTelemetryExists() {
        val state = MetricsState(localStats = listOf(localStatsTelemetry(time = 123)))

        assertTrue(state.hasLocalStats())
    }

    @Test
    fun oldestTimestampIncludesLocalStatsTelemetry() {
        val state =
            MetricsState(
                deviceMetrics = listOf(Telemetry(time = 200)),
                localStats = listOf(localStatsTelemetry(time = 100)),
            )

        assertEquals(100L, state.oldestTimestampSeconds())
    }

    private fun localStatsTelemetry(time: Int) = Telemetry(time = time, local_stats = LocalStats(noise_floor = -101))
}
