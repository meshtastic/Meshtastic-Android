/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.discovery.ai

/**
 * LoRa modem preset reference data for enriching AI prompts and algorithmic summaries. Data sourced from Meshtastic
 * radio-settings documentation.
 */
internal object LoRaPresetReference {

    data class PresetInfo(
        val bandwidth: String,
        val spreadingFactor: String,
        val dataRate: String,
        val linkBudget: String,
        val description: String,
    )

    private val presets =
        mapOf(
            "Long Fast" to
                PresetInfo(
                    "250kHz",
                    "SF11",
                    "1.07kbps",
                    "153dB",
                    "Default. Good range but high airtime per packet; causes congestion in networks >60 nodes.",
                ),
            "Long Moderate" to
                PresetInfo(
                    "125kHz",
                    "SF11",
                    "0.34kbps",
                    "155.5dB",
                    "Maximum range but extremely slow; only suitable for very sparse, long-range deployments.",
                ),
            "Long Slow" to
                PresetInfo(
                    "125kHz",
                    "SF12",
                    "0.18kbps",
                    "158dB",
                    "Extreme range, extremely slow; only for point-to-point long-range links.",
                ),
            "Long Turbo" to
                PresetInfo(
                    "500kHz",
                    "SF9",
                    "7.03kbps",
                    "148dB",
                    "Fast long-range. ~7x LongFast speed, reduced range. Good balance for moderate networks.",
                ),
            "Medium Slow" to
                PresetInfo(
                    "250kHz",
                    "SF10",
                    "1.95kbps",
                    "150.5dB",
                    "~2x LongFast speed. Bay Area mesh (150+ nodes) thrives on this preset.",
                ),
            "Medium Fast" to
                PresetInfo(
                    "250kHz",
                    "SF9",
                    "3.52kbps",
                    "148dB",
                    "~3.5x LongFast speed. Excellent balance for dense urban/suburban networks.",
                ),
            "Short Slow" to
                PresetInfo(
                    "250kHz",
                    "SF8",
                    "6.25kbps",
                    "145.5dB",
                    "~6x LongFast speed. Good for dense networks with adequate node spacing.",
                ),
            "Short Fast" to
                PresetInfo(
                    "250kHz",
                    "SF7",
                    "10.94kbps",
                    "143dB",
                    "~10x LongFast speed. Wellington NZ mesh (150+ nodes) switched here with excellent results.",
                ),
            "Short Turbo" to
                PresetInfo(
                    "500kHz",
                    "SF7",
                    "21.88kbps",
                    "140dB",
                    "Maximum speed, minimum range. Only for very dense, close-proximity deployments.",
                ),
        )

    /** Get reference data for a preset, matching by substring (e.g. "Long Fast" matches "Long Fast"). */
    fun getInfo(presetName: String): PresetInfo? =
        presets.entries.firstOrNull { presetName.contains(it.key, ignoreCase = true) }?.value

    /** Format a one-line reference string for a preset. */
    fun formatReference(presetName: String): String? {
        val info = getInfo(presetName) ?: return null
        return "$presetName: ${info.bandwidth} BW, ${info.spreadingFactor}, " +
            "${info.dataRate}, ${info.linkBudget} link budget. ${info.description}"
    }

    /** Build a multi-line reference block for all scanned presets. */
    fun buildReferenceBlock(presetNames: List<String>): String = buildString {
        appendLine("LoRa Preset Reference:")
        for (name in presetNames) {
            val ref = formatReference(name)
            if (ref != null) {
                appendLine("  $ref")
            }
        }
    }
}
