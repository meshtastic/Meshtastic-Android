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
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.TelemetryProtos
import org.meshtastic.core.database.model.Node

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
                .semantics { contentDescription = node.user.shortName.ifEmpty { "Node" } },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = node.user.shortName.ifEmpty { "???" },
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
fun NodeChipPreview() {
    val user = MeshProtos.User.newBuilder().setShortName("\uD83E\uDEE0").setLongName("John Doe").build()
    val node =
        Node(
            num = 13444,
            user = user,
            isIgnored = false,
            paxcounter = PaxcountProtos.Paxcount.newBuilder().setBle(10).setWifi(5).build(),
            environmentMetrics =
            TelemetryProtos.EnvironmentMetrics.newBuilder().setTemperature(25f).setRelativeHumidity(60f).build(),
        )
    NodeChip(node = node)
}
