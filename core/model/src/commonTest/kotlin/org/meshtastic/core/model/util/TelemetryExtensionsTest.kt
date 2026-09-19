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
package org.meshtastic.core.model.util

import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins which environment readings admit a telemetry row to the Environment log. */
class TelemetryExtensionsTest {

    private fun telemetry(build: EnvironmentMetrics.Builder.() -> Unit): Telemetry = Telemetry.Builder()
        .also { wb -> wb.environment_metrics = EnvironmentMetrics.Builder().also(build).build() }
        .build()

    @Test
    fun `no environment metrics is not valid`() {
        assertFalse(Telemetry.Builder().build().hasValidEnvironmentMetrics())
        assertFalse(telemetry {}.hasValidEnvironmentMetrics())
    }

    @Test
    fun `temperature and humidity together are valid`() {
        assertTrue(
            telemetry {
                temperature = 21.5f
                relative_humidity = 40f
            }
                .hasValidEnvironmentMetrics(),
        )
    }

    @Test
    fun `temperature alone or a NaN temperature is not valid`() {
        assertFalse(telemetry { temperature = 21.5f }.hasValidEnvironmentMetrics())
        assertFalse(
            telemetry {
                temperature = Float.NaN
                relative_humidity = 40f
            }
                .hasValidEnvironmentMetrics(),
        )
    }

    /** An AS3935 on its own reports only lightning; 0 strikes is a reading. */
    @Test
    fun `lightning alone is valid`() {
        assertTrue(telemetry { lightning_strike_count_1h = 0 }.hasValidEnvironmentMetrics())
        assertTrue(telemetry { lightning_distance_km = 12f }.hasValidEnvironmentMetrics())
        assertFalse(telemetry { lightning_distance_km = Float.NaN }.hasValidEnvironmentMetrics())
    }
}
