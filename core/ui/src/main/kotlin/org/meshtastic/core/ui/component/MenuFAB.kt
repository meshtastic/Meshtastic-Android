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
package org.meshtastic.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OfflineShare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenuFAB(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<MenuFABItem>,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                content = {
                    val imageVector = if (expanded) Icons.Filled.Close else Icons.AutoMirrored.Rounded.OfflineShare
                    Icon(imageVector = imageVector, contentDescription = null)
                },
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(),
            )
        },
        horizontalAlignment = Alignment.End,
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    item.onClick()
                    onExpandedChange(false)
                },
                icon = { Icon(item.icon, contentDescription = null) },
                text = { Text(item.label) },
            )
        }
    }
}

data class MenuFABItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)
