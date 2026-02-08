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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.send
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun WarningDialog(
    icon: ImageVector? = Icons.Rounded.Warning,
    title: String,
    text: @Composable () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MeshtasticDialog(
        onDismiss = onDismiss,
        icon = icon,
        title = title,
        text = text,
        confirmText = stringResource(Res.string.send),
        onConfirm = {
            onDismiss()
            onConfirm()
        },
        dismissText = stringResource(Res.string.cancel),
    )
}

@Preview
@Composable
private fun WarningDialogPreview() {
    AppTheme { WarningDialog(title = "Factory Reset?", onDismiss = {}, onConfirm = {}) }
}
