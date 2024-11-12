package com.geeksville.mesh.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.DeviceConfig
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.ui.components.MenuItemAction
import com.geeksville.mesh.ui.components.NodeKeyStatusIcon
import com.geeksville.mesh.ui.components.NodeMenu
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import com.geeksville.mesh.ui.compose.ElevationInfo
import com.geeksville.mesh.ui.compose.SatelliteCountInfo
import com.geeksville.mesh.ui.preview.NodeEntityPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.metersIn
import com.geeksville.mesh.util.toDistanceString

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NodeItem(
    thisNode: NodeEntity?,
    thatNode: NodeEntity,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    ignoreIncomingList: List<Int> = emptyList(),
    menuItemActionClicked: (MenuItemAction) -> Unit = {},
    blinking: Boolean = false,
    expanded: Boolean = false,
    currentTimeMillis: Long,
    isConnected: Boolean = false,
) {
    val isIgnored = ignoreIncomingList.contains(thatNode.num)
    val longName = thatNode.user.longName.ifEmpty { stringResource(id = R.string.unknown_username) }

    val isThisNode = thisNode?.num == thatNode.num
    val distance = thisNode?.distance(thatNode)?.let {
        val system = DisplayConfig.DisplayUnits.forNumber(distanceUnits)
        if (it == 0) null else it.toDistanceString(system)
    }
    val (textColor, nodeColor) = thatNode.colors

    val hwInfoString = when (val hwModel = thatNode.user.hwModel) {
        MeshProtos.HardwareModel.UNSET -> MeshProtos.HardwareModel.UNSET.name
        else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
    }
    val roleName = if (thatNode.isUnknownUser) {
        DeviceConfig.Role.UNRECOGNIZED.name
    } else {
        thatNode.user.role.name
    }

    val bgColor by animateColorAsState(
        targetValue = if (blinking) Color(color = 0x33FFFFFF) else Color.Transparent,
        animationSpec = repeatable(
            iterations = 6,
            animation = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinking node"
    )

    val style = if (thatNode.isUnknownUser) {
        LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)
    } else {
        LocalTextStyle.current
    }

    val (detailsShown, showDetails) = remember { mutableStateOf(expanded) }

    var showEncryptionDialog by remember { mutableStateOf(false) }
    if (showEncryptionDialog) {
        val (title, text) = when {
            thatNode.mismatchKey -> R.string.encryption_error to R.string.encryption_error_text
            thatNode.hasPKC -> R.string.encryption_pkc to R.string.encryption_pkc_text
            else -> R.string.encryption_psk to R.string.encryption_psk_text
        }
        SimpleAlertDialog(title, text) { showEncryptionDialog = false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 80.dp),
        elevation = 4.dp,
        onClick = { showDetails(!detailsShown) },
    ) {
        Surface {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(bgColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier.wrapContentSize(Alignment.TopStart)
                        ) {
                            Chip(
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .defaultMinSize(minHeight = 32.dp, minWidth = 72.dp),
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = Color(nodeColor),
                                    contentColor = Color(textColor)
                                ),
                                onClick = {
                                    menuExpanded = !menuExpanded
                                },
                                content = {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = thatNode.user.shortName.ifEmpty { "???" },
                                        fontWeight = FontWeight.Normal,
                                        fontSize = MaterialTheme.typography.button.fontSize,
                                        textDecoration = TextDecoration.LineThrough.takeIf {
                                            ignoreIncomingList.contains(thatNode.num)
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                },
                            )
                            NodeMenu(
                                node = thatNode,
                                ignoreIncomingList = ignoreIncomingList,
                                isThisNode = isThisNode,
                                onMenuItemAction = menuItemActionClicked,
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                isConnected = isConnected,
                            )
                        }
                        NodeKeyStatusIcon(
                            hasPKC = thatNode.hasPKC,
                            mismatchKey = thatNode.mismatchKey,
                            modifier = Modifier.size(32.dp)
                        ) { showEncryptionDialog = true }
                        Text(
                            modifier = Modifier.weight(1f),
                            text = longName,
                            style = style,
                            textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                            softWrap = true,
                        )

                        LastHeardInfo(
                            lastHeard = thatNode.lastHeard,
                            currentTimeMillis = currentTimeMillis
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (distance != null) {
                            Text(
                                text = distance,
                                fontSize = MaterialTheme.typography.button.fontSize,
                            )
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        BatteryInfo(
                            batteryLevel = thatNode.batteryLevel,
                            voltage = thatNode.voltage
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        signalInfo(
                            node = thatNode,
                            isThisNode = isThisNode
                        )
                        thatNode.validPosition?.let { position ->
                            val satCount = position.satsInView
                            if (satCount > 0) {
                                SatelliteCountInfo(
                                    satCount = satCount
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val telemetryString = thatNode.getTelemetryString(tempInFahrenheit)
                        if (telemetryString.isNotEmpty()) {
                            Text(
                                text = telemetryString,
                                color = MaterialTheme.colors.onSurface,
                                fontSize = MaterialTheme.typography.button.fontSize
                            )
                        }
                    }

                    if (detailsShown || expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            thatNode.validPosition?.let {
                                DisableSelection {
                                    LinkedCoordinates(
                                        latitude = thatNode.latitude,
                                        longitude = thatNode.longitude,
                                        format = gpsFormat,
                                        nodeName = longName
                                    )
                                }
                            }
                            val system =
                                ConfigProtos.Config.DisplayConfig.DisplayUnits.forNumber(distanceUnits)
                            thatNode.validPosition?.let { position ->
                                val altitude = position.altitude.metersIn(system)
                                val elevationSuffix = stringResource(id = R.string.elevation_suffix)
                                ElevationInfo(
                                    altitude = altitude,
                                    system = system,
                                    suffix = elevationSuffix
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = hwInfoString,
                                fontSize = MaterialTheme.typography.button.fontSize,
                                style = style,
                            )
                            Text(
                                modifier = Modifier.weight(1f),
                                text = roleName,
                                textAlign = TextAlign.Center,
                                fontSize = MaterialTheme.typography.button.fontSize,
                                style = style,
                            )
                            Text(
                                modifier = Modifier.weight(1f),
                                text = thatNode.user.id.ifEmpty { "???" },
                                textAlign = TextAlign.End,
                                fontSize = MaterialTheme.typography.button.fontSize,
                                style = style,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = false)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNode = NodeEntityPreviewParameterProvider().values.first()
        val thatNode = NodeEntityPreviewParameterProvider().values.last()
        NodeItem(
            thisNode = thisNode,
            thatNode = thatNode,
            1,
            0,
            true,
            currentTimeMillis = System.currentTimeMillis()
        )
    }
}

@Composable
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
fun NodeInfoPreview(
    @PreviewParameter(NodeEntityPreviewParameterProvider::class)
    thatNode: NodeEntity
) {
    AppTheme {
        val thisNode = NodeEntityPreviewParameterProvider().values.first()
        Column {
            Text(
                text = "Details Collapsed",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = false,
                currentTimeMillis = System.currentTimeMillis()
            )
            Text(
                text = "Details Shown",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = true,
                currentTimeMillis = System.currentTimeMillis()
            )
        }
    }
}
