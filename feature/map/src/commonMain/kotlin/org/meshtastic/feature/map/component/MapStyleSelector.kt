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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_style_selection
import org.meshtastic.core.resources.selected_map_type
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.model.MapStyle

/**
 * Map style selector button + dropdown menu. Shows predefined [MapStyle] entries with a checkmark next to the currently
 * selected style.
 */
@Composable
internal fun MapStyleSelector(selectedStyle: MapStyle, onSelectStyle: (MapStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapButton(
            icon = MeshtasticIcons.Layers,
            contentDescription = stringResource(Res.string.map_style_selection),
            onClick = { expanded = true },
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(style.label)) },
                    onClick = {
                        onSelectStyle(style)
                        expanded = false
                    },
                    trailingIcon =
                    if (selectedStyle == style) {
                        {
                            Icon(
                                MeshtasticIcons.Check,
                                contentDescription = stringResource(Res.string.selected_map_type),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
