package com.geeksville.mesh.model

import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R

enum class ChannelOption(
    val modemPreset: ConfigProtos.Config.LoRaConfig.ModemPreset,
    val configRes: Int,
    val minBroadcastPeriodSecs: Int
) {
    SHORT_FAST(ConfigProtos.Config.LoRaConfig.ModemPreset.ShortFast, R.string.modem_config_short, 30),
    SHORT_SLOW(ConfigProtos.Config.LoRaConfig.ModemPreset.ShortSlow, R.string.modem_config_slow_short, 30),
    MED_FAST(ConfigProtos.Config.LoRaConfig.ModemPreset.MedFast, R.string.modem_config_medium, 60),
    MED_SLOW(ConfigProtos.Config.LoRaConfig.ModemPreset.MedSlow, R.string.modem_config_slow_medium, 60),
    LONG_FAST(ConfigProtos.Config.LoRaConfig.ModemPreset.LongFast, R.string.modem_config_long, 240),
    LONG_SLOW(ConfigProtos.Config.LoRaConfig.ModemPreset.LongSlow, R.string.modem_config_slow_long, 375),
    VERY_LONG(ConfigProtos.Config.LoRaConfig.ModemPreset.VLongSlow, R.string.modem_config_very_long, 375);

    companion object {
        fun fromConfig(modemPreset: ConfigProtos.Config.LoRaConfig.ModemPreset?): ChannelOption? {
            for (option in values()) {
                if (option.modemPreset == modemPreset)
                    return option
            }
            return null
        }

        val defaultMinBroadcastPeriod = VERY_LONG.minBroadcastPeriodSecs
    }
}