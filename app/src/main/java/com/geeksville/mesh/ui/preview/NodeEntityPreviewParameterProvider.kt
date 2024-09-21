package com.geeksville.mesh.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.DeviceMetrics.Companion.currentTime
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.deviceMetrics
import com.geeksville.mesh.environmentMetrics
import com.geeksville.mesh.paxcount
import com.geeksville.mesh.position
import com.geeksville.mesh.telemetry
import com.geeksville.mesh.user
import kotlin.random.Random

class NodeEntityPreviewParameterProvider : PreviewParameterProvider<NodeEntity> {

    val mickeyMouse = NodeEntity(
        num = 1955,
        user = user {
            id = "mickeyMouseId"
            longName = "Mickey Mouse"
            shortName = "MM"
            hwModel = MeshProtos.HardwareModel.TBEAM
        },
        longName = "Mickey Mouse",
        shortName = "MM",
        position = position {
            latitudeI = 338125110
            longitudeI = -1179189760
            altitude = 138
            satsInView = 4
        },
        latitude = 33.812511,
        longitude = -117.918976,
        lastHeard = currentTime(),
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceTelemetry = telemetry {
            deviceMetrics = deviceMetrics {
                channelUtilization = 2.4F
                airUtilTx = 3.5F
                batteryLevel = 85
                voltage = 3.7F
                uptimeSeconds = 3600
            }
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
        longName = "Minnie Mouse",
        shortName = "MiMo",
        snr = 12.5F,
        rssi = -42,
        position = position {},
        latitude = 0.0,
        longitude = 0.0,
        hopsAway = 1
    )

    private val donaldDuck = NodeEntity(
        num = Random.nextInt(),
        position = position {
            latitudeI = 338052347
            longitudeI = -1179208460
            altitude = 121
            satsInView = 66
        },
        latitude = 33.8052347,
        longitude = -117.9208460,
        lastHeard = currentTime() - 300,
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceTelemetry = telemetry {
            deviceMetrics = deviceMetrics {
                channelUtilization = 2.4F
                airUtilTx = 3.5F
                batteryLevel = 85
                voltage = 3.7F
                uptimeSeconds = 3600
            }
        },
        user = user {
            id = "donaldDuckId"
            longName = "Donald Duck, the Grand Duck of the Ducks"
            shortName = "DoDu"
            hwModel = MeshProtos.HardwareModel.HELTEC_V3
        },
        longName = "Donald Duck, the Grand Duck of the Ducks",
        shortName = "DoDu",
        environmentTelemetry = telemetry {
            environmentMetrics = environmentMetrics {
                temperature = 28.0F
                relativeHumidity = 50.0F
                barometricPressure = 1013.25F
                gasResistance = 0.0F
                voltage = 3.7F
                current = 0.0F
                iaq = 100
            }
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
        longName = "Meshtastic myId",
        shortName = null,
        environmentTelemetry = telemetry {
            environmentMetrics = environmentMetrics {}
        },
        paxcounter = paxcount {},
    )

    private val almostNothing = NodeEntity(
        num = Random.nextInt(),
    )

    override val values: Sequence<NodeEntity>
        get() = sequenceOf(
            mickeyMouse, // "this" node
            unknown,
            almostNothing,
            minnieMouse,
            donaldDuck
        )
}
