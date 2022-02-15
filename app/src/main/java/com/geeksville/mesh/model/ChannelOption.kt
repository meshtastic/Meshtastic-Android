package com.geeksville.mesh.model

import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R

enum class ChannelOption(
    val modemConfig: ChannelProtos.ChannelSettings.ModemConfig,
    val configRes: Int,
    val minBroadcastPeriodSecs: Int
) {
    SHORT_FAST(ChannelProtos.ChannelSettings.ModemConfig.ShortFast,R.string.modem_config_short, 30),
    SHORT_SLOW(ChannelProtos.ChannelSettings.ModemConfig.ShortSlow, R.string.modem_config_slow_short, 30),
    MED_FAST(ChannelProtos.ChannelSettings.ModemConfig.MidFast,R.string.modem_config_medium, 60),
    MED_SLOW(ChannelProtos.ChannelSettings.ModemConfig.MidSlow,R.string.modem_config_slow_medium, 60),
    LONG_FAST(ChannelProtos.ChannelSettings.ModemConfig.LongFast, R.string.modem_config_long, 240),
    LONG_SLOW(ChannelProtos.ChannelSettings.ModemConfig.LongSlow, R.string.modem_config_slow_long, 375),
    VERY_LONG(ChannelProtos.ChannelSettings.ModemConfig.VLongSlow, R.string.modem_config_very_long, 375);

    companion object {
        fun fromConfig(modemConfig: ChannelProtos.ChannelSettings.ModemConfig?): ChannelOption? {
            for (option in values()) {
                if (option.modemConfig == modemConfig)
                    return option
            }
            return null
        }

        val defaultMinBroadcastPeriod = VERY_LONG.minBroadcastPeriodSecs
    }
}