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

package com.geeksville.mesh.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.DeviceMetrics.Companion.currentTime
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.deviceMetrics
import com.geeksville.mesh.environmentMetrics
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.paxcount
import com.geeksville.mesh.position
import com.geeksville.mesh.user
import com.google.protobuf.ByteString
import kotlin.random.Random

class NodePreviewParameterProvider : PreviewParameterProvider<Node> {
    val mickeyMouse = Node(
        num = 1955,
        user = user {
            id = "mickeyMouseId"
            longName = "Mickey Mouse"
            shortName = "MM"
            hwModel = MeshProtos.HardwareModel.TBEAM
        },
        position = position {
            latitudeI = 338125110
            longitudeI = -1179189760
            altitude = 138
            satsInView = 4
        },
        lastHeard = currentTime(),
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceMetrics = deviceMetrics {
            channelUtilization = 2.4F
            airUtilTx = 3.5F
            batteryLevel = 85
            voltage = 3.7F
            uptimeSeconds = 3600
        },
        hopsAway = 0
    )

    private val minnieMouse = mickeyMouse.copy(
        num = Random.nextInt(),
        user = user {
            longName = "Minnie Mouse"
            shortName = "MiMo"
            id = "minnieMouseId"
            hwModel = MeshProtos.HardwareModel.HELTEC_V3
        },
        snr = 12.5F,
        rssi = -42,
        position = position {},
        hopsAway = 1
    )

    private val donaldDuck = Node(
        num = Random.nextInt(),
        position = position {
            latitudeI = 338052347
            longitudeI = -1179208460
            altitude = 121
            satsInView = 66
        },
        lastHeard = currentTime() - 300,
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceMetrics = deviceMetrics {
            channelUtilization = 2.4F
            airUtilTx = 3.5F
            batteryLevel = 85
            voltage = 3.7F
            uptimeSeconds = 3600
        },
        user = user {
            id = "donaldDuckId"
            longName = "Donald Duck, the Grand Duck of the Ducks"
            shortName = "DoDu"
            hwModel = MeshProtos.HardwareModel.HELTEC_V3
            publicKey = ByteString.copyFrom(ByteArray(32) { 1 })
        },
        environmentMetrics = environmentMetrics {
            temperature = 28.0F
            relativeHumidity = 50.0F
            barometricPressure = 1013.25F
            gasResistance = 0.0F
            voltage = 3.7F
            current = 0.0F
            iaq = 100
        },
        paxcounter = paxcount {
            wifi = 30
            ble = 39
            uptime = 420
        },
        hopsAway = 2
    )

    private val unknown = donaldDuck.copy(
        user = user {
            id = "myId"
            longName = "Meshtastic myId"
            shortName = "myId"
            hwModel = MeshProtos.HardwareModel.UNSET
        },
        environmentMetrics = environmentMetrics {},
        paxcounter = paxcount {},
    )

    private val almostNothing = Node(
        num = Random.nextInt(),
    )

    override val values: Sequence<Node>
        get() = sequenceOf(
            mickeyMouse, // "this" node
            unknown,
            almostNothing,
            minnieMouse,
            donaldDuck
        )
}
