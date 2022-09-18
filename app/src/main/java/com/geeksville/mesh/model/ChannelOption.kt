package com.geeksville.mesh.model

import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig.ModemPreset
import com.geeksville.mesh.R

enum class ChannelOption(
    val modemPreset: ModemPreset,
    val configRes: Int,
    val minBroadcastPeriodSecs: Int
) {
    SHORT_FAST(ModemPreset.SHORT_FAST, R.string.modem_config_short, 30),
    SHORT_SLOW(ModemPreset.SHORT_SLOW, R.string.modem_config_slow_short, 30),
    MEDIUM_FAST(ModemPreset.MEDIUM_FAST, R.string.modem_config_medium, 60),
    MEDIUM_SLOW(ModemPreset.MEDIUM_SLOW, R.string.modem_config_slow_medium, 60),
    LONG_FAST(ModemPreset.LONG_FAST, R.string.modem_config_long, 60),
    LONG_SLOW(ModemPreset.LONG_SLOW, R.string.modem_config_slow_long, 240),
    VERY_LONG_SLOW(ModemPreset.VERY_LONG_SLOW, R.string.modem_config_very_long, 375);

    companion object {
        fun fromConfig(modemPreset: ModemPreset?): ChannelOption? {
            for (option in values()) {
                if (option.modemPreset == modemPreset)
                    return option
            }
            return null
        }

        val defaultMinBroadcastPeriod = VERY_LONG_SLOW.minBroadcastPeriodSecs
    }
}