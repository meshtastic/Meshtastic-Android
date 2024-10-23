package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    BAD(R.string.bad, Icons.Default.SignalCellularAlt2Bar, Color(247, 147, 26)),
    FAIR(R.string.fair, Icons.Default.SignalCellularAlt, Color(255, 230, 0)),
    GOOD(R.string.good, Icons.Default.SignalCellular4Bar, Color.Green)
}

@Composable
fun Snr(snr: Float) {
    val color: Color = if (snr > SNR_GOOD_THRESHOLD)
        Quality.GOOD.color
    else if (snr > SNR_FAIR_THRESHOLD)
        Quality.FAIR.color
    else
        Quality.BAD.color

    Text(
        text = "%s %.2fdB".format(
            stringResource(id = R.string.snr),
            snr
        ),
            color = color,
            fontSize = MaterialTheme.typography.button.fontSize
        )
}

@Composable
fun Rssi(rssi: Int) {
    val color: Color = if (rssi > RSSI_GOOD_THRESHOLD)
        Quality.GOOD.color
    else if (rssi > RSSI_FAIR_THRESHOLD)
        Quality.FAIR.color
    else
        Quality.BAD.color
    Text(
        text = "%s %ddB".format(
            stringResource(id = R.string.rssi),
            rssi
        ),
        color = color,
        fontSize = MaterialTheme.typography.button.fontSize
    )
}

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

private fun determineSignalQuality(snr: Float, rssi: Int) : Quality {
    return if (snr > SNR_GOOD_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD)
        Quality.GOOD
    else if (snr > SNR_GOOD_THRESHOLD && rssi > RSSI_FAIR_THRESHOLD || snr > SNR_FAIR_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD)
        Quality.FAIR
    else if (snr <= SNR_FAIR_THRESHOLD && rssi <= RSSI_FAIR_THRESHOLD)
        Quality.NONE
    else
        Quality.BAD
}
