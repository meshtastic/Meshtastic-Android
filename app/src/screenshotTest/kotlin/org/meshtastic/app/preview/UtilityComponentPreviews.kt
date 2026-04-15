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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.ConnectionsNavIcon
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.IAQScale
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.MaterialBluetoothSignalInfo
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.PlaceholderScreen
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun TransportIconPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("LoRa:", style = MaterialTheme.typography.labelSmall)
                TransportIcon(transport = 0, viaMqtt = false)
                Text("MQTT:", style = MaterialTheme.typography.labelSmall)
                TransportIcon(transport = 0, viaMqtt = true)
            }
        }
    }
}

@MultiPreview
@Composable
fun CopyIconButtonPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CopyIconButton(valueToCopy = "!a1b2c3d4")
            }
        }
    }
}

@MultiPreview
@Composable
fun MaterialBluetoothSignalInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Strong:", style = MaterialTheme.typography.labelSmall)
                MaterialBluetoothSignalInfo(rssi = -50)
                Text("Medium:", style = MaterialTheme.typography.labelSmall)
                MaterialBluetoothSignalInfo(rssi = -70)
                Text("Weak:", style = MaterialTheme.typography.labelSmall)
                MaterialBluetoothSignalInfo(rssi = -90)
            }
        }
    }
}

@MultiPreview
@Composable
fun NodeKeyStatusIconPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("PKC enabled, key match:", style = MaterialTheme.typography.labelSmall)
                NodeKeyStatusIcon(hasPKC = true, mismatchKey = false, publicKey = "testkey".encodeUtf8())
                Text("PKC enabled, key mismatch:", style = MaterialTheme.typography.labelSmall)
                NodeKeyStatusIcon(hasPKC = true, mismatchKey = true, publicKey = "testkey".encodeUtf8())
                Text("No PKC:", style = MaterialTheme.typography.labelSmall)
                NodeKeyStatusIcon(hasPKC = false, mismatchKey = false)
            }
        }
    }
}

@MultiPreview
@Composable
fun AutoLinkTextPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AutoLinkText(text = "Visit https://meshtastic.org for more info")
                AutoLinkText(text = "Plain text without any links")
            }
        }
    }
}

@MultiPreview
@Composable
fun SlidingSelectorPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlidingSelector(
                    options = listOf("Map", "List", "Chart"),
                    selectedOption = "Map",
                    onOptionSelected = {},
                ) { option ->
                    OptionLabel(text = option)
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun InsetDividerPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Default inset:")
                InsetDivider()
                Text("Custom inset (32dp):")
                InsetDivider(inset = 32.dp)
                Text("Asymmetric inset:")
                InsetDivider(startInset = 48.dp, endInset = 16.dp)
            }
        }
    }
}

@MultiPreview
@Composable
fun PlaceholderScreenPreview() {
    AppTheme(isSystemInDarkTheme()) { Surface { PlaceholderScreen(name = "Feature Coming Soon") } }
}

@MultiPreview
@Composable
fun IAQScalePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IAQScale()
            }
        }
    }
}

@MultiPreview
@Composable
fun ConnectionsNavIconPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectionsNavIcon(connectionState = ConnectionState.Connected, deviceType = DeviceType.BLE)
                ConnectionsNavIcon(connectionState = ConnectionState.Connecting, deviceType = DeviceType.TCP)
                ConnectionsNavIcon(connectionState = ConnectionState.Disconnected, deviceType = null)
                ConnectionsNavIcon(connectionState = ConnectionState.DeviceSleep, deviceType = DeviceType.USB)
            }
        }
    }
}
