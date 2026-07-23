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
@file:Suppress("MagicNumber")

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.snrLimit
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bad
import org.meshtastic.core.resources.fair
import org.meshtastic.core.resources.good
import org.meshtastic.core.resources.ic_signal_cellular_4_bar
import org.meshtastic.core.resources.ic_signal_cellular_alt
import org.meshtastic.core.resources.ic_signal_cellular_alt_1_bar
import org.meshtastic.core.resources.ic_signal_cellular_alt_2_bar
import org.meshtastic.core.resources.none_quality
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.signal
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.snr
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset

// Fixed-threshold SNR colors retained for contexts without an active preset (e.g. traceroute hop coloring in
// AnnotatedStrings). Per-node signal quality uses preset-relative thresholds instead — see [determineSignalQuality].
const val SNR_GOOD_THRESHOLD = -7f
const val SNR_FAIR_THRESHOLD = -15f

const val RSSI_GOOD_THRESHOLD = -115
const val RSSI_FAIR_THRESHOLD = -126

// SNR offsets (dB) below a preset's demodulation floor that delimit the quality bands, matching Meshtastic-Apple's
// getSnrColor(): within 5.5 dB below the limit is FAIR, within 7.5 dB is BAD, further down is NONE.
private const val SNR_FAIR_OFFSET = 5.5f
private const val SNR_BAD_OFFSET = 7.5f

@Stable
enum class Quality(
    @Stable val nameRes: StringResource,
    @Stable val icon: DrawableResource,
    @Stable val color: @Composable () -> Color,
) {
    NONE(Res.string.none_quality, Res.drawable.ic_signal_cellular_alt_1_bar, { colorScheme.StatusRed }),
    BAD(Res.string.bad, Res.drawable.ic_signal_cellular_alt_2_bar, { colorScheme.StatusOrange }),
    FAIR(Res.string.fair, Res.drawable.ic_signal_cellular_alt, { colorScheme.StatusYellow }),
    GOOD(Res.string.good, Res.drawable.ic_signal_cellular_4_bar, { colorScheme.StatusGreen }),
}

/**
 * Displays the `snr` and `rssi` color coded based on the signal quality, along with a human readable description and
 * related icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeSignalQuality(
    snr: Float,
    rssi: Int,
    modifier: Modifier = Modifier,
    modemPreset: ModemPreset? = LocalModemPreset.current,
) {
    val quality = determineSignalQuality(snr, modemPreset)
    FlowRow(
        modifier = modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Snr(snr, modemPreset = modemPreset)
        Rssi(rssi)
        Text(
            text = "${stringResource(Res.string.signal)} ${stringResource(quality.nameRes)}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Icon(
            modifier = Modifier.size(SIZE_ICON_DP.dp),
            imageVector = vectorResource(quality.icon),
            contentDescription = stringResource(Res.string.signal_quality),
            tint = quality.color(),
        )
    }
}

private const val SIZE_ICON_DP = 16

/** Displays the `snr` and `rssi` with color depending on the values respectively. */
@Composable
fun SnrAndRssi(snr: Float, rssi: Int, modemPreset: ModemPreset? = LocalModemPreset.current) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Snr(snr, modemPreset = modemPreset)
        Rssi(rssi)
    }
}

/** Displays a human readable description and icon representing the signal quality. */
@Composable
fun LoraSignalIndicator(
    snr: Float,
    modifier: Modifier = Modifier,
    modemPreset: ModemPreset? = LocalModemPreset.current,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val quality = determineSignalQuality(snr, modemPreset)
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(8.dp),
    ) {
        Icon(
            modifier = Modifier.size(SIZE_ICON_DP.dp),
            imageVector = vectorResource(quality.icon),
            contentDescription = stringResource(Res.string.signal_quality),
            tint = quality.color(),
        )
        Text(
            text = "${stringResource(Res.string.signal)} ${stringResource(quality.nameRes)}",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
fun Snr(snr: Float, modifier: Modifier = Modifier, modemPreset: ModemPreset? = LocalModemPreset.current) {
    val color: Color = determineSignalQuality(snr, modemPreset).color.invoke()

    Text(
        modifier = modifier,
        text = "${stringResource(Res.string.snr)} ${MetricFormatter.snr(snr, decimalPlaces = 2)}",
        color = color,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun Rssi(rssi: Int, modifier: Modifier = Modifier, label: String = stringResource(Res.string.rssi)) {
    val color: Color =
        if (rssi > RSSI_GOOD_THRESHOLD) {
            Quality.GOOD.color.invoke()
        } else if (rssi > RSSI_FAIR_THRESHOLD) {
            Quality.FAIR.color.invoke()
        } else {
            Quality.BAD.color.invoke()
        }
    Text(
        modifier = modifier,
        text = "$label ${MetricFormatter.rssi(rssi)}",
        color = color,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * Rates link quality from SNR relative to the active modem preset's demodulation floor ([ModemPreset.snrLimit]). A
 * given SNR means different things per preset — e.g. -15 dB is excellent on LongSlow (SF12) but unusable on ShortFast
 * (SF7) — so a fixed threshold mis-rates most presets.
 *
 * RSSI is intentionally not considered: without the noise floor it cannot indicate whether a signal is demodulable, so
 * SNR-versus-preset-limit is the meaningful measure (it is still shown to the user via [Rssi]). See #5446.
 *
 * A null/unknown [modemPreset] falls back to the LongFast default limit.
 */
fun determineSignalQuality(snr: Float, modemPreset: ModemPreset?): Quality {
    val limit = modemPreset.snrLimit
    return when {
        snr > limit -> Quality.GOOD
        snr > limit - SNR_FAIR_OFFSET -> Quality.FAIR
        snr >= limit - SNR_BAD_OFFSET -> Quality.BAD
        else -> Quality.NONE
    }
}
