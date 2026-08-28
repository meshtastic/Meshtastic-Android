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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A compact icon button used in map control overlays. Uses [FilledIconButton] for a consistent, compact appearance
 * across both Google and F-Droid flavors.
 *
 * @param compact draws the button at Material 3's small icon-button metrics instead of the default. For the maps that
 *   are a thumbnail rather than a screen — the node-detail mini-map is 200dp tall — where the default pair of controls
 *   would occupy half the map. This is the framework's own size token rather than a hand-picked `size` modifier, so the
 *   container shrinks without shrinking the touch target with it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    compact: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = if (compact) modifier.size(IconButtonDefaults.smallContainerSize()) else modifier,
        shape = if (compact) IconButtonDefaults.smallRoundShape else IconButtonDefaults.filledShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint ?: IconButtonDefaults.filledIconButtonColors().contentColor,
            modifier = if (compact) Modifier.size(IconButtonDefaults.smallIconSize) else Modifier,
        )
    }
}
