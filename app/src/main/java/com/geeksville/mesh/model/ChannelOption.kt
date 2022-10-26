package com.geeksville.mesh.model

import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig.ModemPreset
import com.geeksville.mesh.R

enum class ChannelOption(
    val modemPreset: ModemPreset,
    val configRes: Int,
) {
    SHORT_FAST(ModemPreset.SHORT_FAST, R.string.modem_config_short),
    SHORT_SLOW(ModemPreset.SHORT_SLOW, R.string.modem_config_slow_short),
    MEDIUM_FAST(ModemPreset.MEDIUM_FAST, R.string.modem_config_medium),
    MEDIUM_SLOW(ModemPreset.MEDIUM_SLOW, R.string.modem_config_slow_medium),
    LONG_FAST(ModemPreset.LONG_FAST, R.string.modem_config_long),
    LONG_SLOW(ModemPreset.LONG_SLOW, R.string.modem_config_slow_long),
    VERY_LONG_SLOW(ModemPreset.VERY_LONG_SLOW, R.string.modem_config_very_long);

    companion object {
        fun fromConfig(modemPreset: ModemPreset?): ChannelOption? {
            for (option in values()) {
                if (option.modemPreset == modemPreset) return option
            }
            return null
        }
    }
}