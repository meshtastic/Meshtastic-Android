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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The per-channel telemetry fields are `optional`, so `null` is the only "channel absent" signal and a reported `0` is
 * a real reading. Both cases are pinned here — either alone lets the two states collapse back into one.
 */
class TelemetryChannelsTest {

    @Test
    fun oneWireAccessorReadsEveryChannelInOrder() {
        val metrics =
            EnvironmentMetrics(
                one_wire_temperature_ch0 = 0f,
                one_wire_temperature_ch1 = 1f,
                one_wire_temperature_ch2 = 2f,
                one_wire_temperature_ch3 = 3f,
                one_wire_temperature_ch4 = 4f,
                one_wire_temperature_ch5 = 5f,
                one_wire_temperature_ch6 = 6f,
                one_wire_temperature_ch7 = 7f,
            )

        for (channel in 0 until TELEMETRY_CHANNEL_COUNT) {
            assertEquals(channel.toFloat(), metrics.oneWireTemperature(channel))
        }
    }

    @Test
    fun adcAccessorReadsEveryChannelInOrder() {
        val metrics =
            EnvironmentMetrics(
                adc_voltage_ch0 = 0f,
                adc_voltage_ch1 = 1f,
                adc_voltage_ch2 = 2f,
                adc_voltage_ch3 = 3f,
                adc_voltage_ch4 = 4f,
                adc_voltage_ch5 = 5f,
                adc_voltage_ch6 = 6f,
                adc_voltage_ch7 = 7f,
            )

        for (channel in 0 until TELEMETRY_CHANNEL_COUNT) {
            assertEquals(channel.toFloat(), metrics.adcVoltage(channel))
        }
    }

    @Test
    fun measuredZeroIsDistinctFromAbsentChannel() {
        val reported = EnvironmentMetrics(one_wire_temperature_ch3 = 0f, adc_voltage_ch3 = 0f)

        assertEquals(0f, reported.oneWireTemperature(3))
        assertEquals(0f, reported.adcVoltage(3))
        assertNull(EnvironmentMetrics().oneWireTemperature(3))
        assertNull(EnvironmentMetrics().adcVoltage(3))
    }

    @Test
    fun outOfRangeChannelIsNullRatherThanAnError() {
        val metrics = EnvironmentMetrics(one_wire_temperature_ch0 = 1f, adc_voltage_ch0 = 1f)

        assertNull(metrics.oneWireTemperature(TELEMETRY_CHANNEL_COUNT))
        assertNull(metrics.adcVoltage(TELEMETRY_CHANNEL_COUNT))
        assertNull(metrics.oneWireTemperature(-1))
        assertNull(metrics.adcVoltage(-1))
    }

    @Test
    fun withAccessorsSetOnlyTheTargetChannel() {
        val metrics = EnvironmentMetrics().withOneWireTemperature(2, 12.5f).withAdcVoltage(5, 1.8f)

        assertEquals(12.5f, metrics.oneWireTemperature(2))
        assertEquals(1.8f, metrics.adcVoltage(5))
        assertNull(metrics.oneWireTemperature(1))
        assertNull(metrics.adcVoltage(4))
    }

    @Test
    fun withAccessorsIgnoreOutOfRangeChannels() {
        val metrics = EnvironmentMetrics()

        assertEquals(metrics, metrics.withOneWireTemperature(TELEMETRY_CHANNEL_COUNT, 1f))
        assertEquals(metrics, metrics.withAdcVoltage(TELEMETRY_CHANNEL_COUNT, 1f))
    }

    // ---- legacy repeated-field fallback ----

    @Suppress("DEPRECATION")
    @Test
    fun legacyListIsLiftedOntoPerChannelFields() {
        val stored = EnvironmentMetrics(one_wire_temperature = listOf(10f, 0f, 30f))

        val lifted = stored.withLegacyOneWireTemperatures()

        assertEquals(10f, lifted.oneWireTemperature(0))
        // A stored 0°C is a real historical reading, so it must survive the lift.
        assertEquals(0f, lifted.oneWireTemperature(1))
        assertEquals(30f, lifted.oneWireTemperature(2))
        assertNull(lifted.oneWireTemperature(3))
    }

    @Suppress("DEPRECATION")
    @Test
    fun perChannelValuesWinOverLegacyList() {
        val mixed = EnvironmentMetrics(one_wire_temperature = listOf(10f, 20f), one_wire_temperature_ch0 = 99f)

        val lifted = mixed.withLegacyOneWireTemperatures()

        assertEquals(99f, lifted.oneWireTemperature(0))
        assertEquals(20f, lifted.oneWireTemperature(1))
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyListLongerThanTheChannelRangeIsTruncated() {
        val stored = EnvironmentMetrics(one_wire_temperature = List(12) { it.toFloat() })

        val lifted = stored.withLegacyOneWireTemperatures()

        assertEquals(7f, lifted.oneWireTemperature(TELEMETRY_CHANNEL_COUNT - 1))
        assertNull(lifted.oneWireTemperature(TELEMETRY_CHANNEL_COUNT))
    }

    @Test
    fun absentLegacyListLeavesMetricsUnchanged() {
        val metrics = EnvironmentMetrics(temperature = 21f)

        assertEquals(metrics, metrics.withLegacyOneWireTemperatures())
    }
}
