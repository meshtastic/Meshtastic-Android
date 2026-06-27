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
package org.meshtastic.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.security_icon_help_dismiss
import org.meshtastic.core.resources.security_signed_node
import org.meshtastic.core.resources.security_signed_node_help
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.ShieldCheck
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen

/**
 * The "signed node" shield (XEdDSA). Tappable like [NodeKeyStatusIcon] — tapping opens a plain-language explanation.
 * Render it next to the key status in [NodeSecurityIcons] (node rows + details) so the affordance is consistent.
 */
@Composable
fun NodeSignedStatusIcon(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(Res.string.security_signed_node)) },
            text = { Text(stringResource(Res.string.security_signed_node_help)) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.security_icon_help_dismiss))
                }
            },
        )
    }
    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            imageVector = MeshtasticIcons.ShieldCheck,
            contentDescription = stringResource(Res.string.security_signed_node),
            tint = MaterialTheme.colorScheme.StatusGreen,
        )
    }
}
