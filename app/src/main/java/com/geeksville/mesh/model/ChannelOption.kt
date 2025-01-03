/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.model

import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig.ModemPreset
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig.RegionCode
import com.geeksville.mesh.R

/**
 * hash a string into an integer using the djb2 algorithm by Dan Bernstein
 * http://www.cse.yorku.ca/~oz/hash.html
 */
private fun hash(name: String): UInt { // using UInt instead of Long to match RadioInterface.cpp results
    var hash: UInt = 5381u
    for (c in name) {
        hash += (hash shl 5) + c.code.toUInt()
    }
    return hash
}

private val ModemPreset.bandwidth: Float
    get() {
        for (option in ChannelOption.entries) {
            if (option.modemPreset == this) return option.bandwidth
        }
        return 0f
    }

private fun LoRaConfig.bandwidth() = if (usePreset) {
    val wideLora = region == RegionCode.LORA_24
    modemPreset.bandwidth * if (wideLora) 3.25f else 1f
} else when (bandwidth) {
    31 -> .03125f
    62 -> .0625f
    200 -> .203125f
    400 -> .40625f
    800 -> .8125f
    1600 -> 1.6250f
    else -> bandwidth / 1000f
}

val LoRaConfig.numChannels: Int get() {
    for (option in RegionInfo.entries) {
        if (option.regionCode == region) {
            return ((option.freqEnd - option.freqStart) / bandwidth()).toInt()
        }
    }
    return 0
}

internal fun LoRaConfig.channelNum(primaryName: String): Int = when {
    channelNum != 0 -> channelNum
    numChannels == 0 -> 0
    else -> (hash(primaryName) % numChannels.toUInt()).toInt() + 1
}

internal fun LoRaConfig.radioFreq(channelNum: Int): Float {
    if (overrideFrequency != 0f) return overrideFrequency + frequencyOffset
    for (option in RegionInfo.entries) {
        if (option.regionCode == region) {
            return (option.freqStart + bandwidth() / 2) + (channelNum - 1) * bandwidth()
        }
    }
    return 0f
}

@Suppress("MagicNumber")
enum class RegionInfo(
    val regionCode: RegionCode,
    val description: String,
    val freqStart: Float,
    val freqEnd: Float,
) {
    UNSET(RegionCode.UNSET, "Please set a region", 902.0f, 928.0f),
    US(RegionCode.US, "United States", 902.0f, 928.0f),
    EU_433(RegionCode.EU_433, "European Union 433MHz", 433.0f, 434.0f),
    EU_868(RegionCode.EU_868, "European Union 868MHz", 869.4f, 869.65f),
    CN(RegionCode.CN, "China", 470.0f, 510.0f),
    JP(RegionCode.JP, "Japan", 920.5f, 923.5f),
    ANZ(RegionCode.ANZ, "Australia / New Zealand", 915.0f, 928.0f),
    KR(RegionCode.KR, "Korea", 920.0f, 923.0f),
    TW(RegionCode.TW, "Taiwan", 920.0f, 925.0f),
    RU(RegionCode.RU, "Russia", 868.7f, 869.2f),
    IN(RegionCode.IN, "India", 865.0f, 867.0f),
    NZ_865(RegionCode.NZ_865, "New Zealand 865MHz", 864.0f, 868.0f),
    TH(RegionCode.TH, "Thailand", 920.0f, 925.0f),
    UA_433(RegionCode.UA_433, "Ukraine 433MHz", 433.0f, 434.7f),
    UA_868(RegionCode.UA_868, "Ukraine 868MHz", 868.0f, 868.6f),
    MY_433(RegionCode.MY_433, "Malaysia 433MHz", 433.0f, 435.0f),
    MY_919(RegionCode.MY_919, "Malaysia 919MHz", 919.0f, 924.0f),
    SG_923(RegionCode.SG_923, "Singapore 923MHz", 917.0f, 925.0f),
    PH_433(RegionCode.PH_433, "Philippines 433MHz", 433.0f, 434.7f),
    PH_868(RegionCode.PH_868, "Philippines 868MHz", 868.0f, 869.4f),
    PH_915(RegionCode.PH_915, "Philippines 915MHz", 915.0f, 918.0f),
    LORA_24(RegionCode.LORA_24, "2.4 GHz", 2400.0f, 2483.5f),
}

enum class ChannelOption(
    val modemPreset: ModemPreset,
    val configRes: Int,
    val bandwidth: Float,
) {
    SHORT_TURBO(ModemPreset.SHORT_TURBO, R.string.modem_config_turbo, bandwidth = .500f),
    SHORT_FAST(ModemPreset.SHORT_FAST, R.string.modem_config_short, .250f),
    SHORT_SLOW(ModemPreset.SHORT_SLOW, R.string.modem_config_slow_short, .250f),
    MEDIUM_FAST(ModemPreset.MEDIUM_FAST, R.string.modem_config_medium, .250f),
    MEDIUM_SLOW(ModemPreset.MEDIUM_SLOW, R.string.modem_config_slow_medium, .250f),
    LONG_FAST(ModemPreset.LONG_FAST, R.string.modem_config_long, .250f),
    LONG_MODERATE(ModemPreset.LONG_MODERATE, R.string.modem_config_mod_long, .125f),
    LONG_SLOW(ModemPreset.LONG_SLOW, R.string.modem_config_slow_long, .125f),
    VERY_LONG_SLOW(ModemPreset.VERY_LONG_SLOW, R.string.modem_config_very_long, .0625f),
}
