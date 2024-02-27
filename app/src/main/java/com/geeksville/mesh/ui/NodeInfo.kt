package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.preview.NodeInfoPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NodeInfo(
    thisNodeInfo: NodeInfo,
    thatNodeInfo: NodeInfo,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    isIgnored: Boolean = false,
    onClicked: () -> Unit = {}
) {
    val unknownShortName = stringResource(id = R.string.unknown_node_short_name)
    val unknownLongName = stringResource(id = R.string.unknown_node_long_name)

    val nodeName = thatNodeInfo.user?.longName ?: unknownLongName
    val isThisNode = thisNodeInfo.num == thatNodeInfo.num
    val distance = thisNodeInfo.distanceStr(thatNodeInfo, distanceUnits)
    val (textColor, nodeColor) = thatNodeInfo.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
    ) {
        Surface {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        // removes the extra spacing above the chip
                        modifier = Modifier.height(32.dp)
                    ) {
                        Chip(
                            onClick = onClicked,
                            colors = ChipDefaults.chipColors(
                                backgroundColor = Color(nodeColor),
                                contentColor = Color(textColor)
                            ),
                            content = {
                                Text(
                                    text = (thatNodeInfo.user?.shortName ?: unknownShortName).strikeIf(isIgnored),
                                    fontWeight = FontWeight.Normal
                                )
                            },
                        )
                    }

                    if (distance != null) {
                        Text(text = distance)
                    }
                }
                Column(
                    modifier = Modifier.weight(1F),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val style = if (nodeName == unknownLongName) {
                        LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)
                    } else {
                        LocalTextStyle.current
                    }

                    Text(
                        text = nodeName.strikeIf(isIgnored),
                        style = style
                    )

                    LinkedCoordinates(
                        position = thatNodeInfo.position,
                        format = gpsFormat,
                        nodeName = nodeName
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    BatteryInfo(
                        batteryLevel = thatNodeInfo.batteryLevel,
                        voltage = thatNodeInfo.voltage
                    )
                    LastHeardInfo(lastHeard = thatNodeInfo.lastHeard)
                    SignalInfo(
                        nodeInfo = thatNodeInfo,
                        isThisNode = isThisNode
                    )

                    val envMetrics = thatNodeInfo.envMetricStr(tempInFahrenheit)
                    if (envMetrics.isNotBlank()) {
                        Text(
                            text = envMetrics,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                    }
                }
            }
        }
    }
}

private fun String.strike() = AnnotatedString(
    this,
    spanStyles = listOf(
        AnnotatedString.Range(
            SpanStyle(textDecoration = TextDecoration.LineThrough),
            start = 0,
            end = this.length
        )
    )
)
private fun String.strikeIf(isIgnored: Boolean): AnnotatedString = if (isIgnored) strike() else AnnotatedString(this)

@Composable
@Preview(showBackground = false)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNodeInfo = NodeInfoPreviewParameterProvider().values.first()
        val thatNodeInfo = NodeInfoPreviewParameterProvider().values.last()
        NodeInfo(
            thisNodeInfo = thisNodeInfo,
            thatNodeInfo = thatNodeInfo,
            1,
            0,
            true
        )
    }
}

@Composable
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
fun NodeInfoPreview(
    @PreviewParameter(NodeInfoPreviewParameterProvider::class)
    thatNodeInfo: NodeInfo
) {
    AppTheme {
        val thisNodeInfo = NodeInfoPreviewParameterProvider().values.first()
        NodeInfo(
            thisNodeInfo,
            thatNodeInfo,
            0,
            1,
            true
        )
    }
}