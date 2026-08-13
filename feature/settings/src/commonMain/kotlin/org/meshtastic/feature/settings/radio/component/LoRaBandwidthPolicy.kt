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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.HardwareModel

internal data class LoRaBandwidthOption(val wireValue: Int, val displayKilohertz: String)

internal data class LoRaBandwidthSelection(
    /** Null outside LORA_24, where Android's existing numeric input remains unconstrained. */
    val options: List<LoRaBandwidthOption>?,
    /** An unsupported stored value that the UI must keep visible until the user replaces it. */
    val invalidPersistedValue: Int?,
) {
    val isValid: Boolean
        get() = invalidPersistedValue == null

    fun allowsSave(usePreset: Boolean): Boolean = usePreset || isValid
}

private const val SX128X_WIDEST_BANDWIDTH_CODE = 1600

private val LORA_24_DEFAULT_OPTION = LoRaBandwidthOption(0, "812.5")

private val CONSERVATIVE_HIGH_BAND_OPTIONS =
    listOf(LoRaBandwidthOption(200, "203.125"), LoRaBandwidthOption(400, "406.25"), LoRaBandwidthOption(800, "812.5"))

private val SX128X_ONLY_TARGETS =
    setOf(
        "betafpv_2400_tx_micro",
        "makerpython_nrf52840_sx1280_eink",
        "makerpython_nrf52840_sx1280_oled",
        "my-esp32s3-diy-eink",
        "my-esp32s3-diy-oled",
        "tlora-v2-1-1_8",
    )

private val SX128X_EXCLUDED_HARDWARE_MODELS = setOf(HardwareModel.TLORA_T3_S3, HardwareModel.MUZI_BASE)

/**
 * Returns the custom-bandwidth policy for the destination radio.
 *
 * Current protobufs report a PlatformIO target only for the directly connected node and do not report the radio chip
 * selected at runtime. Therefore 1600 is granted only to exact firmware targets that compile exclusively for SX128x.
 * Mixed, remote, and unknown targets use the LR1121-safe conservative set.
 */
internal fun loRaBandwidthSelection(
    storedValue: Int,
    region: RegionCode,
    hwModel: HardwareModel?,
    pioEnv: String?,
): LoRaBandwidthSelection {
    if (region != RegionCode.LORA_24) return LoRaBandwidthSelection(options = null, invalidPersistedValue = null)

    val sx128xOnly =
        hwModel !in SX128X_EXCLUDED_HARDWARE_MODELS && pioEnv?.lowercase()?.let(SX128X_ONLY_TARGETS::contains) == true
    val supportedOptions =
        if (sx128xOnly) {
            CONSERVATIVE_HIGH_BAND_OPTIONS + LoRaBandwidthOption(SX128X_WIDEST_BANDWIDTH_CODE, "1625")
        } else {
            CONSERVATIVE_HIGH_BAND_OPTIONS
        }
    val options = listOf(LORA_24_DEFAULT_OPTION) + supportedOptions
    val invalidPersistedValue = storedValue.takeUnless { value -> options.any { it.wireValue == value } }
    return LoRaBandwidthSelection(options = options, invalidPersistedValue = invalidPersistedValue)
}
