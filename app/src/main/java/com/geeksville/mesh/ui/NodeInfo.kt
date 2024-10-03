package com.geeksville.mesh.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.compose.ElevationInfo
import com.geeksville.mesh.ui.compose.SatelliteCountInfo
import com.geeksville.mesh.ui.preview.NodeInfoPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.metersIn

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NodeInfo(
    thisNodeInfo: NodeInfo?,
    thatNodeInfo: NodeInfo,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    isIgnored: Boolean = false,
    onClicked: () -> Unit = {},
    blinking: Boolean = false,
) {

    val BLINK_DURATION = 250

    val unknownShortName = stringResource(id = R.string.unknown_node_short_name)
    val unknownLongName = stringResource(id = R.string.unknown_username)

    val nodeName = thatNodeInfo.user?.longName ?: unknownLongName
    val isThisNode = thisNodeInfo?.num == thatNodeInfo.num
    val distance = thisNodeInfo?.distanceStr(thatNodeInfo, distanceUnits)
    val (textColor, nodeColor) = thatNodeInfo.colors

    val highlight = Color(0x33FFFFFF)
    val bgColor by animateColorAsState(
        targetValue = if (blinking) highlight else Color.Transparent,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = BLINK_DURATION,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 80.dp)
    ) {
        Surface {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(8.dp)
            ) {
                val (chip, dist, name, pos, alt, sats, batt, heard, sig, env) = createRefs()
                val barrierBattHeard = createStartBarrier(batt, heard)
                val sigBarrier = createBottomBarrier(pos, heard)

                Box(
                    // removes the extra spacing above the chip
                    modifier = Modifier
                        .height(32.dp)
                        .constrainAs(chip) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                        }
                ) {
                    Chip(
                        modifier = Modifier.width(72.dp),
                        onClick = onClicked,
                        colors = ChipDefaults.chipColors(
                            backgroundColor = Color(nodeColor),
                            contentColor = Color(textColor)
                        ),
                        content = {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = (thatNodeInfo.user?.shortName ?: unknownShortName).strikeIf(isIgnored),
                                fontWeight = FontWeight.Normal,
                                fontSize = MaterialTheme.typography.button.fontSize,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }

                if (distance != null) {
                    Text(
                        modifier = Modifier.constrainAs(dist) {
                            top.linkTo(chip.bottom, 8.dp)
                            start.linkTo(chip.start)
                            end.linkTo(chip.end)
                        },
                        text = distance,
                        fontSize = MaterialTheme.typography.button.fontSize,
                    )
                }

                val style = if (thatNodeInfo.user?.hwModel == MeshProtos.HardwareModel.UNSET) {
                    LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)
                } else {
                    LocalTextStyle.current
                }
                Text(
                    modifier = Modifier.constrainAs(name) {
                        top.linkTo(parent.top)
                        linkTo(
                            start = chip.end,
                            end = barrierBattHeard,
                            bias = 0F,
                            startMargin = 8.dp,
                            endMargin = 8.dp,

                        )
                        width = Dimension.preferredWrapContent
                    },
                    text = nodeName.strikeIf(isIgnored),
                    style = style
                )

                val position = thatNodeInfo.position
                LinkedCoordinates(
                    modifier = Modifier.constrainAs(pos) {
                        linkTo(
                            top = name.bottom,
                            bottom = sig.top,
                            bias = 0F,
                            topMargin = 4.dp,
                            bottomMargin = 4.dp
                        )
                        linkTo(
                            start = name.start,
                            end = barrierBattHeard,
                            bias = 0F,
                            endMargin = 8.dp
                        )
                        width = Dimension.preferredWrapContent
                    },
                    position = position,
                    format = gpsFormat,
                    nodeName = nodeName
                )

                val signalShown = signalInfo(
                    modifier = Modifier.constrainAs(sig) {
                        top.linkTo(sigBarrier, 4.dp)
                        bottom.linkTo(env.top, 4.dp)
                        end.linkTo(parent.end)
                    },
                    nodeInfo = thatNodeInfo,
                    isThisNode = isThisNode
                )

                if (position?.isValid() == true) {
                    val system = ConfigProtos.Config.DisplayConfig.DisplayUnits.forNumber(distanceUnits)
                    val altitude = position.altitude.metersIn(system)
                    val elevationSuffix = stringResource(id = R.string.elevation_suffix)

                    ElevationInfo(
                        modifier = Modifier.constrainAs(alt) {
                            top.linkTo(pos.bottom, 4.dp)
                            if (signalShown) {
                                baseline.linkTo(sig.baseline)
                            }
                            linkTo(
                                start = pos.start,
                                end = sig.start,
                                endMargin = 8.dp,
                                bias = 0F,
                            )
                            width = Dimension.preferredWrapContent
                        },
                        altitude = altitude,
                        system = system,
                        suffix = elevationSuffix
                    )

                    val satCount = position.satellitesInView
                    if (satCount > 0) {
                        SatelliteCountInfo(
                            modifier = Modifier.constrainAs(sats) {
                                top.linkTo(alt.bottom, 4.dp)
                                linkTo(
                                    start = pos.start,
                                    end = env.start,
                                    endMargin = 8.dp,
                                    bias = 0F,
                                )
                                width = Dimension.preferredWrapContent
                            },
                            satCount = satCount
                        )
                    }
                }

                BatteryInfo(
                    modifier = Modifier.constrainAs(batt) {
                        top.linkTo(parent.top)
                        end.linkTo(parent.end)
                    },
                    batteryLevel = thatNodeInfo.batteryLevel,
                    voltage = thatNodeInfo.voltage
                )

                LastHeardInfo(
                    modifier = Modifier.constrainAs(heard) {
                        top.linkTo(batt.bottom, 4.dp)
                        end.linkTo(parent.end)
                    },
                    lastHeard = thatNodeInfo.lastHeard
                )

                val envMetrics = thatNodeInfo.environmentMetrics
                    ?.getDisplayString(tempInFahrenheit) ?: ""
                if (envMetrics.isNotBlank()) {
                    Text(
                        modifier = Modifier.constrainAs(env) {
                            if (signalShown) {
                                top.linkTo(sig.bottom, 4.dp)
                            } else {
                                top.linkTo(pos.bottom, 4.dp)
                            }
                            end.linkTo(parent.end)
                        },
                        text = envMetrics,
                        color = MaterialTheme.colors.onSurface,
                        fontSize = MaterialTheme.typography.button.fontSize
                    )
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
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
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