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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.model.Node
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.User

@Composable
fun NodeChip(modifier: Modifier = Modifier, node: Node, onClick: ((Node) -> Unit)? = null) {
    val (textColor, nodeColor) = node.colors
    val colors = CardDefaults.cardColors(containerColor = Color(nodeColor), contentColor = Color(textColor))

    val content: @Composable () -> Unit = {
        Box(
            modifier =
            Modifier.width(IntrinsicSize.Min)
                .defaultMinSize(minWidth = 72.dp, minHeight = 32.dp)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = node.user.short_name.ifEmpty { "Node" } },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = node.user.short_name.ifEmpty { "???" },
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                textDecoration = TextDecoration.LineThrough.takeIf { node.isIgnored },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }

    if (onClick == null) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.small, colors = colors) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.small, colors = colors, onClick = { onClick(node) }) {
            content()
        }
    }
}

@Suppress("MagicNumber")
@Preview
@Composable
private fun NodeChipPreview() {
    val user = User(short_name = "\uD83E\uDEE0", long_name = "John Doe")
    val node =
        Node(
            num = 13444,
            user = user,
            isIgnored = false,
            paxcounter = Paxcount(ble = 10, wifi = 5),
            environmentMetrics = EnvironmentMetrics(temperature = 25f, relative_humidity = 60f),
        )
    NodeChip(node = node)
}
