/*
 * Copyright (c) 2026 Meshtastic LLC
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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.edit
import org.meshtastic.core.resources.generate_qr_code
import org.meshtastic.core.resources.map
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.qr_code
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.share_qr_subtext
import org.meshtastic.core.resources.type_a_message
import org.meshtastic.core.ui.component.AnimatedConnectionsNavIcon
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.QrCode
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.rememberQrCodePainter
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MapZoomControls
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.node.detail.NodeDetailContent
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Config

/** Every store screenshot is the dark theme, matching the live listing; dynamic color would follow the host. */
@Composable
internal fun MarketingTheme(content: @Composable () -> Unit) {
    AppTheme(darkTheme = true, dynamicColor = false) { Surface(modifier = Modifier.fillMaxSize(), content = content) }
}

/**
 * The app's bottom navigation, drawn from the same destinations and icons as `MeshtasticNavigationSuite`, which itself
 * needs the live ViewModels this generator does not have.
 */
@Composable
private fun BottomNav(selected: TopLevelDestination) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = {},
                icon = {
                    if (destination == TopLevelDestination.Connect) {
                        AnimatedConnectionsNavIcon(
                            connectionState = ConnectionState.Connected,
                            deviceType = DeviceType.fromAddress("x00:11:22:33:44:55"),
                            meshActivityFlow = emptyFlow(),
                        )
                    } else {
                        Icon(
                            imageVector = vectorResource(destination.icon),
                            contentDescription = stringResource(destination.label),
                        )
                    }
                },
            )
        }
    }
}

/** The LongTurbo channel thread, built from the messaging feature's own bubbles, reactions and top bar. */
@Composable
internal fun MessagesScreen() {
    val channelName = Channel(SampleMesh.channelSet.settings.first(), SampleMesh.channelSet.lora_config!!).name
    val messages = SampleMesh.messages
    MarketingTheme {
        Scaffold(
            topBar = {
                MessageTopBar(
                    title = channelName,
                    channelIndex = 0,
                    mismatchKey = false,
                    onNavigateBack = {},
                    channels = SampleMesh.channelSet,
                    channelIndexParam = 0,
                    showQuickChat = false,
                    onToggleQuickChat = {},
                )
            },
            bottomBar = {
                Column {
                    Composer()
                    BottomNav(TopLevelDestination.Messages)
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.uuid }) { index, message ->
                    val prevSame = index > 0 && sameGroup(messages[index - 1], message)
                    val nextSame = index < messages.lastIndex && sameGroup(message, messages[index + 1])
                    MessageItem(
                        message = message,
                        node = message.node,
                        ourNode = SampleMesh.baseCamp,
                        selected = false,
                        showUserName = !prevSame,
                        hasSamePrev = prevSame,
                        hasSameNext = nextSame,
                        emojis = SampleMesh.reactions[message.uuid].orEmpty(),
                    )
                }
            }
        }
    }
}

/** The list's own grouping rule: one sender, one direction, within ten minutes. The feature's copy is internal. */
private fun sameGroup(older: Message, newer: Message): Boolean = older.fromLocal == newer.fromLocal &&
    older.node.num == newer.node.num &&
    newer.receivedTime - older.receivedTime < 10 * 60_000L

/**
 * The composer row. The feature's `MessageInput` is internal to `feature:messaging`, so this is the same shape drawn
 * from Material 3 primitives: an outlined field with the real placeholder string and the real send icon.
 */
@Composable
private fun Composer() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(Res.string.type_a_message)) },
            shape = MaterialTheme.shapes.extraLarge,
            singleLine = true,
        )
        IconButton(onClick = {}) {
            Icon(
                imageVector = MeshtasticIcons.Send,
                contentDescription = stringResource(Res.string.send),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The node list as the app shows it: our node's chip in the bar, one [NodeItem] card per node. */
@Composable
internal fun NodesScreen() {
    val ourNode = SampleMesh.baseCamp
    MarketingTheme {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = stringResource(Res.string.nodes),
                    ourNode = ourNode,
                    showNodeChip = true,
                    canNavigateUp = false,
                    onNavigateUp = {},
                    onClickChip = {},
                    actions = {},
                )
            },
            bottomBar = { BottomNav(TopLevelDestination.Nodes) },
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
                items(SampleMesh.nodes, key = { it.num }) { node ->
                    NodeItem(
                        thisNode = ourNode,
                        thatNode = node,
                        distanceUnits = MeasurementSystem.METRIC,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        isActive = node.num == ourNode.num,
                    )
                }
            }
        }
    }
}

/**
 * The map screen: the real MapLibre snapshot (basemap plus the app's own node chips, captured by [MapSnapshot]) under
 * the map screen's real app bar, toolbar and zoom controls. The library draws its attribution as a MapView overlay,
 * which the snapshotter has no equivalent of, so the same credit line is written here.
 */
@Composable
internal fun MapScreen(snapshot: ImageBitmap) {
    MarketingTheme {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = stringResource(Res.string.map),
                    ourNode = SampleMesh.baseCamp,
                    showNodeChip = true,
                    canNavigateUp = false,
                    onNavigateUp = {},
                    onClickChip = {},
                    actions = {},
                )
            },
            bottomBar = { BottomNav(TopLevelDestination.Map) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                MapControlsOverlay(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    onToggleFilterMenu = {},
                    onSitePlannerClick = {},
                    onToggleLocationTracking = {},
                )
                MapZoomControls(
                    onZoomIn = {},
                    onZoomOut = {},
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

/** Ridge Repeater's detail page from the node feature's own [NodeDetailContent], as a remote, unmanaged node. */
@Composable
internal fun NodeDetailScreen() {
    val node = SampleMesh.ridgeRepeater
    MarketingTheme {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = node.user.long_name,
                    ourNode = SampleMesh.baseCamp,
                    showNodeChip = false,
                    canNavigateUp = true,
                    onNavigateUp = {},
                    onClickChip = {},
                    actions = {},
                )
            },
            bottomBar = { BottomNav(TopLevelDestination.Nodes) },
        ) { padding ->
            NodeDetailContent(
                uiState =
                NodeDetailUiState(
                    node = node,
                    ourNode = SampleMesh.baseCamp,
                    metricsState = MetricsState(isLocal = false, isManaged = false),
                    availableLogs =
                    setOf(
                        LogsType.DEVICE,
                        LogsType.POSITIONS,
                        LogsType.ENVIRONMENT,
                        LogsType.SIGNAL,
                        LogsType.TRACEROUTE,
                    ),
                ),
                onAction = {},
                onFirmwareSelect = {},
                onSaveNotes = { _, _ -> },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * The channel list with the share affordance. The real screen shows the QR in a dialog window, which an offscreen scene
 * does not compose, so the same QR painter is laid out inline under the real [ChannelSelection] rows.
 */
@Composable
internal fun ChannelsScreen() {
    val channelSet = SampleMesh.channelSet
    val loraConfig = channelSet.lora_config ?: Config.LoRaConfig.Builder().build()
    val presetName = Channel(loraConfig = loraConfig).name
    val url = remember { channelSet.getChannelUrl().toString() }
    val qrSizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val qrPainter = rememberQrCodePainter(url, qrSizePx)
    MarketingTheme {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = stringResource(Res.string.channels),
                    ourNode = SampleMesh.baseCamp,
                    showNodeChip = false,
                    canNavigateUp = true,
                    onNavigateUp = {},
                    onClickChip = {},
                    actions = {},
                )
            },
            bottomBar = { BottomNav(TopLevelDestination.Settings) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                channelSet.settings.forEachIndexed { index, settings ->
                    ChannelSelection(
                        index = index,
                        title = settings.name.ifEmpty { presetName },
                        enabled = true,
                        isSelected = true,
                        onSelected = {},
                        channel = Channel(settings, loraConfig),
                    )
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text(text = stringResource(Res.string.edit))
                }
                Button(onClick = {}, modifier = Modifier.padding(16.dp)) {
                    Icon(imageVector = MeshtasticIcons.QrCode, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.generate_qr_code))
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = qrPainter,
                            contentDescription = stringResource(Res.string.qr_code),
                            modifier = Modifier.size(220.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = stringResource(Res.string.share_qr_subtext),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
