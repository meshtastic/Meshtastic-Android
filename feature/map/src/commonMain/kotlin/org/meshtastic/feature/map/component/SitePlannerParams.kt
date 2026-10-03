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
package org.meshtastic.feature.map.component

/** Transmitter, receiver, and display settings for a Site Planner coverage estimate. */
data class SitePlannerParams(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val txPowerWatts: Double = DEFAULT_TX_POWER_WATTS,
    val txFreqMhz: Double = DEFAULT_TX_FREQ_MHZ,
    val txHeightMeters: Double = DEFAULT_TX_HEIGHT_METERS,
    val txGainDbi: Double = DEFAULT_TX_GAIN_DBI,
    val colorScale: String = DEFAULT_COLOR_SCALE,
    val rxSensitivityDbm: Double = DEFAULT_RX_SENSITIVITY_DBM,
    val rxHeightMeters: Double = DEFAULT_RX_HEIGHT_METERS,
    val maxRangeKm: Double = DEFAULT_MAX_RANGE_KM,
    val minDbm: Double = DEFAULT_MIN_DBM,
    val maxDbm: Double = DEFAULT_MAX_DBM,
    val overlayTransparency: Int = DEFAULT_OVERLAY_TRANSPARENCY,
) {
    companion object {
        // Meshtastic-typical stock defaults; all editable in the form before submission.
        const val DEFAULT_TX_POWER_WATTS: Double = 0.1
        const val DEFAULT_TX_FREQ_MHZ: Double = 907.0
        const val DEFAULT_TX_HEIGHT_METERS: Double = 2.0
        const val DEFAULT_TX_GAIN_DBI: Double = 2.0
        const val DEFAULT_COLOR_SCALE: String = "plasma"
        const val DEFAULT_RX_SENSITIVITY_DBM: Double = -130.0
        const val DEFAULT_RX_HEIGHT_METERS: Double = 1.0
        const val DEFAULT_MAX_RANGE_KM: Double = 30.0
        const val DEFAULT_MIN_DBM: Double = -130.0
        const val DEFAULT_MAX_DBM: Double = -80.0
        const val DEFAULT_OVERLAY_TRANSPARENCY: Int = 50

        // ITU-R P.1812 is defined from 30 MHz to 6 GHz; the model rejects anything outside it.
        const val MIN_FREQ_MHZ: Double = 30.0
        const val MAX_FREQ_MHZ: Double = 6_000.0
        const val MIN_RX_SENSITIVITY_DBM: Double = -150.0
        const val MAX_RX_SENSITIVITY_DBM: Double = -30.0
        const val MIN_RANGE_KM: Double = 1.0
        const val MAX_RANGE_KM: Double = 150.0
        const val MIN_TRANSPARENCY: Int = 0
        const val MAX_TRANSPARENCY: Int = 100

        /** Coverage palettes (value → label), matching the hosted Site Planner's picker. */
        val COLOR_SCALES: List<Pair<String, String>> =
            listOf(
                "plasma" to "Plasma",
                "viridis" to "Viridis (colorblind-safe)",
                "CMRmap" to "CMR map",
                "cool" to "Cool",
                "turbo" to "Turbo",
                "jet" to "Jet",
            )
    }
}
