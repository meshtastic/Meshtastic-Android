package com.geeksville.mesh.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.preview.NodeInfoPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun signalInfo(
    modifier: Modifier = Modifier,
    nodeInfo: NodeInfo,
    isThisNode: Boolean
): Boolean {
    val text = if (isThisNode) {
        stringResource(R.string.channel_air_util).format(
            nodeInfo.deviceMetrics?.channelUtilization,
            nodeInfo.deviceMetrics?.airUtilTx
        )
    } else {
        buildList {
            if (nodeInfo.channel > 0) add("ch:${nodeInfo.channel}")
            if (nodeInfo.hopsAway == 0) {
                if (nodeInfo.snr < 100F && nodeInfo.rssi < 0) {
                    add("RSSI: %d SNR: %.1f".format(nodeInfo.rssi, nodeInfo.snr))
                }
            } else {
                add("%s: %d".format(stringResource(R.string.hops_away), nodeInfo.hopsAway))
            }
        }.joinToString(" ")
    }
    return if (text.isNotEmpty()) {
        Text(
            modifier = modifier,
            text = text,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
        true
    } else {
        false
    }
}

@Composable
@Preview(showBackground = true)
fun SignalInfoSimplePreview() {
    AppTheme {
        signalInfo(
            nodeInfo = NodeInfo(
                num = 1,
                position = null,
                lastHeard = 0,
                channel = 0,
                snr = 12.5F,
                rssi = -42,
                deviceMetrics = null,
                user = null,
                hopsAway = 0
            ),
            isThisNode = false
        )
    }
}

@PreviewLightDark
@Composable
fun SignalInfoPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    nodeInfo: NodeInfo
) {
    AppTheme {
        signalInfo(
            nodeInfo = nodeInfo,
            isThisNode = false
        )
    }
}

@Composable
@PreviewLightDark
fun SignalInfoSelfPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    nodeInfo: NodeInfo
) {
    AppTheme {
        signalInfo(
            nodeInfo = nodeInfo,
            isThisNode = true
        )
    }
}
