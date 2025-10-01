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

package com.geeksville.mesh.ui.node.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.icon.Elevation
import org.meshtastic.core.ui.icon.MeshtasticIcons

private const val SIZE_ICON = 20

@Composable
fun IconInfo(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    text: String? = null,
    content: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            modifier = Modifier.size(SIZE_ICON.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        text?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        content()
    }
}

@Composable
@Preview
fun IconInfoPreview() {
    MaterialTheme {
        IconInfo(icon = MeshtasticIcons.Elevation, contentDescription = "Elevation", content = { Text(text = "100") })
    }
}
