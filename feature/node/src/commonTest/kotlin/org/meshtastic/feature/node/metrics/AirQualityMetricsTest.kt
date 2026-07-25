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

import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the air-quality chart's value extraction ([AirQuality.getValue]).
 *
 * Locks the BUG B fix: a present-and-zero reading (a PM sensor in clean air reports 0 µg/m³) must be plotted, while a
 * genuinely absent field stays null so the metric is neither charted nor offered as a selectable chip. Also pins the
 * assumption that a partial-sensor node (e.g. CO2 only) leaves the other fields unset/null rather than zero.
 */
class AirQualityMetricsTest {

    private fun telemetry(aq: AirQualityMetrics) = Telemetry(air_quality_metrics = aq)

    @Test
    fun `getValue returns present non-zero readings`() {
        val t = telemetry(AirQualityMetrics(pm10_standard = 1, pm25_standard = 2, pm100_standard = 3, co2 = 800))
        assertEquals(1f, AirQuality.PM1_0.getValue(t))
        assertEquals(2f, AirQuality.PM2_5.getValue(t))
        assertEquals(3f, AirQuality.PM10.getValue(t))
        assertEquals(800f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue plots a present-zero reading instead of suppressing it`() {
        // BUG B regression: clean air reads 0 µg/m³ and must chart as 0f, not be dropped to null.
        val t = telemetry(AirQualityMetrics(pm10_standard = 0, pm25_standard = 0, pm100_standard = 0, co2 = 0))
        assertEquals(0f, AirQuality.PM1_0.getValue(t))
        assertEquals(0f, AirQuality.PM2_5.getValue(t))
        assertEquals(0f, AirQuality.PM10.getValue(t))
        assertEquals(0f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue returns null for an absent field so a partial-sensor node does not chart spurious series`() {
        // A CO2-only node leaves the PM fields unset (Wire decodes an unset optional uint32 to null).
        val t = telemetry(AirQualityMetrics(co2 = 450))
        assertNull(AirQuality.PM1_0.getValue(t))
        assertNull(AirQuality.PM2_5.getValue(t))
        assertNull(AirQuality.PM10.getValue(t))
        assertEquals(450f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue returns null for every series when there are no air quality metrics`() {
        val t = Telemetry()
        AirQuality.entries.forEach {
            assertNull(it.getValue(t), "${it.name} should be null without air_quality_metrics")
        }
    }

    @Test
    fun `metricsWithData drops selected series that have no reading so the legend is not ever-present`() {
        // Issue 5873, CO2-only node: PM2.5 is default-selected but never reported, so it must not survive into the
        // legend.
        val co2Only = listOf(sample(AirQualityMetrics(co2 = 450)))
        assertEquals(listOf(AirQuality.CO2), metricsWithData(listOf(AirQuality.PM2_5, AirQuality.CO2), co2Only))
    }

    @Test
    fun `metricsWithData keeps a series once it has any reading in the frame`() {
        val mixed = listOf(sample(AirQualityMetrics(co2 = 450)), sample(AirQualityMetrics(pm25_standard = 8)))
        assertEquals(
            listOf(AirQuality.PM2_5, AirQuality.CO2),
            metricsWithData(listOf(AirQuality.PM2_5, AirQuality.CO2), mixed),
        )
    }

    // --- AQI series (issue #6381): derived from PM2.5 history, not carried by the telemetry proto ---

    @Test
    fun `getValue for AQI comes from the sample derived value rather than the telemetry`() {
        val t = telemetry(AirQualityMetrics(pm25_standard = 25))
        assertNull(AirQuality.AQI.getValue(t), "AQI is not a proto field, so a bare Telemetry has none")
        assertEquals(78f, AirQuality.AQI.getValue(AirQualitySample(t, aqi = 78)))
        assertNull(AirQuality.AQI.getValue(AirQualitySample(t, aqi = null)))
    }

    @Test
    fun `getValue for AQI plots a zero AQI instead of suppressing it`() {
        // Pristine air is AQI 0 - a real value, and the same present-and-zero rule the PM series follow.
        assertEquals(0f, AirQuality.AQI.getValue(AirQualitySample(telemetry(AirQualityMetrics()), aqi = 0)))
    }

    @Test
    fun `withNowCastAqi returns samples ascending by time regardless of input order`() {
        // The list view feeds newest-first data in; the chart needs oldest-first.
        val newestFirst = (3 downTo 0).map { hourlyTelemetry(it, pm25 = 10) }
        assertEquals(listOf(0, 1, 2, 3).map { HOUR * it }, withNowCastAqi(newestFirst).map { it.telemetry.time })
    }

    @Test
    fun `withNowCastAqi leaves the earliest sample without an AQI and fills in later ones`() {
        val samples = withNowCastAqi((0..3).map { hourlyTelemetry(it, pm25 = 10) })
        assertNull(samples.first().aqi, "one hour of history is below EPA's minimum-data rule")
        // 2024 EPA table: a steady 10 µg/m³ is AQI 53 (Moderate).
        assertEquals(listOf(53, 53, 53), samples.drop(1).map { it.aqi })
        assertEquals(listOf(AirQuality.AQI), metricsWithData(listOf(AirQuality.AQI), samples))
    }

    @Test
    fun `withNowCastAqi gives a CO2-only node no AQI so the series is never offered`() {
        val co2Only =
            (0..3).map { hour -> Telemetry(time = HOUR * hour, air_quality_metrics = AirQualityMetrics(co2 = 450)) }
        val samples = withNowCastAqi(co2Only)
        assertEquals(emptyList(), samples.mapNotNull { it.aqi })
        assertEquals(emptyList(), metricsWithData(listOf(AirQuality.AQI), samples))
    }

    @Test
    fun `withNowCastAqi skips samples whose PM2_5 is absent without shifting the others' values`() {
        // A CO2-only sample interleaved with PM2.5 samples must not consume an entry of the AQI series.
        val telemetries =
            listOf(
                hourlyTelemetry(0, pm25 = 10),
                Telemetry(time = HOUR, air_quality_metrics = AirQualityMetrics(co2 = 450)),
                hourlyTelemetry(2, pm25 = 10),
                hourlyTelemetry(3, pm25 = 10),
            )
        val samples = withNowCastAqi(telemetries)
        assertNull(samples[1].aqi, "a sample with no PM2.5 reading gets no AQI")
        assertEquals(53, samples[3].aqi)
    }

    private fun hourlyTelemetry(hoursFromStart: Int, pm25: Int) =
        Telemetry(time = HOUR * hoursFromStart, air_quality_metrics = AirQualityMetrics(pm25_standard = pm25))

    private fun sample(aq: AirQualityMetrics, aqi: Int? = null) = AirQualitySample(telemetry(aq), aqi)

    private companion object {
        const val HOUR = 3600
    }
}
