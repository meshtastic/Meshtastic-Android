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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.map.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.selected_map_type
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.MeshtasticIcons

/**
 * The menu that picks what the map draws underneath the mesh.
 *
 * Both maps show the same shape — the renderer's own styles, then the shared raster catalogue, then the user's custom
 * sources, then whatever the host appends — and previously each flavour had its own copy of it. What differs is only
 * the contents of [groups], which the caller assembles: the Google map opens with Google's four map types where the
 * MapLibre map opens with its vector styles.
 *
 * Each list in [groups] renders as a visually separate block; empty ones are dropped so a user with no custom sources
 * sees no empty divider.
 */
@Composable
fun BasemapMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<BasemapChoice>>,
    selectedId: String?,
    onSelect: (BasemapChoice) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        groups
            .filter { it.isNotEmpty() }
            .forEach { group ->
                DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                    group.forEach { choice ->
                        BasemapMenuItem(choice = choice, selected = choice.id == selectedId) {
                            onSelect(choice)
                            onDismissRequest()
                        }
                    }
                }
            }
        trailingContent()
    }
}

@Composable
private fun BasemapMenuItem(choice: BasemapChoice, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = choice.label) },
        onClick = onClick,
        trailingIcon =
        if (selected) {
            { Icon(MeshtasticIcons.Check, contentDescription = stringResource(Res.string.selected_map_type)) }
        } else {
            null
        },
    )
}
