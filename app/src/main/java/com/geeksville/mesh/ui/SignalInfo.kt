package com.geeksville.mesh.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.ui.preview.NodeInfoPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun SignalInfo(
    nodeInfo: NodeInfo,
    isThisNode: Boolean
) {
    val text = if (isThisNode) {
        "ChUtil %.1f%% AirUtilTX %.1f%%".format(
            nodeInfo.deviceMetrics?.channelUtilization,
            nodeInfo.deviceMetrics?.airUtilTx
        )
    } else {
        buildString {
            if (nodeInfo.channel > 0) append("ch:${nodeInfo.channel}")
            if (nodeInfo.snr < 100F && nodeInfo.rssi < 0) {
                if (isNotEmpty()) append(" ")
                append("RSSI: %d SNR: %.1f".format(nodeInfo.rssi, nodeInfo.snr))
            }
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

@Composable
@Preview(showBackground = true)
fun SignalInfoSimplePreview() {
    AppTheme {
        SignalInfo(NodeInfo(
            num = 1,
            position = null,
            lastHeard = 0,
            channel = 0,
            snr = 12.5F,
            rssi = -42,
            deviceMetrics = null,
            user = null
        ), false)
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
fun SignalInfoPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    nodeInfo: NodeInfo
) {
    AppTheme {
        SignalInfo(nodeInfo, false)
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
fun SignalInfoSelfPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    nodeInfo: NodeInfo
) {
    AppTheme {
        SignalInfo(nodeInfo, true)
    }
}