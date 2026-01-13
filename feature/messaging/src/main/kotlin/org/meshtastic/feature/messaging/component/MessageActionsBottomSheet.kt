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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsBottomSheet(onDismiss: () -> Unit, onReply: () -> Unit, onReact: () -> Unit, onCopy: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            ListItem(
                headlineContent = { Text("Reply") },
                leadingContent = { Icon(Icons.Default.Reply, contentDescription = "Reply") },
                modifier = Modifier.clickable(onClick = onReply),
            )
            ListItem(
                headlineContent = { Text("React") },
                leadingContent = { Icon(Icons.Default.ThumbUp, contentDescription = "React") },
                modifier = Modifier.clickable(onClick = onReact),
            )
            ListItem(
                headlineContent = { Text("Copy") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") },
                modifier = Modifier.clickable(onClick = onCopy),
            )
        }
    }
}
