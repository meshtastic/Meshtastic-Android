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
package org.meshtastic.feature.messaging

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Composable
private fun QuickChatItemPreview() {
    AppTheme { QuickChatItem(action = QuickChatAction(name = "TST", message = "Test", position = 0)) }
}

@PreviewLightDark
@Composable
private fun EditQuickChatDialogPreview() {
    AppTheme {
        EditQuickChatDialog(
            action = QuickChatAction(name = "TST", message = "Test", position = 0),
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
