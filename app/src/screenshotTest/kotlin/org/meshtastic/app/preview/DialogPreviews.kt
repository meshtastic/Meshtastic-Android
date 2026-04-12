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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun SimpleDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                MeshtasticDialog(
                    title = "Firmware Update",
                    message = "A new firmware version is available. Would you like to update now?",
                    confirmText = "Update",
                    dismissText = "Later",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun IconDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                MeshtasticDialog(
                    title = "Warning",
                    message = "This action will reset all device settings to factory defaults. This cannot be undone.",
                    icon = MeshtasticIcons.Warning,
                    confirmText = "Reset",
                    dismissText = "Cancel",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun MultipleChoiceDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                MeshtasticDialog(
                    title = "Select Channel",
                    message = "Choose a channel to join:",
                    choices = mapOf("Public" to {}, "Private" to {}, "Emergency" to {}),
                    onDismiss = {},
                )
            }
        }
    }
}
