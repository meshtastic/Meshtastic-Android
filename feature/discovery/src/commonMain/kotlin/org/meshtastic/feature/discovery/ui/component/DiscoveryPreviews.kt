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
package org.meshtastic.feature.discovery.ui.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Suppress("MagicNumber", "PreviewPublic") // fake data; public so :screenshot-tests can reference it
@Composable
fun PreviewDiscoveryPresetResult() {
    AppTheme {
        Surface {
            PresetResultCard(
                result =
                DiscoveryPresetResultEntity(
                    sessionId = 1,
                    presetName = "LongFast",
                    dwellDurationSeconds = 900,
                    uniqueNodes = 12,
                    directNeighborCount = 5,
                    meshNeighborCount = 7,
                    infrastructureNodeCount = 2,
                    messageCount = 34,
                    sensorPacketCount = 18,
                    avgChannelUtilization = 14.2,
                    avgAirtimeRate = 3.1,
                    packetSuccessRate = 96.5,
                    packetFailureRate = 3.5,
                    numPacketsTx = 21,
                    numPacketsRx = 412,
                    numPacketsRxBad = 6,
                    numRxDupe = 11,
                    numTxRelay = 38,
                    numOnlineNodes = 12,
                    numTotalNodes = 40,
                ),
                nodes = emptyList(),
                rank = 1,
            )
        }
    }
}

@PreviewLightDark
@Suppress("MagicNumber", "PreviewPublic") // fake data; public so :screenshot-tests can reference it
@Composable
fun PreviewDiscoveryDwellProgress() {
    AppTheme { Surface { DwellProgressIndicator(presetName = "LongFast", remainingSeconds = 312, totalSeconds = 900) } }
}
