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
package org.meshtastic.core.ui.component.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

class NodePreviewParameterProvider : PreviewParameterProvider<Node> {
    val mickeyMouse =
        Node(
            num = 1955,
            user =
            User.Builder().also { wb ->
            wb.id = "mickeyMouseId"
            wb.long_name = "Mickey Mouse"
            wb.short_name = "MM"
            wb.hw_model = HardwareModel.TBEAM
            wb.role = Config.DeviceConfig.Role.ROUTER
            }.build(),
            position = Position.Builder().also { wb ->wb.latitude_i = 338125110; wb.longitude_i = -1179189760; wb.altitude = 138; wb.sats_in_view = 4}.build(),
            lastHeard = 1700000000,
            channel = 0,
            snr = 12.5F,
            rssi = -42,
            deviceMetrics =
            DeviceMetrics.Builder().also { wb ->
            wb.channel_utilization = 2.4F
            wb.air_util_tx = 3.5F
            wb.battery_level = 85
            wb.voltage = 3.7F
            wb.uptime_seconds = 3600
            }.build(),
            isFavorite = true,
            hopsAway = 0,
        )

    val minnieMouse =
        mickeyMouse.copy(
            num = 1928,
            user =
            User.Builder().also { wb ->
            wb.long_name = "Minnie Mouse"
            wb.short_name = "MiMo"
            wb.id = "minnieMouseId"
            wb.hw_model = HardwareModel.HELTEC_V3
            }.build(),
            snr = 12.5F,
            rssi = -42,
            position = Position.Builder().build(),
            hopsAway = 1,
        )

    private val donaldDuck =
        Node(
            num = 1934,
            position = Position.Builder().also { wb ->wb.latitude_i = 338052347; wb.longitude_i = -1179208460; wb.altitude = 121; wb.sats_in_view = 66}.build(),
            lastHeard = 1699999700,
            channel = 0,
            snr = 12.5F,
            rssi = -42,
            deviceMetrics =
            DeviceMetrics.Builder().also { wb ->
            wb.channel_utilization = 2.4F
            wb.air_util_tx = 3.5F
            wb.battery_level = 85
            wb.voltage = 3.7F
            wb.uptime_seconds = 3600
            }.build(),
            user =
            User.Builder().also { wb ->
            wb.id = "donaldDuckId"
            wb.long_name = "Donald Duck, the Grand Duck of the Ducks"
            wb.short_name = "DoDu"
            wb.hw_model = HardwareModel.HELTEC_V3
            wb.public_key = ByteArray(32) { 1 }.toByteString()
            }.build(),
            environmentMetrics =
            EnvironmentMetrics.Builder().also { wb ->
            wb.temperature = 28.0F
            wb.relative_humidity = 50.0F
            wb.barometric_pressure = 1013.25F
            wb.gas_resistance = 0.0F
            wb.voltage = 3.7F
            wb.current = 0.0F
            wb.iaq = 100
            wb.soil_temperature = 28.0F
            wb.soil_moisture = 50
            }.build(),
            paxcounter = Paxcount.Builder().also { wb ->wb.wifi = 30; wb.ble = 39; wb.uptime = 420}.build(),
            isFavorite = true,
            hopsAway = 2,
        )

    val unknown =
        donaldDuck.copy(
            user =
            User.Builder().also { wb ->wb.id = "myId"; wb.long_name = "Meshtastic myId"; wb.short_name = "myId"; wb.hw_model = HardwareModel.UNSET}.build(),
            environmentMetrics = EnvironmentMetrics.Builder().build(),
            paxcounter = Paxcount.Builder().build(),
        )

    private val almostNothing = Node(num = 9999)

    override val values: Sequence<Node>
        get() =
            sequenceOf(
                mickeyMouse, // "this" node
                unknown,
                almostNothing,
                minnieMouse,
                donaldDuck,
            )
}
