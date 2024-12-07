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

package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun BatteryInfo(
    modifier: Modifier = Modifier,
    batteryLevel: Int?,
    voltage: Float?
) {
    val infoString = "%d%% %.2fV".format(batteryLevel, voltage)
    val (image, level) = when (batteryLevel) {
        in 0 .. 4 -> R.drawable.ic_battery_alert to " $infoString"
        in 5 .. 14 -> R.drawable.ic_battery_outline to infoString
        in 15..34 -> R.drawable.ic_battery_low to infoString
        in 35..79 -> R.drawable.ic_battery_medium to infoString
        in 80..100 -> R.drawable.ic_battery_high to infoString
        101 -> R.drawable.ic_power_plug_24 to "%.2fV".format(voltage)
        else -> R.drawable.ic_battery_unknown to (voltage?.let { "%.2fV".format(it) } ?: "")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.height(18.dp),
            imageVector = ImageVector.vectorResource(id = image),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface,
        )
        Text(
            text = level,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

@PreviewLightDark
@Composable
fun BatteryInfoPreview(
    @PreviewParameter(BatteryInfoPreviewParameterProvider::class)
    batteryInfo: Pair<Int?, Float?>
) {
    AppTheme {
        BatteryInfo(
            batteryLevel = batteryInfo.first,
            voltage = batteryInfo.second
        )
    }
}

@Composable
@Preview
fun BatteryInfoPreviewSimple() {
    AppTheme {
        BatteryInfo(
            batteryLevel = 85,
            voltage = 3.7F
        )
    }
}

class BatteryInfoPreviewParameterProvider : PreviewParameterProvider<Pair<Int?, Float?>> {
    override val values: Sequence<Pair<Int?, Float?>>
        get() = sequenceOf(
            85 to 3.7F,
            2 to 3.7F,
            12 to 3.7F,
            28 to 3.7F,
            50 to 3.7F,
            101 to 4.9F,
            null to 4.5F,
            null to null
        )
}
