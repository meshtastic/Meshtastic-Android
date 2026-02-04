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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.send
import org.meshtastic.core.strings.shutdown_node_name
import org.meshtastic.core.strings.shutdown_warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.User

@Composable
fun ShutdownConfirmationDialog(
    title: String,
    node: Node?,
    onDismiss: () -> Unit,
    isShutdown: Boolean = true,
    icon: ImageVector? = Icons.Rounded.Warning,
    onConfirm: () -> Unit,
) {
    val nodeLongName = node?.user?.long_name ?: "Unknown Node"

    AlertDialog(
        onDismissRequest = {},
        icon = { icon?.let { Icon(imageVector = it, contentDescription = null) } },
        title = { Text(text = title) },
        text = { ShutdownDialogContent(nodeLongName = nodeLongName, isShutdown = isShutdown) },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(Res.string.cancel)) } },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            ) {
                Text(stringResource(Res.string.send))
            }
        },
    )
}

@Composable
private fun ShutdownDialogContent(nodeLongName: String, isShutdown: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.shutdown_node_name, nodeLongName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        if (isShutdown) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.shutdown_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun ShutdownConfirmationDialogPreview() {
    val mockNode = Node(num = 123, user = User(long_name = "Rooftop Router Node", short_name = "ROOF"))

    AppTheme { ShutdownConfirmationDialog(title = "Shutdown?", node = mockNode, onDismiss = {}, onConfirm = {}) }
}
