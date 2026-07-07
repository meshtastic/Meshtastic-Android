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

/**
 * Transmitter parameters for a Meshtastic Site Planner coverage estimate. Maps onto the planner's flat query contract
 * (`?lat=&lon=&name=&tx_power=&tx_freq=&tx_height=&tx_gain=`), so [toQueryUrl] can hand a configured, auto-running view
 * to the planner without knowing its internal parameter schema.
 */
data class SitePlannerParams(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val txPowerWatts: Double = DEFAULT_TX_POWER_WATTS,
    val txFreqMhz: Double = DEFAULT_TX_FREQ_MHZ,
    val txHeightMeters: Double = DEFAULT_TX_HEIGHT_METERS,
    val txGainDbi: Double = DEFAULT_TX_GAIN_DBI,
    val colorScale: String = DEFAULT_COLOR_SCALE,
    // Advanced fields — default to the planner's own values, so sending them is behaviorally identical to omitting
    // them (they just make the hand-off explicit). Surfaced behind an "Advanced" section in the form.
    val rxSensitivityDbm: Double = DEFAULT_RX_SENSITIVITY_DBM,
    val rxHeightMeters: Double = DEFAULT_RX_HEIGHT_METERS,
    val maxRangeKm: Double = DEFAULT_MAX_RANGE_KM,
    val highResolution: Boolean = false,
    val minDbm: Double = DEFAULT_MIN_DBM,
    val maxDbm: Double = DEFAULT_MAX_DBM,
    val overlayTransparency: Int = DEFAULT_OVERLAY_TRANSPARENCY,
) {
    /**
     * Build the planner URL that prefills these params and auto-runs the simulation (`run=1`), asking the planner to
     * hand the result to a native host bridge (`bridge=1`) rather than the share sheet. Advanced params map onto the
     * planner's flat query contract (receiver / simulation / display sections).
     */
    fun toQueryUrl(baseUrl: String): String {
        val query = buildString {
            append("lat=").append(latitude)
            append("&lon=").append(longitude)
            append("&name=").append(encodeQueryComponent(name))
            append("&tx_power=").append(txPowerWatts)
            append("&tx_freq=").append(txFreqMhz)
            append("&tx_height=").append(txHeightMeters)
            append("&tx_gain=").append(txGainDbi)
            append("&color_scale=").append(encodeQueryComponent(colorScale))
            append("&rx_sensitivity=").append(rxSensitivityDbm)
            append("&rx_height=").append(rxHeightMeters)
            append("&max_range=").append(maxRangeKm)
            if (highResolution) append("&high_res=1")
            append("&min_dbm=").append(minDbm)
            append("&max_dbm=").append(maxDbm)
            append("&overlay_transparency=").append(overlayTransparency)
            append("&run=1&bridge=1")
        }
        val separator = if (baseUrl.endsWith("/")) "" else "/"
        return "$baseUrl$separator?$query"
    }

    companion object {
        // Meshtastic-typical stock defaults; all editable in the form before submission. Values mirror the planner's
        // own factory defaults (src/store.ts defaultParams()) so an untouched form matches a fresh planner session.
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

        // Validation ranges mirroring the planner's input constraints (src/components/*.vue).
        const val MIN_FREQ_MHZ: Double = 20.0
        const val MAX_FREQ_MHZ: Double = 20_000.0
        const val MIN_RX_SENSITIVITY_DBM: Double = -150.0
        const val MAX_RX_SENSITIVITY_DBM: Double = -30.0
        const val MAX_RANGE_STANDARD_KM: Double = 150.0
        const val MAX_RANGE_HIGH_RES_KM: Double = 70.0
        const val MIN_TRANSPARENCY: Int = 0
        const val MAX_TRANSPARENCY: Int = 100

        /** Coverage palettes the planner ships (value → label), mirroring its Display.vue picker. */
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

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
private val HEX = "0123456789ABCDEF".toCharArray()
private const val BYTE_MASK = 0xFF
private const val ASCII_LIMIT = 0x80
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0xF

/** Percent-encode a query-component value (UTF-8), so names with spaces/non-ASCII survive the round trip. */
internal fun encodeQueryComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and BYTE_MASK
        if (code < ASCII_LIMIT && code.toChar() in UNRESERVED) {
            append(code.toChar())
        } else {
            append('%').append(HEX[code shr NIBBLE_BITS]).append(HEX[code and NIBBLE_MASK])
        }
    }
}
