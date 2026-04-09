/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

val MeshtasticIcons.Bluetooth: ImageVector
    get() = Icons.Rounded.Bluetooth
val MeshtasticIcons.Usb: ImageVector
    get() = Icons.Rounded.Usb
val MeshtasticIcons.Wifi: ImageVector
    get() = Icons.Rounded.Wifi

val MeshtasticIcons.BluetoothConnected: ImageVector
    get() = Icons.Rounded.BluetoothConnected
val MeshtasticIcons.BluetoothSearching: ImageVector
    get() = Icons.Rounded.BluetoothSearching
val MeshtasticIcons.UsbOff: ImageVector
    get() = Icons.Rounded.UsbOff
val MeshtasticIcons.Serial: ImageVector
    get() = Icons.AutoMirrored.Rounded.Forward
val MeshtasticIcons.Antenna: ImageVector
    get() = Icons.Rounded.SettingsInputAntenna
val MeshtasticIcons.Speaker: ImageVector
    get() = Icons.Rounded.SpeakerPhone
val MeshtasticIcons.Reconnecting: ImageVector
    get() = Icons.Rounded.Cached
val MeshtasticIcons.Nfc: ImageVector
    get() = Icons.Rounded.Nfc
