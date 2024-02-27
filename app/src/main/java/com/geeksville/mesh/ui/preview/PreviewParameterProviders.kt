package com.geeksville.mesh.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.DeviceMetrics.Companion.currentTime
import com.geeksville.mesh.EnvironmentMetrics
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position
import kotlin.random.Random


class NodeInfoPreviewParameterProvider: PreviewParameterProvider<NodeInfo> {

    val mickeyMouse = NodeInfo(
        num = 1955,
        position = Position(
            latitude = 33.812511,
            longitude = -117.918976,
            altitude = 138,
        ),
        lastHeard = currentTime(),
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceMetrics = DeviceMetrics(
            channelUtilization = 2.4F,
            airUtilTx = 3.5F,
            batteryLevel = 85,
            voltage = 3.7F
        ),
        user = MeshUser(
            longName = "Micky Mouse",
            shortName = "MM",
            id = "mickeyMouseId",
            hwModel = MeshProtos.HardwareModel.TBEAM
        )
    )

    private val donaldDuck = NodeInfo(
        num = Random.nextInt(),
        position = Position(
            latitude = 33.80523471893125,
            longitude = -117.92084605996297,
            altitude = 121,
        ),
        lastHeard = currentTime() - 300,
        channel = 0,
        snr = 12.5F,
        rssi = -42,
        deviceMetrics = DeviceMetrics(
            channelUtilization = 2.4F,
            airUtilTx = 3.5F,
            batteryLevel = 85,
            voltage = 3.7F
        ),
        user = MeshUser(
            longName = "Donald Duck",
            shortName = "DoDu",
            id = "donaldDuckId",
            hwModel = MeshProtos.HardwareModel.HELTEC_V3,
        ),
        environmentMetrics = EnvironmentMetrics(
            temperature = 28.0F,
            relativeHumidity = 50.0F,
            barometricPressure = 1013.25F,
            gasResistance = 0.0F,
            voltage = 3.7F,
            current = 0.0F
        )
    )

    private val unknown = donaldDuck.copy(
        user = null,
        environmentMetrics = null
    )

    private val almostNothing = NodeInfo(
        num = Random.nextInt(),
    )

    override val values: Sequence<NodeInfo>
        get() = sequenceOf(
            mickeyMouse, // "this" node
            unknown,
            almostNothing,
            donaldDuck
        )

}