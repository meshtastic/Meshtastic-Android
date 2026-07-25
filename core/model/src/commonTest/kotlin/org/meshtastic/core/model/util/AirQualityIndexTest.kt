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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SECONDS_PER_HOUR = 3600L
private const val NOW = 1_000_000L
private const val EPSILON = 0.001

class AirQualityIndexTest {

    // Current (2024) EPA PM2.5 24-hour breakpoint table reference values (concentration -> AQI).
    @Test
    fun pm25ToAqi_matches_epa_breakpoint_table() {
        assertEquals(0, AirQualityIndex.pm25ToAqi(0.0))
        assertEquals(50, AirQualityIndex.pm25ToAqi(9.0))
        assertEquals(51, AirQualityIndex.pm25ToAqi(9.1))
        assertEquals(100, AirQualityIndex.pm25ToAqi(35.4))
        assertEquals(101, AirQualityIndex.pm25ToAqi(35.5))
        assertEquals(150, AirQualityIndex.pm25ToAqi(55.4))
        assertEquals(151, AirQualityIndex.pm25ToAqi(55.5))
        assertEquals(200, AirQualityIndex.pm25ToAqi(125.4))
        assertEquals(201, AirQualityIndex.pm25ToAqi(125.5))
        assertEquals(300, AirQualityIndex.pm25ToAqi(225.4))
        assertEquals(301, AirQualityIndex.pm25ToAqi(225.5))
        assertEquals(500, AirQualityIndex.pm25ToAqi(325.4))
    }

    // Regression for meshtastic/design#54: PM2.5 = 10 µg/m³ was "42, Good" on the legacy table;
    // on the 2024 table it must be "53, Moderate", matching iOS (meshtastic/Meshtastic-Apple#2075).
    @Test
    fun pm25ToAqi_uses_2024_breakpoints_for_low_concentrations() {
        assertEquals(53, AirQualityIndex.pm25ToAqi(10.0))
    }

    @Test
    fun pm25ToAqi_clamps_negative_and_above_scale_concentrations() {
        assertEquals(0, AirQualityIndex.pm25ToAqi(-5.0))
        assertEquals(500, AirQualityIndex.pm25ToAqi(1000.0))
    }

    @Test
    fun computeNowCastPm25_returns_null_when_most_recent_hour_missing() {
        // Only hour 1 (an hour ago) has data - EPA requires the most recent hour (c1) to be present.
        val readings = listOf(NOW - SECONDS_PER_HOUR to 20.0)
        assertNull(AirQualityIndex.computeNowCastPm25(readings, NOW))
    }

    @Test
    fun computeNowCastPm25_returns_null_with_fewer_than_two_valid_hours() {
        val readings = listOf(NOW to 20.0)
        assertNull(AirQualityIndex.computeNowCastPm25(readings, NOW))
    }

    @Test
    fun computeNowCastPm25_returns_null_when_second_valid_hour_is_outside_recent_three() {
        // Two valid hours in the 12h window (now + 11h ago), but only one within the most recent 3 hours.
        // EPA requires 2 of the 3 most recent hours, so this must not report a value.
        val readings = listOf(NOW to 20.0, NOW - 11 * SECONDS_PER_HOUR to 20.0)
        assertNull(AirQualityIndex.computeNowCastPm25(readings, NOW))
    }

    @Test
    fun computeNowCastPm25_averages_stable_readings_with_weight_factor_one() {
        // No variation across hours -> weight factor stays at 1, so NowCast is a plain average.
        val readings = listOf(NOW to 20.0, NOW - SECONDS_PER_HOUR to 20.0, NOW - 2 * SECONDS_PER_HOUR to 20.0)
        val result = AirQualityIndex.computeNowCastPm25(readings, NOW)
        assertEquals(20.0, result!!, EPSILON)
    }

    @Test
    fun computeNowCastPm25_weights_recent_hours_more_heavily_when_declining() {
        // c1=20 (now), c2=10 (1h ago). weightFactor = 1 - (20-10)/20 = 0.5 (also the EPA floor).
        // NowCast = (20*1 + 10*0.5) / (1 + 0.5) = 25 / 1.5
        val readings = listOf(NOW to 20.0, NOW - SECONDS_PER_HOUR to 10.0)
        val result = AirQualityIndex.computeNowCastPm25(readings, NOW)
        assertEquals(25.0 / 1.5, result!!, EPSILON)
    }

    @Test
    fun computeNowCastPm25_applies_minimum_weight_factor_floor() {
        // Range far exceeds max, so the raw weight factor would go deeply negative - it must floor at 0.5.
        val readings = listOf(NOW to 100.0, NOW - SECONDS_PER_HOUR to 1.0)
        val result = AirQualityIndex.computeNowCastPm25(readings, NOW)
        // NowCast = (100*1 + 1*0.5) / 1.5
        assertEquals(100.5 / 1.5, result!!, EPSILON)
    }

    @Test
    fun computeNowCastPm25_averages_multiple_readings_within_the_same_hour() {
        val readings =
            listOf(
                NOW to 10.0,
                NOW - 60 to 30.0, // same hour bucket as above -> averages to 20.0
                NOW - SECONDS_PER_HOUR to 20.0,
            )
        val result = AirQualityIndex.computeNowCastPm25(readings, NOW)
        // Both hourly buckets average to 20.0 -> stable, so NowCast is 20.0 regardless of weighting.
        assertEquals(20.0, result!!, EPSILON)
    }

    @Test
    fun computeNowCastPm25_ignores_readings_older_than_the_twelve_hour_window() {
        val readings = listOf(NOW to 20.0, NOW - SECONDS_PER_HOUR to 20.0, NOW - 13 * SECONDS_PER_HOUR to 500.0)
        val result = AirQualityIndex.computeNowCastPm25(readings, NOW)
        assertEquals(20.0, result!!, EPSILON)
    }

    // --- nowCastAqiSeries: historical AQI for the Air Quality graph/table (issue #6381) ---

    @Test
    fun nowCastAqiSeries_returns_an_entry_per_reading_in_input_order() {
        val readings = List(4) { i -> (NOW + i * SECONDS_PER_HOUR) to 20.0 }
        assertEquals(readings.size, AirQualityIndex.nowCastAqiSeries(readings).size)
    }

    @Test
    fun nowCastAqiSeries_is_empty_for_no_readings() {
        assertEquals(emptyList(), AirQualityIndex.nowCastAqiSeries(emptyList()))
    }

    @Test
    fun nowCastAqiSeries_leaves_the_first_reading_null_because_one_hour_is_below_the_epa_minimum() {
        // EPA needs the current hour plus at least one of the two hours before it, so the opening sample can never
        // have an AQI - a point must never be plotted from insufficient history.
        val readings = listOf(NOW to 20.0, (NOW + SECONDS_PER_HOUR) to 20.0)
        assertEquals(listOf(null, AirQualityIndex.pm25ToAqi(20.0)), AirQualityIndex.nowCastAqiSeries(readings))
    }

    @Test
    fun nowCastAqiSeries_scopes_each_point_to_its_own_timestamp_not_the_newest() {
        // Point 1 sees only clean air; point 2 (an hour later) is the first with two populated hours, and the spike
        // at point 3 must not leak backwards into the earlier points' values.
        val readings = listOf(NOW to 5.0, (NOW + SECONDS_PER_HOUR) to 5.0, (NOW + 2 * SECONDS_PER_HOUR) to 200.0)
        val series = AirQualityIndex.nowCastAqiSeries(readings)
        assertNull(series[0])
        assertEquals(AirQualityIndex.pm25ToAqi(5.0), series[1])
        kotlin.test.assertTrue(series[2]!! > series[1]!!, "the spike must raise only its own point: $series")
    }

    @Test
    fun nowCastAqiSeries_drops_history_older_than_the_twelve_hour_window() {
        // A 500 µg/m³ reading 13h before the last point is outside the NowCast window, so the tail must read as the
        // clean-air pair alone - identical to calling computeNowCastPm25 with the stale reading omitted.
        val last = NOW + 13 * SECONDS_PER_HOUR
        val readings = listOf(NOW to 500.0, (last - SECONDS_PER_HOUR) to 20.0, last to 20.0)
        assertEquals(AirQualityIndex.pm25ToAqi(20.0), AirQualityIndex.nowCastAqiSeries(readings).last())
    }

    @Test
    fun nowCastAqiSeries_matches_computeNowCastPm25_at_the_final_point() {
        val readings = List(6) { i -> (NOW + i * SECONDS_PER_HOUR) to (10.0 + i) }
        val expected = AirQualityIndex.pm25ToAqi(AirQualityIndex.computeNowCastPm25(readings, readings.last().first)!!)
        assertEquals(expected, AirQualityIndex.nowCastAqiSeries(readings).last())
    }

    @Test
    fun nowCastAqiSeries_yields_null_where_a_gap_breaks_the_epa_minimum_data_rule() {
        // A 5h gap leaves the resumed reading as the only populated hour in its recent-3h window -> no AQI.
        val readings = listOf(NOW to 20.0, (NOW + SECONDS_PER_HOUR) to 20.0, (NOW + 6 * SECONDS_PER_HOUR) to 20.0)
        val series = AirQualityIndex.nowCastAqiSeries(readings)
        assertEquals(AirQualityIndex.pm25ToAqi(20.0), series[1])
        assertNull(series[2])
    }

    private fun assertEquals(expected: Double, actual: Double, epsilon: Double) {
        kotlin.test.assertTrue(abs(expected - actual) < epsilon, "expected $expected but was $actual")
    }
}
