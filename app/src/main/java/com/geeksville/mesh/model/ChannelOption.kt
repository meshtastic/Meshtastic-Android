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
        if (option.regionCode == region)
            return ((option.freqEnd - option.freqStart) / bandwidth()).toInt()
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
        if (option.regionCode == region)
            return (option.freqStart + bandwidth() / 2) + (channelNum - 1) * bandwidth()
    }
    return 0f
}

@Suppress("MagicNumber")
enum class RegionInfo(
    val regionCode: RegionCode,
    val freqStart: Float,
    val freqEnd: Float,
) {
    US(RegionCode.US, 902.0f, 928.0f),
    EU_433(RegionCode.EU_433, 433.0f, 434.0f),
    EU_868(RegionCode.EU_868, 869.4f, 869.65f),
    CN(RegionCode.CN, 470.0f, 510.0f),
    JP(RegionCode.JP, 920.5f, 923.5f),
    ANZ(RegionCode.ANZ, 915.0f, 928.0f),
    RU(RegionCode.RU, 868.7f, 869.2f),
    KR(RegionCode.KR, 920.0f, 923.0f),
    TW(RegionCode.TW, 920.0f, 925.0f),
    IN(RegionCode.IN, 865.0f, 867.0f),
    NZ_865(RegionCode.NZ_865, 864.0f, 868.0f),
    TH(RegionCode.TH, 920.0f, 925.0f),
    UA_433(RegionCode.UA_433, 433.0f, 434.7f),
    UA_868(RegionCode.UA_868, 868.0f, 868.6f),
    MY_433(RegionCode.MY_433, 433.0f, 435.0f),
    MY_919(RegionCode.MY_919, 919.0f, 924.0f),
    SG_923(RegionCode.SG_923, 917.0f, 925.0f),
    LORA_24(RegionCode.LORA_24, 2400.0f, 2483.5f),
    UNSET(RegionCode.UNSET, 902.0f, 928.0f),
    ;
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
    ;
}
