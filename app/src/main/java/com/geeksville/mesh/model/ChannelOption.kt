package com.geeksville.mesh.model

import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R

enum class ChannelOption(
    val modemConfig: ChannelProtos.ChannelSettings.ModemConfig,
    val configRes: Int,
    val minBroadcastPeriodSecs: Int
) {
    SHORT(ChannelProtos.ChannelSettings.ModemConfig.Bw500Cr45Sf128, R.string.modem_config_short, 3),
    MEDIUM(
        ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128,
        R.string.modem_config_medium,
        12
    ),
    LONG(
        ChannelProtos.ChannelSettings.ModemConfig.Bw31_25Cr48Sf512,
        R.string.modem_config_long,
        240
    ),
    VERY_LONG(
        ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096,
        R.string.modem_config_very_long,
        375
    );

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