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

package com.geeksville.mesh.ui.connections.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.connections.DeviceType

@Suppress("LambdaParameterEventTrailing")
@Composable
fun ConnectionsSegmentedBar(modifier: Modifier = Modifier, onClickDeviceType: (DeviceType) -> Unit) {
    var selectedItem by remember { mutableStateOf(Item.BLUETOOTH) }

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        Item.entries.forEachIndexed { index, item ->
            val text = stringResource(item.textRes)
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, Item.entries.size),
                onClick = {
                    selectedItem = item
                    onClickDeviceType(item.deviceType)
                },
                selected = item == selectedItem,
                icon = { Icon(imageVector = item.imageVector, contentDescription = text) },
                label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private enum class Item(val imageVector: ImageVector, @StringRes val textRes: Int, val deviceType: DeviceType) {
    BLUETOOTH(imageVector = Icons.Rounded.Bluetooth, textRes = R.string.bluetooth, deviceType = DeviceType.BLE),
    NETWORK(imageVector = Icons.Rounded.Wifi, textRes = R.string.network, deviceType = DeviceType.TCP),
    SERIAL(imageVector = Icons.Rounded.Usb, textRes = R.string.serial, deviceType = DeviceType.USB),
}

@Preview(showBackground = true)
@Composable
private fun ConnectionsSegmentedBarPreview() {
    AppTheme { ConnectionsSegmentedBar {} }
}
