/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SignalCellular0Bar
import org.meshtastic.core.ui.icon.SignalCellular1Bar
import org.meshtastic.core.ui.icon.SignalCellular2Bar
import org.meshtastic.core.ui.icon.SignalCellular3Bar
import org.meshtastic.core.ui.icon.SignalCellular4Bar
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.strings.R as Res

private const val SIZE_ICON = 20

/**
 * A composable that displays a signal strength indicator with an icon and optional text value. The icon and its color
 * change based on the number of signal bars.
 *
 * @param modifier Modifier for this composable.
 * @param signalBars The number of signal bars, typically from 0 to 4. Values outside this range (e.g., < 0) will
 *   display a "signal off" or unknown state icon.
 * @param signalStrengthValue Optional text to display next to the icon, such as dBm or SNR value.
 */
@Suppress("MagicNumber")
@Composable
fun MaterialSignalInfo(
    signalBars: Int,
    modifier: Modifier = Modifier,
    signalStrengthValue: String? = null,
    typeIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val (iconVector, iconTint) =
            when (signalBars) {
                0 -> MeshtasticIcons.SignalCellular0Bar to MaterialTheme.colorScheme.StatusRed
                1 -> MeshtasticIcons.SignalCellular1Bar to MaterialTheme.colorScheme.StatusRed
                2 -> MeshtasticIcons.SignalCellular2Bar to MaterialTheme.colorScheme.StatusOrange
                3 -> MeshtasticIcons.SignalCellular3Bar to MaterialTheme.colorScheme.StatusYellow
                4 -> MeshtasticIcons.SignalCellular4Bar to MaterialTheme.colorScheme.StatusGreen
                else -> Icons.Rounded.SignalCellularOff to MaterialTheme.colorScheme.onSurfaceVariant
            }

        val foregroundPainter = typeIcon?.let { rememberVectorPainter(typeIcon) }
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = iconTint,
            modifier =
            Modifier.size(SIZE_ICON.dp).drawWithContent {
                drawContent()
                @Suppress("MagicNumber")
                if (foregroundPainter != null) {
                    val badgeSize = size.width * .45f
                    with(foregroundPainter) {
                        draw(Size(badgeSize, badgeSize), colorFilter = ColorFilter.tint(iconTint))
                    }
                }
            },
        )

        signalStrengthValue?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun MaterialBluetoothSignalInfo(rssi: Int, modifier: Modifier = Modifier) {
    MaterialSignalInfo(
        modifier = modifier,
        signalBars = getBluetoothSignalBars(rssi = rssi),
        signalStrengthValue = stringResource(Res.string.dbm_value, rssi),
        typeIcon = Icons.Rounded.Bluetooth,
    )
}

@Suppress("MagicNumber")
private fun getBluetoothSignalBars(rssi: Int): Int = when {
    rssi > -60 -> 4 // Excellent
    rssi > -70 -> 3 // Good
    rssi > -80 -> 2 // Fair
    rssi > -90 -> 1 // Weak
    else -> 0 // Poor/No Signal
}

class SignalStrengthProvider : PreviewParameterProvider<Int> {
    override val values: Sequence<Int> = sequenceOf(-95, -85, -75, -65, -55)
}

@PreviewLightDark
@Composable
private fun MaterialBluetoothSignalInfoPreview(@PreviewParameter(SignalStrengthProvider::class) rssi: Int) {
    AppTheme { Surface { MaterialBluetoothSignalInfo(rssi = rssi) } }
}
