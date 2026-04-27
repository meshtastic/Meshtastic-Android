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
package org.meshtastic.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_power
import org.meshtastic.core.resources.ic_star
import org.meshtastic.wear.presentation.components.MiniPill
import org.meshtastic.wear.presentation.components.PulsingDot
import org.meshtastic.wear.presentation.components.TabChip

@Composable
fun NodesScreen(viewModel: NodesViewModel = koinViewModel()) {
    val allNodes by viewModel.nodes.collectAsStateWithLifecycle()
    val radioState by viewModel.connectionState.collectAsStateWithLifecycle()
    var showFavs by remember { mutableStateOf(false) }

    val displayed = if (showFavs) allNodes.filter { it.favorite } else allNodes
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { 
                NodesHeader(
                    onlineCount = allNodes.count { it.online }, 
                    totalCount = allNodes.size,
                    radioState = radioState
                ) 
            }

            item { NodesTabSelection(showFavs = showFavs, onTabSelect = { showFavs = it }) }

            if (displayed.isEmpty()) {
                item { EmptyNodes(showFavs = showFavs) }
            } else {
                items(displayed.sortedByDescending { it.online }) { node -> NodeCard(node) }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NodesHeader(onlineCount: Int, totalCount: Int, radioState: ConnectionState) {
    val statusColor = when (radioState) {
        ConnectionState.Connected -> COLOR_NEON_GREEN
        ConnectionState.Connecting -> COLOR_AMBER
        ConnectionState.Disconnected, ConnectionState.DeviceSleep -> COLOR_OFFLINE_GRAY
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(color = statusColor, sizeDp = 7)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "MESH NODES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = COLOR_TEAL,
        )
        Spacer(Modifier.weight(1f))
        Text(text = "$onlineCount/$totalCount", fontSize = 10.sp, color = COLOR_TEXT_SECONDARY)
    }
}

@Composable
private fun NodesTabSelection(showFavs: Boolean, onTabSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabChip(label = "ALL", selected = !showFavs, onClick = { onTabSelect(false) }, modifier = Modifier.weight(1f))
        TabChip(
            label = "FAV",
            selected = showFavs,
            onClick = { onTabSelect(true) },
            modifier = Modifier.weight(1f),
            icon = vectorResource(Res.drawable.ic_star)
        )
    }
}

@Composable
private fun EmptyNodes(showFavs: Boolean) {
    val message = if (showFavs) "No favourites yet" else "No nodes yet"
    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 12.sp, color = COLOR_TEXT_SECONDARY)
    }
}

@Composable
fun NodeCard(node: WearableNode) {
    val onlineColor = if (node.online) COLOR_NEON_GREEN else COLOR_OFFLINE_GRAY

    Box(
        modifier =
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(COLOR_SURFACE1)
            .drawBehind { drawRect(color = onlineColor, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodeAvatar(node = node)

            Spacer(Modifier.width(8.dp))

            NodeInfo(node = node, modifier = Modifier.weight(1f))

            Canvas(modifier = Modifier.size(7.dp)) { drawCircle(color = onlineColor) }
        }
    }
}

@Composable
private fun NodeAvatar(node: WearableNode) {
    Box(
        modifier =
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(
                if (node.online) {
                    Brush.radialGradient(listOf(COLOR_TEAL_DIM, Color(COLOR_NODES_BG)))
                } else {
                    Brush.radialGradient(listOf(COLOR_SURFACE2, COLOR_SURFACE1))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = node.shortName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (node.online) COLOR_TEAL else COLOR_TEXT_SECONDARY,
        )
    }
}

@Composable
private fun NodeInfo(node: WearableNode, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = node.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = COLOR_TEXT_PRIMARY,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (node.favorite) {
                Text(" *", fontSize = 11.sp, color = COLOR_AMBER)
            }
        }

        Spacer(Modifier.height(2.dp))

        NodeStatusPills(node = node)
    }
}

@Composable
private fun NodeStatusPills(node: WearableNode) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        node.battery?.let { batt ->
            val isPowered = batt >= 101
            val battColor =
                when {
                    isPowered -> COLOR_TEAL
                    batt > BATTERY_HIGH_THRESHOLD -> COLOR_NEON_GREEN
                    batt > BATTERY_LOW_THRESHOLD -> COLOR_AMBER
                    else -> COLOR_ERROR_RED
                }
            
            if (isPowered) {
                MiniPill(
                    text = "PWR",
                    tint = battColor,
                    icon = vectorResource(Res.drawable.ic_power)
                )
            } else {
                MiniPill(text = "$batt%", tint = battColor)
            }
        }
        node.snr?.let { snr -> MiniPill(text = "SNR ${snr.toInt()}", tint = COLOR_TEAL) }
    }
}
