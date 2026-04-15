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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.feature.node.component.CooldownOutlinedIconButton
import org.meshtastic.feature.node.component.InfoCard
import org.meshtastic.feature.node.component.NodeStatusIcons
import org.meshtastic.feature.node.metrics.DeleteItem
import org.meshtastic.feature.node.metrics.LegendIndicator
import org.meshtastic.feature.node.metrics.MetricIndicator
import org.meshtastic.feature.node.metrics.MetricLogItem
import org.meshtastic.feature.node.metrics.MetricValueRow
import org.meshtastic.feature.node.metrics.SelectableMetricCard
import org.meshtastic.feature.node.metrics.TimeFrameSelector
import org.meshtastic.feature.node.model.TimeFrame

@MultiPreview
@Composable
fun InfoCardPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoCard(text = "Battery", value = "85%", icon = MeshtasticIcons.Favorite)
                InfoCard(text = "Temperature", value = "24.5 C", icon = MeshtasticIcons.Temperature)
                InfoCard(text = "Humidity", value = "62%", icon = MeshtasticIcons.Humidity)
            }
        }
    }
}

@MultiPreview
@Composable
fun NodeStatusIconsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Connected, favorite, this node:", style = MaterialTheme.typography.labelMedium)
                NodeStatusIcons(
                    isThisNode = true,
                    isUnmessageable = false,
                    isFavorite = true,
                    isMuted = false,
                    connectionState = ConnectionState.Connected,
                )
                Text("Disconnected, muted:", style = MaterialTheme.typography.labelMedium)
                NodeStatusIcons(
                    isThisNode = false,
                    isUnmessageable = false,
                    isFavorite = false,
                    isMuted = true,
                    connectionState = ConnectionState.Disconnected,
                )
                Text("Connecting, unmessageable:", style = MaterialTheme.typography.labelMedium)
                NodeStatusIcons(
                    isThisNode = false,
                    isUnmessageable = true,
                    isFavorite = false,
                    isMuted = false,
                    connectionState = ConnectionState.Connecting,
                )
                Text("Device sleep:", style = MaterialTheme.typography.labelMedium)
                NodeStatusIcons(
                    isThisNode = false,
                    isUnmessageable = false,
                    isFavorite = true,
                    isMuted = false,
                    connectionState = ConnectionState.DeviceSleep,
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun TimeFrameSelectorPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeFrameSelector(
                    selectedTimeFrame = TimeFrame.TWENTY_FOUR_HOURS,
                    availableTimeFrames = TimeFrame.entries,
                    onTimeFrameSelected = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun MetricLogComponentsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricLogItem(icon = MeshtasticIcons.Temperature, text = "24.5 C", contentDescription = "Temperature")
                MetricLogItem(icon = MeshtasticIcons.Humidity, text = "62%", contentDescription = "Humidity")
                DeleteItem(onClick = {})
            }
        }
    }
}

@MultiPreview
@Composable
fun SelectableMetricCardPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectableMetricCard(isSelected = true, onClick = {}) {
                    Text("Selected metric card", modifier = Modifier.padding(16.dp))
                }
                SelectableMetricCard(isSelected = false, onClick = {}) {
                    Text("Unselected metric card", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun MetricValueRowPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetricValueRow(color = Color.Red, text = "Max: 35.2 C")
                MetricValueRow(color = Color.Blue, text = "Min: 18.7 C")
                MetricValueRow(color = Color.Green, text = "Avg: 24.5 C")
            }
        }
    }
}

@MultiPreview
@Composable
fun LegendIndicatorsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Legend indicators:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendIndicator(color = Color.Red, isLine = false)
                    LegendIndicator(color = Color.Blue, isLine = true)
                    LegendIndicator(color = Color.Green, isLine = false)
                }
                Text("Metric indicators:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricIndicator(color = Color.Red)
                    MetricIndicator(color = Color.Blue)
                    MetricIndicator(color = Color.Green)
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun CooldownButtonsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CooldownIconButton(onClick = {}, cooldownTimestamp = null) {
                    Icon(MeshtasticIcons.Temperature, contentDescription = "Request")
                }
                CooldownOutlinedIconButton(onClick = {}, cooldownTimestamp = null) {
                    Icon(MeshtasticIcons.Humidity, contentDescription = "Request")
                }
            }
        }
    }
}
