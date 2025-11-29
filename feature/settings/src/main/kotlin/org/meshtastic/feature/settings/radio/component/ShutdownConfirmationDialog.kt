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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.meshtastic.core.strings.shutdown_critical_node
import org.meshtastic.core.strings.shutdown_node_name
import org.meshtastic.core.strings.shutdown_type_name
import org.meshtastic.core.strings.shutdown_warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos

@Composable
fun ShutdownConfirmationDialog(
    title: String,
    node: Node?,
    requiresTypedConfirmation: Boolean,
    onDismiss: () -> Unit,
    isShutdown: Boolean = true,
    icon: ImageVector? = Icons.Rounded.Warning,
    onConfirm: () -> Unit,
) {
    var typedName by remember { mutableStateOf("") }
    val nodeLongName = node?.user?.longName ?: "Unknown Node"
    val confirmEnabled = !requiresTypedConfirmation || typedName.trim().equals(nodeLongName, ignoreCase = true)

    AlertDialog(
        onDismissRequest = {},
        icon = { icon?.let { Icon(imageVector = it, contentDescription = null) } },
        title = { Text(text = title) },
        text = {
            ShutdownDialogContent(
                nodeLongName = nodeLongName,
                isShutdown = isShutdown,
                requiresTypedConfirmation = requiresTypedConfirmation,
                typedName = typedName,
                onTypedNameChange = { typedName = it },
            )
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(Res.string.cancel)) } },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                enabled = confirmEnabled,
            ) {
                Text(stringResource(Res.string.send))
            }
        },
    )
}

@Composable
private fun ShutdownDialogContent(
    nodeLongName: String,
    isShutdown: Boolean,
    requiresTypedConfirmation: Boolean,
    typedName: String,
    onTypedNameChange: (String) -> Unit,
) {
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

        if (requiresTypedConfirmation) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.shutdown_critical_node),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = typedName,
                onValueChange = onTypedNameChange,
                label = { Text(stringResource(Res.string.shutdown_type_name, nodeLongName)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

/**
 * Determines if a node requires typed confirmation before shutdown.
 *
 * For now we treat infrastructure and base-station style roles as requiring extra protection:
 * - ROUTER (infrastructure)
 * - ROUTER_LATE (infrastructure)
 * - REPEATER (infrastructure)
 * - CLIENT_BASE (likely fixed/base station, e.g., on a roof)
 *
 * This matches the set of roles that are treated as infrastructure elsewhere in the app.
 */
fun Node.requiresTypedShutdownConfirmation(deviceConfig: ConfigProtos.Config.DeviceConfig?): Boolean {
    val role = deviceConfig?.role ?: return false
    return role in
        listOf(
            ConfigProtos.Config.DeviceConfig.Role.ROUTER,
            ConfigProtos.Config.DeviceConfig.Role.ROUTER_LATE,
            ConfigProtos.Config.DeviceConfig.Role.REPEATER,
            ConfigProtos.Config.DeviceConfig.Role.CLIENT_BASE,
        )
}

@Preview
@Composable
private fun ShutdownConfirmationDialogPreview() {
    val mockNode =
        Node(
            num = 123,
            user = MeshProtos.User.newBuilder().setLongName("Rooftop Router Node").setShortName("ROOF").build(),
        )

    AppTheme {
        ShutdownConfirmationDialog(
            title = "Shutdown?",
            node = mockNode,
            requiresTypedConfirmation = true,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
