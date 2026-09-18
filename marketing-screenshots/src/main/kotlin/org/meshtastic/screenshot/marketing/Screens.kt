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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.conversations
import org.meshtastic.core.resources.direct_messages
import org.meshtastic.core.resources.edit
import org.meshtastic.core.resources.generate_qr_code
import org.meshtastic.core.resources.map
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.resources.qr_code
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.share_qr_subtext
import org.meshtastic.core.resources.type_a_message
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.QrCode
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.rememberQrCodePainter
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MapZoomControls
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.messaging.ui.contact.ContactItem
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
 * The messages tab: the LongTurbo thread, and beside it on an expanded window the conversation list the app shows in
 * the list pane. A compact window shows the thread alone, as the app does once a conversation is open.
 */
@Composable
internal fun MessagesScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Messages) {
            ListDetail(compactPane = Pane.Detail, list = { ConversationsPane(mesh) }, detail = { ThreadPane(mesh) })
        }
    }
}

/** The conversation list from the messaging feature's own [ContactItem] rows under its own app bar. */
@Composable
private fun ConversationsPane(mesh: SampleMesh) {
    val (channels, direct) = mesh.contacts.partition { it.contactKey.endsWith("^all") }
    val openThread = mesh.contacts.first().contactKey
    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.conversations),
                ourNode = mesh.baseCamp,
                showNodeChip = true,
                canNavigateUp = false,
                onNavigateUp = {},
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(Res.string.channels), channels.size) }
            items(channels, key = { it.contactKey }) { contact ->
                ContactItem(
                    contact = contact.copy(lastMessageText = previewLine(mesh, contact.contactKey)),
                    selected = false,
                    isActive = contact.contactKey == openThread,
                    channels = mesh.channelSet,
                )
            }
            item { SectionHeader(stringResource(Res.string.direct_messages), direct.size) }
            items(direct, key = { it.contactKey }) { contact ->
                ContactItem(
                    contact = contact.copy(lastMessageText = previewLine(mesh, contact.contactKey)),
                    selected = false,
                    channels = mesh.channelSet,
                )
            }
        }
    }
}

/** The app prefixes a received preview with the sender's short name and leaves our own bare. */
@Composable
private fun previewLine(mesh: SampleMesh, contactKey: String): String {
    val (sender, line) = mesh.contactPreview.getValue(contactKey)
    val text = stringResource(line)
    return if (sender == null) text else "${sender.user.short_name}: $text"
}

/** The LongTurbo channel thread, built from the messaging feature's own bubbles, reactions and top bar. */
@Composable
private fun ThreadPane(mesh: SampleMesh) {
    val channelName = Channel(mesh.channelSet.settings.first(), mesh.channelSet.lora_config!!).name
    val messages = mesh.messages.map { it.copy(text = stringResource(mesh.messageText.getValue(it.uuid))) }
    Scaffold(
        topBar = {
            MessageTopBar(
                title = channelName,
                channelIndex = 0,
                mismatchKey = false,
                onNavigateBack = {},
                channels = mesh.channelSet,
                channelIndexParam = 0,
                showQuickChat = false,
                onToggleQuickChat = {},
            )
        },
        bottomBar = { Composer() },
    ) { padding ->
        // The app's list is reversed and anchored at the newest message, so a short window shows the end of the
        // thread; the grouping rule still reads in thread order.
        val newestFirst = messages.asReversed()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = true,
        ) {
            itemsIndexed(newestFirst, key = { _, message -> message.uuid }) { reversedIndex, message ->
                val index = messages.lastIndex - reversedIndex
                val prevSame = index > 0 && sameGroup(messages[index - 1], message)
                val nextSame = index < messages.lastIndex && sameGroup(message, messages[index + 1])
                MessageItem(
                    message = message,
                    node = message.node,
                    ourNode = mesh.baseCamp,
                    selected = false,
                    showUserName = !prevSame,
                    hasSamePrev = prevSame,
                    hasSameNext = nextSame,
                    emojis = mesh.reactions[message.uuid].orEmpty(),
                )
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

/**
 * The nodes tab: the list, and beside it on an expanded window a node's detail page. Summit Solar sits in the detail
 * pane here so this shot and [NodeDetailScreen], which opens Ridge Top, are two different pages on a wide window.
 */
@Composable
internal fun NodesScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Nodes) {
            ListDetail(
                compactPane = Pane.List,
                list = { NodesPane(mesh) },
                detail = { NodeDetailPane(mesh, mesh.summitSolar) },
            )
        }
    }
}

/** Ridge Top's detail page, with the list beside it on an expanded window. */
@Composable
internal fun NodeDetailScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Nodes) {
            ListDetail(
                compactPane = Pane.Detail,
                list = { NodesPane(mesh) },
                detail = { NodeDetailPane(mesh, mesh.ridgeTop) },
            )
        }
    }
}

/** The node list as the app shows it: our node's chip in the bar, one [NodeItem] card per node. */
@Composable
private fun NodesPane(mesh: SampleMesh) {
    val ourNode = mesh.baseCamp
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
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
            items(mesh.nodes, key = { it.num }) { node ->
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

/** A remote, unmanaged node's page from the node feature's own [NodeDetailContent]. */
@Composable
private fun NodeDetailPane(mesh: SampleMesh, node: Node) {
    Scaffold(
        topBar = {
            MainAppBar(
                title = node.user.long_name,
                ourNode = mesh.baseCamp,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = {},
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        NodeDetailContent(
            uiState =
            NodeDetailUiState(
                node = node,
                ourNode = mesh.baseCamp,
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

/**
 * The map screen: the real MapLibre snapshot (basemap plus the app's own node chips, captured by [MapSnapshot]) under
 * the map screen's real app bar, toolbar and zoom controls. The library draws its attribution as a MapView overlay,
 * which the snapshotter has no equivalent of, so the same credit line is written here.
 *
 * Composed once per form factor with no [snapshot] to measure the map area - [onMapArea] reports it in pixels - and
 * again with the capture made at exactly that size, so the image is placed without rescaling.
 */
@Composable
internal fun MapScreen(mesh: SampleMesh, snapshot: ImageBitmap?, onMapArea: (IntSize) -> Unit = {}) {
    MarketingTheme {
        AppShell(TopLevelDestination.Map) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.map),
                        ourNode = mesh.baseCamp,
                        showNodeChip = true,
                        canNavigateUp = false,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding).onSizeChanged(onMapArea)) {
                    if (snapshot != null) {
                        Image(
                            bitmap = snapshot,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
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
}

/**
 * The channel list with the share affordance, in the same [AdaptiveTwoPane] the app's channel screen uses: the rows and
 * the edit button first, the QR second, stacked on a phone and side by side on an expanded window. The real screen
 * shows the QR in a dialog window, which an offscreen scene does not compose, so the same QR painter is laid out
 * inline.
 */
@Composable
internal fun ChannelsScreen(mesh: SampleMesh) {
    val channelSet = mesh.channelSet
    val loraConfig = channelSet.lora_config ?: Config.LoRaConfig.Builder().build()
    val presetName = Channel(loraConfig = loraConfig).name
    MarketingTheme {
        AppShell(TopLevelDestination.Settings) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.channels),
                        ourNode = mesh.baseCamp,
                        showNodeChip = false,
                        canNavigateUp = true,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                AdaptiveTwoPane(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
                    first = {
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
                            colors =
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Text(text = stringResource(Res.string.edit))
                        }
                    },
                    second = { ShareCard(channelSet.getChannelUrl().toString()) },
                )
            }
        }
    }
}

@Composable
private fun ShareCard(url: String) {
    val qrSizePx = with(LocalDensity.current) { 200.dp.roundToPx() }
    val qrPainter = rememberQrCodePainter(remember(url) { url }, qrSizePx)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    modifier = Modifier.size(200.dp),
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
