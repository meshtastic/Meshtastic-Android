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
package org.meshtastic.core.ui.component.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceMetrics.Companion.currentTime
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.random.Random

class NodePreviewParameterProvider : PreviewParameterProvider<Node> {
    val mickeyMouse =
        Node(
            num = 1955,
            user =
            User(
                id = "mickeyMouseId",
                long_name = "Mickey Mouse",
                short_name = "MM",
                hw_model = HardwareModel.TBEAM,
                role = Config.DeviceConfig.Role.ROUTER,
            ),
            position = Position(latitude_i = 338125110, longitude_i = -1179189760, altitude = 138, sats_in_view = 4),
            lastHeard = currentTime(),
            channel = 0,
            snr = 12.5F,
            rssi = -42,
            deviceMetrics =
            DeviceMetrics(
                channel_utilization = 2.4F,
                air_util_tx = 3.5F,
                battery_level = 85,
                voltage = 3.7F,
                uptime_seconds = 3600,
            ),
            isFavorite = true,
            hopsAway = 0,
        )

    val minnieMouse =
        mickeyMouse.copy(
            num = Random.nextInt(),
            user =
            User(
                long_name = "Minnie Mouse",
                short_name = "MiMo",
                id = "minnieMouseId",
                hw_model = HardwareModel.HELTEC_V3,
            ),
            snr = 12.5F,
            rssi = -42,
            position = Position(),
            hopsAway = 1,
        )

    private val donaldDuck =
        Node(
            num = Random.nextInt(),
            position = Position(latitude_i = 338052347, longitude_i = -1179208460, altitude = 121, sats_in_view = 66),
            lastHeard = currentTime() - 300,
            channel = 0,
            snr = 12.5F,
            rssi = -42,
            deviceMetrics =
            DeviceMetrics(
                channel_utilization = 2.4F,
                air_util_tx = 3.5F,
                battery_level = 85,
                voltage = 3.7F,
                uptime_seconds = 3600,
            ),
            user =
            User(
                id = "donaldDuckId",
                long_name = "Donald Duck, the Grand Duck of the Ducks",
                short_name = "DoDu",
                hw_model = HardwareModel.HELTEC_V3,
                public_key = ByteArray(32) { 1 }.toByteString(),
            ),
            environmentMetrics =
            EnvironmentMetrics(
                temperature = 28.0F,
                relative_humidity = 50.0F,
                barometric_pressure = 1013.25F,
                gas_resistance = 0.0F,
                voltage = 3.7F,
                current = 0.0F,
                iaq = 100,
                soil_temperature = 28.0F,
                soil_moisture = 50,
            ),
            paxcounter = Paxcount(wifi = 30, ble = 39, uptime = 420),
            isFavorite = true,
            hopsAway = 2,
        )

    private val unknown =
        donaldDuck.copy(
            user =
            User(id = "myId", long_name = "Meshtastic myId", short_name = "myId", hw_model = HardwareModel.UNSET),
            environmentMetrics = EnvironmentMetrics(),
            paxcounter = Paxcount(),
        )

    private val almostNothing = Node(num = Random.nextInt())

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
