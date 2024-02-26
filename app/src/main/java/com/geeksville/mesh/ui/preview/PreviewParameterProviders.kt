package com.geeksville.mesh.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.NodeInfo


class NodeInfoPreviewParameterProvider: PreviewParameterProvider<NodeInfo> {
    override val values: Sequence<NodeInfo>
        get() = sequenceOf(
            NodeInfo(
                num = 1,
                position = null,
                lastHeard = 0,
                channel = 0,
                snr = 12.5F,
                rssi = -42,
                deviceMetrics = DeviceMetrics(
                    channelUtilization = 2.4F,
                    airUtilTx = 3.5F,
                    batteryLevel = 85,
                    voltage = 3.7F
                ),
                user = null
            )
        )
}