package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R

enum class ChannelOption(val modemConfig: MeshProtos.ChannelSettings.ModemConfig, val configRes: Int) {
    SHORT(MeshProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128, R.string.modem_config_short),
    MEDIUM(MeshProtos.ChannelSettings.ModemConfig.Bw500Cr45Sf128, R.string.modem_config_medium),
    LONG(MeshProtos.ChannelSettings.ModemConfig.Bw31_25Cr48Sf512, R.string.modem_config_long),
    VERY_LONG(MeshProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096, R.string.modem_config_very_long)
}