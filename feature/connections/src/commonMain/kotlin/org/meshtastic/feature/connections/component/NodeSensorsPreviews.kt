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
package org.meshtastic.feature.connections.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.connections.model.BearerRowState
import org.meshtastic.feature.connections.model.BearerRowUi
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.SensorRowState
import org.meshtastic.feature.connections.model.SensorRowUi
import org.meshtastic.feature.connections.model.SensorsUi
import org.meshtastic.feature.connections.ui.components.NodeStatusCard
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.sensors.SensorKind
import org.meshtastic.node.sensors.SensorUnread
import org.meshtastic.node.transport.Bearer
import kotlin.time.Duration.Companion.minutes

@PreviewLightDark
@Composable
private fun NodeStatusCardSensorsPreview() {
    AppTheme { SensorsCard(previewSensorsFound()) }
}

@PreviewLightDark
@Composable
private fun NodeStatusCardSensorsFailingPreview() {
    AppTheme { SensorsCard(previewSensorsFailing()) }
}

@PreviewLightDark
@Composable
private fun NodeStatusCardSensorBusFailedPreview() {
    AppTheme { SensorsCard(SensorsUi.BusFailed("/dev/i2c-1: Permission denied")) }
}

@Composable
internal fun SensorsCard(sensors: SensorsUi) {
    NodeStatusCard(
        status =
        NodeStatusUi(
            phase = NodePhase.Running,
            nodeId = "!3a5806be",
            longName = "Meshtastic 06be",
            shortName = "06be",
            peerCount = 2,
            failure = null,
            settingsReset = false,
            bearers = listOf(BearerRowUi(Bearer.UDP, true, true, BearerRowState.Active, PREVIEW_RX, PREVIEW_TX, 0)),
            sensors = sensors,
        ),
        onSetBearer = { _, _ -> },
        onSetExtraRadio = { _, _ -> },
        onRefreshRequirements = {},
        onRequestUsbPermission = {},
    )
}

private fun previewSensorsFound(): SensorsUi = SensorsUi.Found(
    listOf(
        SensorRowUi("INA219", "0x40", listOf(SensorKind.POWER), SensorRowState.Reading(null)),
        SensorRowUi("SHTC1/SHTC3", "0x70", listOf(SensorKind.ENVIRONMENT), SensorRowState.Reading(PREVIEW_AGE)),
        SensorRowUi(
            "SCD4X",
            "0x62",
            listOf(SensorKind.ENVIRONMENT, SensorKind.AIR_QUALITY),
            SensorRowState.Waiting,
        ),
        SensorRowUi("MPU6050", "0x68", emptyList(), SensorRowState.Unread(SensorUnread.NO_DRIVER)),
        SensorRowUi(null, "0x1c", emptyList(), SensorRowState.Unread(SensorUnread.UNIDENTIFIED)),
    ),
)

private fun previewSensorsFailing(): SensorsUi = SensorsUi.Found(
    listOf(
        SensorRowUi("INA219", "0x40", listOf(SensorKind.POWER), SensorRowState.Reading(null)),
        SensorRowUi(
            "BME280",
            "0x76",
            listOf(SensorKind.ENVIRONMENT),
            SensorRowState.Failing("Remote I/O error", PREVIEW_AGE),
        ),
        SensorRowUi("PMSA003I", "0x12", listOf(SensorKind.AIR_QUALITY), SensorRowState.Failing("timed out", null)),
    ),
)

private val PREVIEW_AGE = 4.minutes
private const val PREVIEW_RX = 42L
private const val PREVIEW_TX = 7L
