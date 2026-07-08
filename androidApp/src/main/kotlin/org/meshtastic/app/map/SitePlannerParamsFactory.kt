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
package org.meshtastic.app.map

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.primaryChannel
import org.meshtastic.feature.map.component.SitePlannerParams
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.math.pow

/**
 * Seed Site Planner params from a node (name + position) and, when a radio is connected, from its actual config:
 * transmit frequency (from the primary channel), transmit power (dBm→W), and a receiver sensitivity derived from the
 * modem preset. Antenna gain/height aren't in the device config, so they keep the planner's stock defaults.
 *
 * Flavor-neutral: both the Google and F-Droid maps feed the same hosted Site Planner.
 */
fun Node?.toSitePlannerParams(channelSet: ChannelSet?): SitePlannerParams {
    val lora = channelSet?.lora_config
    val freqMhz = channelSet?.primaryChannel?.radioFreq?.toDouble()?.takeIf { it > 0.0 }
    return SitePlannerParams(
        name = this?.user?.long_name?.takeIf { it.isNotBlank() } ?: "Coverage",
        latitude = this?.latitude ?: 0.0,
        longitude = this?.longitude ?: 0.0,
        txFreqMhz = freqMhz ?: SitePlannerParams.DEFAULT_TX_FREQ_MHZ,
        txPowerWatts = dbmToWatts(lora?.tx_power ?: 0) ?: SitePlannerParams.DEFAULT_TX_POWER_WATTS,
        rxSensitivityDbm = sensitivityDbmFor(lora?.modem_preset),
    )
}

/**
 * Meshtastic tx_power is dBm; the planner wants watts. 0 (or less) means "use region max" in firmware — unknown here.
 */
@Suppress("MagicNumber")
private fun dbmToWatts(dbm: Int): Double? = if (dbm <= 0) null else 10.0.pow((dbm - 30).toDouble() / 10.0)

/**
 * Approximate receiver sensitivity (dBm) per Meshtastic modem preset, from the Site Planner's own parameters.md table.
 * Unmapped presets fall back to the planner's default so the estimate still runs.
 */
@Suppress("MagicNumber")
private fun sensitivityDbmFor(preset: ModemPreset?): Double = when (preset) {
    ModemPreset.SHORT_TURBO -> -126.0
    ModemPreset.SHORT_FAST -> -129.0
    ModemPreset.SHORT_SLOW -> -131.5
    ModemPreset.MEDIUM_FAST -> -134.0
    ModemPreset.MEDIUM_SLOW -> -136.5
    ModemPreset.LONG_FAST -> -139.0
    ModemPreset.LONG_MODERATE -> -142.0
    ModemPreset.LONG_SLOW -> -144.5
    ModemPreset.VERY_LONG_SLOW -> -147.5
    else -> SitePlannerParams.DEFAULT_RX_SENSITIVITY_DBM
}
