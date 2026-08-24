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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.shutdown_node_name
import org.meshtastic.core.resources.shutdown_warning
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning

@Composable
fun ShutdownConfirmationDialog(
    title: String,
    node: Node?,
    onDismiss: () -> Unit,
    isShutdown: Boolean = true,
    icon: ImageVector? = null,
    onConfirm: () -> Unit,
) {
    val nodeLongName = node?.user?.long_name ?: "Unknown Node"
    val resolvedIcon = icon ?: MeshtasticIcons.Warning

    MeshtasticDialog(
        onDismiss = onDismiss,
        icon = resolvedIcon,
        title = title,
        text = { ShutdownDialogContent(nodeLongName = nodeLongName, isShutdown = isShutdown) },
        confirmText = stringResource(Res.string.send),
        onConfirm = {
            onDismiss()
            onConfirm()
        },
        dismissText = stringResource(Res.string.cancel),
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
