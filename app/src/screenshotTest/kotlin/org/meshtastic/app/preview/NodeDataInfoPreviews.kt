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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.ui.component.ChannelInfo
import org.meshtastic.core.ui.component.DistanceInfo
import org.meshtastic.core.ui.component.HopsInfo
import org.meshtastic.core.ui.component.IconInfo
import org.meshtastic.core.ui.component.LastHeardInfo
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.SnrAndRssi
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun DistanceInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DistanceInfo(distance = "2.4 km")
                DistanceInfo(distance = "150 m")
                DistanceInfo(distance = "0 m")
            }
        }
    }
}

@MultiPreview
@Composable
fun LastHeardInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("With label:", style = MaterialTheme.typography.labelSmall)
                LastHeardInfo(lastHeard = (nowSeconds - 300).toInt())
                Text("Without label:", style = MaterialTheme.typography.labelSmall)
                LastHeardInfo(lastHeard = (nowSeconds - 3600).toInt(), showLabel = false)
                Text("Never heard:", style = MaterialTheme.typography.labelSmall)
                LastHeardInfo(lastHeard = 0)
            }
        }
    }
}

@MultiPreview
@Composable
fun HopsInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HopsInfo(hops = 0)
                HopsInfo(hops = 1)
                HopsInfo(hops = 3)
                HopsInfo(hops = 7)
            }
        }
    }
}

@MultiPreview
@Composable
fun ChannelInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelInfo(channel = 0)
                ChannelInfo(channel = 1)
                ChannelInfo(channel = 7)
            }
        }
    }
}

@MultiPreview
@Composable
fun IconInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconInfo(
                    icon = MeshtasticIcons.Temperature,
                    contentDescription = "Temperature",
                    label = "Temperature",
                    text = "24.5 C",
                )
                IconInfo(icon = MeshtasticIcons.Temperature, contentDescription = "Temperature", text = "18.2 C")
            }
        }
    }
}

@MultiPreview
@Composable
fun SnrRssiPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("SnrAndRssi:", style = MaterialTheme.typography.labelSmall)
                SnrAndRssi(snr = 10f, rssi = -90)
                SnrAndRssi(snr = -5f, rssi = -120)
                Text("Snr alone:", style = MaterialTheme.typography.labelSmall)
                Snr(snr = 10f)
                Snr(snr = -15f)
                Text("Rssi alone:", style = MaterialTheme.typography.labelSmall)
                Rssi(rssi = -80)
                Rssi(rssi = -130)
            }
        }
    }
}
