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

import kotlin.math.pow

/**
 * EPA NowCast + AQI breakpoint math for PM2.5, per meshtastic/design#54.
 *
 * NowCast is a 12-hour rolling average of PM2.5 that weights recent hours more heavily than older ones, used in lieu of
 * the official 24h EPA AQI average because it can report a value well before a full day of data exists. See
 * https://usepa.servicenowservices.com/airnow (NowCast) and the EPA PM2.5 AQI breakpoint table.
 */
object AirQualityIndex {

    private const val NOWCAST_WINDOW_HOURS = 12
    private const val SECONDS_PER_HOUR = 3600L

    /** EPA requires the most recent hour plus at least 2 of the 3 most recent hours, or NowCast isn't reported. */
    private const val MIN_VALID_HOURS = 2
    private const val RECENT_WINDOW_HOURS = 3

    private const val MIN_WEIGHT_FACTOR = 0.5

    /**
     * Computes the NowCast PM2.5 concentration (µg/m³) from a node's PM2.5 [readings] (epoch-seconds to µg/m³ pairs),
     * relative to [nowEpochSeconds]. Readings are binned into hourly buckets (0 = most recent hour) and averaged within
     * each bucket. Returns null if there isn't enough history yet: the most recent hour must have a reading, and at
     * least [MIN_VALID_HOURS] of the [RECENT_WINDOW_HOURS] most recent hours must be populated — EPA's minimum-data
     * rule, so stale data spread across the older end of the 12h window can't produce a value.
     */
    fun computeNowCastPm25(readings: List<Pair<Long, Double>>, nowEpochSeconds: Long): Double? {
        val sums = DoubleArray(NOWCAST_WINDOW_HOURS)
        val counts = IntArray(NOWCAST_WINDOW_HOURS)
        for ((time, pm25) in readings) {
            val hoursAgo = (nowEpochSeconds - time) / SECONDS_PER_HOUR
            if (hoursAgo in 0 until NOWCAST_WINDOW_HOURS) {
                sums[hoursAgo.toInt()] += pm25
                counts[hoursAgo.toInt()]++
            }
        }
        val hourlyAverages = List(NOWCAST_WINDOW_HOURS) { i -> if (counts[i] > 0) sums[i] / counts[i] else null }
        val present = hourlyAverages.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }

        val recentValid = hourlyAverages.take(RECENT_WINDOW_HOURS).count { it != null }
        return if (hourlyAverages[0] == null || recentValid < MIN_VALID_HOURS) {
            null
        } else {
            val max = present.maxOf { it.second }
            val min = present.minOf { it.second }
            val weightFactor = if (max <= 0.0) 1.0 else (1.0 - (max - min) / max).coerceAtLeast(MIN_WEIGHT_FACTOR)

            var weightedSum = 0.0
            var weightTotal = 0.0
            for ((hoursAgo, value) in present) {
                val weight = weightFactor.pow(hoursAgo)
                weightedSum += weight * value
                weightTotal += weight
            }
            weightedSum / weightTotal
        }
    }

    private data class Breakpoint(
        val concentrationLow: Double,
        val concentrationHigh: Double,
        val aqiLow: Int,
        val aqiHigh: Int,
    )

    // Standard EPA PM2.5 (µg/m³) breakpoint table.
    private val BREAKPOINTS =
        listOf(
            Breakpoint(0.0, 12.0, 0, 50),
            Breakpoint(12.1, 35.4, 51, 100),
            Breakpoint(35.5, 55.4, 101, 150),
            Breakpoint(55.5, 150.4, 151, 200),
            Breakpoint(150.5, 250.4, 201, 300),
            Breakpoint(250.5, 500.4, 301, 500),
        )

    /**
     * Converts a PM2.5 concentration (µg/m³) to a 0-500 EPA AQI value via linear interpolation over the standard
     * breakpoint table. Concentrations above the top breakpoint are clamped to AQI 500.
     */
    fun pm25ToAqi(concentration: Double): Int {
        val clamped = concentration.coerceAtLeast(0.0)
        val breakpoint = BREAKPOINTS.lastOrNull { clamped >= it.concentrationLow } ?: BREAKPOINTS.first()
        if (clamped > breakpoint.concentrationHigh) return breakpoint.aqiHigh
        val aqi =
            (breakpoint.aqiHigh - breakpoint.aqiLow).toDouble() /
                (breakpoint.concentrationHigh - breakpoint.concentrationLow) * (clamped - breakpoint.concentrationLow) +
                breakpoint.aqiLow
        return aqi.let { kotlin.math.round(it).toInt() }
    }
}
