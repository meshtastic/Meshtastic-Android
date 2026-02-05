/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions")

package com.meshtastic.android.meshserviceexample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BatteryUnknown
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.meshtastic.core.model.NodeInfo

@Composable
fun ListItem(
    text: String,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(text) },
        supportingContent = supportingText?.let { { Text(it) } },
        leadingContent = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = trailingIcon?.let { { Icon(it, contentDescription = null) } },
    )
}

@Composable
fun TitledCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    expanded: Boolean,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onExpandClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MeshServiceViewModel) {
    val isConnected by viewModel.serviceConnectionStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { TopBarTitle(isConnected, connectionState) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.requestNodes()
                            scope.launch { snackbarHostState.showSnackbar("Refreshing nodes...") }
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Nodes")
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        MainContent(viewModel, innerPadding, snackbarHostState)
    }
}

@Composable
private fun TopBarTitle(isConnected: Boolean, connectionState: String) {
    Column {
        Text(
            text = "Mesh Service Example",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor =
                if (isConnected) {
                    Color.Green
                } else {
                    MaterialTheme.colorScheme.error
                }
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "Connected ($connectionState)" else "Disconnected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MainContent(
    viewModel: MeshServiceViewModel,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    val myNodeInfo by viewModel.myNodeInfo.collectAsState()
    val myId by viewModel.myId.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val lastMessage by viewModel.message.collectAsState()
    val packetLog by viewModel.packetLog.collectAsState()

    var nodesExpanded by remember { mutableStateOf(false) }
    var logExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MyInfoSection(myId, myNodeInfo) }
        item { TitledCard(title = "Messaging") { MessagingSection(viewModel, lastMessage) } }
        
        item {
            SectionHeader(
                title = "Mesh Nodes (${nodes.size})",
                expanded = nodesExpanded,
                onExpandClick = { nodesExpanded = !nodesExpanded }
            )
        }
        
        if (nodesExpanded) {
            if (nodes.isEmpty()) {
                item { EmptyNodeState() }
            } else {
                items(nodes) { node ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        val nodeLabel = node.user?.longName ?: node.user?.id ?: "Unknown Node"
                        NodeItem(node) { action ->
                            scope.launch {
                                when (action) {
                                    "traceroute" -> {
                                        viewModel.requestTraceroute(node.num)
                                        snackbarHostState.showSnackbar("Traceroute requested for $nodeLabel")
                                    }
                                    "telemetry" -> {
                                        viewModel.requestTelemetry(node.num)
                                        snackbarHostState.showSnackbar("Telemetry requested for $nodeLabel")
                                    }
                                    "neighbors" -> {
                                        viewModel.requestNeighborInfo(node.num)
                                        snackbarHostState.showSnackbar("Neighbor info requested for $nodeLabel")
                                    }
                                    "position" -> {
                                        viewModel.requestPosition(node.num)
                                        snackbarHostState.showSnackbar("Position requested for $nodeLabel")
                                    }
                                    "userinfo" -> {
                                        viewModel.requestUserInfo(node.num)
                                        snackbarHostState.showSnackbar("User info requested for $nodeLabel")
                                    }
                                    "connstatus" -> {
                                        viewModel.requestDeviceConnectionStatus(node.num)
                                        snackbarHostState.showSnackbar("Connection status requested for $nodeLabel")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Packet Log",
                expanded = logExpanded,
                onExpandClick = { logExpanded = !logExpanded }
            )
        }
        
        if (logExpanded) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        PacketLogContent(packetLog)
                    }
                }
            }
        }

        item { ActionButtons(viewModel, snackbarHostState) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PacketLogContent(log: List<String>) {
    Column(
        modifier =
        Modifier.fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (log.isEmpty()) {
            Text(
                text = "No packets yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            log.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun MyInfoSection(myId: String?, myNodeInfo: org.meshtastic.core.model.MyNodeInfo?) {
    TitledCard(title = "My Node Information") {
        ListItem(
            text = "Long ID",
            supportingText = myId ?: "N/A",
            leadingIcon = Icons.Rounded.AccountCircle,
            trailingIcon = null,
        )
        ListItem(
            text = "Firmware",
            supportingText = myNodeInfo?.firmwareString ?: "N/A",
            leadingIcon = Icons.Rounded.Info,
            trailingIcon = null,
        )
    }
}

@Composable
private fun EmptyNodeState() {
    Text(
        text = "No mesh nodes discovered yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun MessagingSection(viewModel: MeshServiceViewModel, lastMessage: String) {
    var textToSend by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        if (lastMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            ) {
                ListItem(
                    text = "Last Received",
                    supportingText = lastMessage,
                    leadingIcon = Icons.AutoMirrored.Rounded.Message,
                    trailingIcon = null,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textToSend,
                onValueChange = { textToSend = it },
                modifier = Modifier.weight(1f),
                label = { Text("Send broadcast message") },
                shape = MaterialTheme.shapes.large,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (textToSend.isNotBlank()) {
                        viewModel.sendMessage(textToSend)
                        textToSend = ""
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun NodeItem(node: NodeInfo, onAction: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        NodeItemHeader(node)
        Spacer(modifier = Modifier.height(8.dp))
        NodeItemActions(node.isOnline, onAction)
    }
}

@Composable
private fun NodeItemHeader(node: NodeInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            if (node.isOnline) {
                Box(
                    modifier =
                    Modifier.size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color.Green),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.user?.longName ?: "Unknown Node",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ID: ${node.user?.id ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NodeItemActions(isOnline: Boolean, onAction: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction("traceroute") }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Route, "Traceroute", Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onAction("telemetry") }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.BatteryUnknown,
                "Telemetry",
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.secondary,
            )
        }
        IconButton(onClick = { onAction("position") }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MyLocation, "Position", Modifier.size(20.dp), MaterialTheme.colorScheme.tertiary)
        }
        IconButton(onClick = { onAction("neighbors") }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Hub, "Neighbors", Modifier.size(20.dp), MaterialTheme.colorScheme.tertiary)
        }
        IconButton(onClick = { onAction("userinfo") }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.PersonSearch, "User Info", Modifier.size(20.dp), MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = { onAction("connstatus") }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.SignalCellularAlt,
                "Conn Status",
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.outline,
            )
        }
        if (isOnline) {
            Icon(
                imageVector = Icons.Rounded.Router,
                contentDescription = "Online",
                tint = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun ActionButtons(viewModel: MeshServiceViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    TitledCard(title = "Device Controls") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GpsButtons(viewModel, snackbarHostState)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.rebootLocalDevice()
                    scope.launch { snackbarHostState.showSnackbar("Reboot Requested") }
                },
                shape = MaterialTheme.shapes.medium,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reboot Radio")
            }
        }
    }
}

@Composable
private fun GpsButtons(viewModel: MeshServiceViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val colors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                viewModel.startProvideLocation()
                scope.launch { snackbarHostState.showSnackbar("GPS Sharing Started") }
            },
            shape = MaterialTheme.shapes.medium,
            colors = colors,
        ) {
            Icon(imageVector = Icons.Rounded.GpsFixed, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start GPS", style = MaterialTheme.typography.labelLarge)
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                viewModel.stopProvideLocation()
                scope.launch { snackbarHostState.showSnackbar("GPS Sharing Stopped") }
            },
            shape = MaterialTheme.shapes.medium,
            colors = colors,
        ) {
            Icon(imageVector = Icons.Rounded.GpsOff, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop GPS", style = MaterialTheme.typography.labelLarge)
        }
    }
}
