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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.component.ActionModeTopBar
import org.meshtastic.feature.messaging.component.DeleteMessageDialog
import org.meshtastic.feature.messaging.component.MessageStatusIcon
import org.meshtastic.feature.messaging.component.UnreadMessagesDivider

@MultiPreview
@Composable
fun MessageStatusIconsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageStatus.entries.forEach { status ->
                    Text(status.name, style = MaterialTheme.typography.labelSmall)
                    MessageStatusIcon(status = status)
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun DeleteMessageDialogPreview() {
    AppTheme(isSystemInDarkTheme()) { Surface { DeleteMessageDialog(count = 3, onConfirm = {}, onDismiss = {}) } }
}

@MultiPreview
@Composable
fun UnreadMessagesDividerPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Messages above", style = MaterialTheme.typography.bodyMedium)
                UnreadMessagesDivider()
                Text("New messages below", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@MultiPreview
@Composable
fun ActionModeTopBarPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionModeTopBar(selectedCount = 1, onAction = {})
                ActionModeTopBar(selectedCount = 5, onAction = {})
            }
        }
    }
}
