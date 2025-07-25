/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.node.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.model.Node

@Composable
fun NodeChip(
    modifier: Modifier = Modifier,
    node: Node,
    isThisNode: Boolean,
    isConnected: Boolean,
    onAction: (NodeMenuAction) -> Unit,
) {
    val isIgnored = node.isIgnored
    val (textColor, nodeColor) = node.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val inputChipInteractionSource = remember { MutableInteractionSource() }
    Box {
        ElevatedAssistChip(
            modifier = modifier
                .width(IntrinsicSize.Min)
                .defaultMinSize(minWidth = 72.dp),
            elevation = AssistChipDefaults.elevatedAssistChipElevation(),
            colors = AssistChipDefaults.elevatedAssistChipColors(
                containerColor = Color(nodeColor),
                labelColor = Color(textColor),
            ),
            label = {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = node.user.shortName.ifEmpty { "???" },
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            },
            onClick = {},
            interactionSource = inputChipInteractionSource,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = { onAction(NodeMenuAction.MoreDetails(node)) },
                    onLongClick = { menuExpanded = true },
                    interactionSource = inputChipInteractionSource,
                    indication = null,
                )
        )
    }
    NodeMenu(
        expanded = menuExpanded,
        node = node,
        showFullMenu = !isThisNode && isConnected,
        onDismissMenuRequest = { menuExpanded = false },
        onAction = {
            menuExpanded = false
            onAction(it)
        }
    )
}

@Suppress("MagicNumber")
@Preview
@Composable
fun NodeChipPreview() {
    val user = MeshProtos.User.newBuilder()
        .setShortName("\uD83E\uDEE0")
        .setLongName("John Doe")
        .build()
    val node = Node(
        num = 13444,
        user = user,
        isIgnored = false,
        paxcounter = PaxcountProtos.Paxcount.newBuilder().setBle(10).setWifi(5).build(),
        environmentMetrics = TelemetryProtos.EnvironmentMetrics.newBuilder().setTemperature(25f)
            .setRelativeHumidity(60f).build()
    )
    NodeChip(
        node = node,
        isThisNode = false,
        isConnected = true,
        onAction = {}
    )
}
