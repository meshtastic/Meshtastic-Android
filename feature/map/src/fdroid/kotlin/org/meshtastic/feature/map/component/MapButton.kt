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

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.map_style_selection
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun MapButton(
    icon: ImageVector,
    contentDescription: StringResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    MapButton(
        icon = icon,
        contentDescription = stringResource(contentDescription),
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun MapButton(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

@PreviewLightDark
@Composable
private fun MapButtonPreview() {
    AppTheme { MapButton(icon = Icons.Outlined.Layers, contentDescription = Res.string.map_style_selection) }
}
