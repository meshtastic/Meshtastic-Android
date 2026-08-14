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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model.util

import org.meshtastic.proto.EnvironmentMetrics

/**
 * Channel count for the per-channel `EnvironmentMetrics` telemetry fields — `one_wire_temperature_ch0..ch7` and
 * `adc_voltage_ch0..ch7`.
 */
const val TELEMETRY_CHANNEL_COUNT: Int = 8

/**
 * Index-to-field accessors for the per-channel telemetry fields.
 *
 * Wire generates one named property per channel, so indexed access needs an explicit map rather than a list lookup.
 * Each field is `optional`, making `null` the only "channel absent" signal — a returned `0f` is a real reading (0 V on
 * an unloaded ADC input, 0°C on a probe at freezing), so callers must not treat zero as missing.
 *
 * Reading past [TELEMETRY_CHANNEL_COUNT] returns `null` rather than throwing, so a firmware that grows the channel
 * range degrades to "not shown" instead of crashing.
 */
fun EnvironmentMetrics.oneWireTemperature(channel: Int): Float? = when (channel) {
    0 -> one_wire_temperature_ch0
    1 -> one_wire_temperature_ch1
    2 -> one_wire_temperature_ch2
    3 -> one_wire_temperature_ch3
    4 -> one_wire_temperature_ch4
    5 -> one_wire_temperature_ch5
    6 -> one_wire_temperature_ch6
    7 -> one_wire_temperature_ch7
    else -> null
}

fun EnvironmentMetrics.adcVoltage(channel: Int): Float? = when (channel) {
    0 -> adc_voltage_ch0
    1 -> adc_voltage_ch1
    2 -> adc_voltage_ch2
    3 -> adc_voltage_ch3
    4 -> adc_voltage_ch4
    5 -> adc_voltage_ch5
    6 -> adc_voltage_ch6
    7 -> adc_voltage_ch7
    else -> null
}

/** Returns a copy with 1-Wire [channel] set to [value]; an out-of-range [channel] is a no-op. */
fun EnvironmentMetrics.withOneWireTemperature(channel: Int, value: Float?): EnvironmentMetrics = when (channel) {
    0 -> copy(one_wire_temperature_ch0 = value)
    1 -> copy(one_wire_temperature_ch1 = value)
    2 -> copy(one_wire_temperature_ch2 = value)
    3 -> copy(one_wire_temperature_ch3 = value)
    4 -> copy(one_wire_temperature_ch4 = value)
    5 -> copy(one_wire_temperature_ch5 = value)
    6 -> copy(one_wire_temperature_ch6 = value)
    7 -> copy(one_wire_temperature_ch7 = value)
    else -> this
}

/** Returns a copy with ADC [channel] set to [value]; an out-of-range [channel] is a no-op. */
fun EnvironmentMetrics.withAdcVoltage(channel: Int, value: Float?): EnvironmentMetrics = when (channel) {
    0 -> copy(adc_voltage_ch0 = value)
    1 -> copy(adc_voltage_ch1 = value)
    2 -> copy(adc_voltage_ch2 = value)
    3 -> copy(adc_voltage_ch3 = value)
    4 -> copy(adc_voltage_ch4 = value)
    5 -> copy(adc_voltage_ch5 = value)
    6 -> copy(adc_voltage_ch6 = value)
    7 -> copy(adc_voltage_ch7 = value)
    else -> this
}

/**
 * Lifts the deprecated `repeated one_wire_temperature` list onto the per-channel fields, for telemetry stored before
 * firmware 2.8 moved to `one_wire_temperature_chN`.
 *
 * Upstream flipped field 23 to `FT_IGNORE`, so current firmware never emits it and this is a read path for historical
 * logs only. Channels already carrying a per-channel value win, so a live packet is never overwritten by legacy data.
 */
@Suppress("DEPRECATION")
fun EnvironmentMetrics.withLegacyOneWireTemperatures(): EnvironmentMetrics =
    one_wire_temperature.take(TELEMETRY_CHANNEL_COUNT).foldIndexed(this) { channel, metrics, legacy ->
        if (metrics.oneWireTemperature(channel) == null) {
            metrics.withOneWireTemperature(channel, legacy)
        } else {
            metrics
        }
    }
