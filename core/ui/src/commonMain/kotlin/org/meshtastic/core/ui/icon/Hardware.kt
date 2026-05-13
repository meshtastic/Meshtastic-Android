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
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_bluetooth
import org.meshtastic.core.resources.ic_bluetooth_connected
import org.meshtastic.core.resources.ic_bluetooth_searching
import org.meshtastic.core.resources.ic_cached
import org.meshtastic.core.resources.ic_display_settings
import org.meshtastic.core.resources.ic_memory
import org.meshtastic.core.resources.ic_nfc
import org.meshtastic.core.resources.ic_settings_input_antenna
import org.meshtastic.core.resources.ic_speaker_phone
import org.meshtastic.core.resources.ic_terminal
import org.meshtastic.core.resources.ic_usb
import org.meshtastic.core.resources.ic_usb_off
import org.meshtastic.core.resources.ic_wifi

val MeshtasticIcons.BluetoothConnected: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth_connected)
val MeshtasticIcons.BluetoothSearching: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth_searching)
val MeshtasticIcons.UsbOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_usb_off)
val MeshtasticIcons.Antenna: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings_input_antenna)
val MeshtasticIcons.Speaker: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_speaker_phone)
val MeshtasticIcons.Reconnecting: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cached)
val MeshtasticIcons.Nfc: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_nfc)
val MeshtasticIcons.Bluetooth: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth)
val MeshtasticIcons.Wifi: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_wifi)
val MeshtasticIcons.Usb: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_usb)
val MeshtasticIcons.Serial: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_terminal)
val MeshtasticIcons.Memory: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_memory)
val MeshtasticIcons.DisplaySettings: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_display_settings)
