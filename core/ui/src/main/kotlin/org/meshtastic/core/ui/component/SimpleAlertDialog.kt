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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.message
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.sample_message
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun SimpleAlertDialog(
    title: StringResource,
    text: @Composable (() -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(text = dismissText ?: stringResource(Res.string.cancel))
        }
    },
    confirmButton = {
        onConfirm?.let {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.padding(horizontal = 16.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(text = confirmText ?: stringResource(Res.string.okay))
            }
        }
    },
    title = {
        Text(text = stringResource(title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    },
    text = text,
    shape = RoundedCornerShape(16.dp),
)

@Composable
fun SimpleAlertDialog(
    title: StringResource,
    text: StringResource,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) = SimpleAlertDialog(
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    title = title,
    text = { Text(text = stringResource(text), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
)

@Composable
fun SimpleAlertDialog(
    title: StringResource,
    text: String,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) = SimpleAlertDialog(
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    title = title,
    text = { Text(text = text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
)

@PreviewLightDark
@Composable
private fun SimpleAlertDialogPreview() {
    AppTheme { SimpleAlertDialog(title = Res.string.message, text = Res.string.sample_message) }
}
