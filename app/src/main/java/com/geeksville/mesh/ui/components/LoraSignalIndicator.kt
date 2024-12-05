/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

private const val SNR_GOOD_THRESHOLD = -7f
private const val SNR_FAIR_THRESHOLD = -15f

private const val RSSI_GOOD_THRESHOLD = -115
private const val RSSI_FAIR_THRESHOLD = -126

private enum class Quality(
    val nameRes: Int,
    val imageVector: ImageVector,
    val color: Color
) {
    NONE(R.string.none_quality, Icons.Default.SignalCellularAlt1Bar, Color.Red),
    BAD(R.string.bad, Icons.Default.SignalCellularAlt2Bar, Color(red = 247, green = 147, blue = 26)),
    FAIR(R.string.fair, Icons.Default.SignalCellularAlt, Color(red = 255, green = 230, blue = 0)),
    GOOD(R.string.good, Icons.Default.SignalCellular4Bar, Color.Green)
}

/**
 * Displays the `snr` and `rssi` color coded based on the signal quality, along with
 * a human readable description and related icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeSignalQuality(snr: Float, rssi: Int, modifier: Modifier = Modifier) {
    val quality = determineSignalQuality(snr, rssi)
    FlowRow(
        modifier = modifier,
        maxLines = 1,
    ) {
        Snr(snr)
        Spacer(Modifier.width(8.dp))
        Rssi(rssi)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${stringResource(R.string.signal)} ${stringResource(quality.nameRes)}",
            fontSize = MaterialTheme.typography.button.fontSize,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = quality.imageVector,
            contentDescription = stringResource(R.string.signal_quality),
            tint = quality.color
        )
    }
}

/**
 * Displays the `snr` and `rssi` with color depending on the values respectively.
 */
@Composable
fun SnrAndRssi(snr: Float, rssi: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Snr(snr)
        Rssi(rssi)
    }
}

/**
 * Displays a human readable description and icon representing the signal quality.
 */
@Composable
fun LoraSignalIndicator(snr: Float, rssi: Int) {

    val quality = determineSignalQuality(snr, rssi)

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Icon(
            imageVector = quality.imageVector,
            contentDescription = stringResource(R.string.signal_quality),
            tint = quality.color
        )
        Text(text = "${stringResource(R.string.signal)} ${stringResource(quality.nameRes)}")
    }
}

@Composable
private fun Snr(snr: Float) {
    val color: Color = if (snr > SNR_GOOD_THRESHOLD) {
        Quality.GOOD.color
    } else if (snr > SNR_FAIR_THRESHOLD) {
        Quality.FAIR.color
    } else {
        Quality.BAD.color
    }

    Text(
        text = "%s %.2fdB".format(stringResource(id = R.string.snr), snr),
        color = color,
        fontSize = MaterialTheme.typography.button.fontSize
    )
}

@Composable
private fun Rssi(rssi: Int) {
    val color: Color = if (rssi > RSSI_GOOD_THRESHOLD) {
        Quality.GOOD.color
    } else if (rssi > RSSI_FAIR_THRESHOLD) {
        Quality.FAIR.color
    } else {
        Quality.BAD.color
    }
    Text(
        text = "%s %ddBm".format(stringResource(id = R.string.rssi), rssi),
        color = color,
        fontSize = MaterialTheme.typography.button.fontSize
    )
}

private fun determineSignalQuality(snr: Float, rssi: Int): Quality = when {
    snr > SNR_GOOD_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> Quality.GOOD
    snr > SNR_GOOD_THRESHOLD && rssi > RSSI_FAIR_THRESHOLD -> Quality.FAIR
    snr > SNR_FAIR_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> Quality.FAIR
    snr <= SNR_FAIR_THRESHOLD && rssi <= RSSI_FAIR_THRESHOLD -> Quality.NONE
    else -> Quality.BAD
}
