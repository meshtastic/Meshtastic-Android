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

package org.meshtastic.feature.node.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SatelliteAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.sats
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun SatelliteCountInfo(
    modifier: Modifier = Modifier,
    satCount: Int,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.TwoTone.SatelliteAlt,
        contentDescription = stringResource(Res.string.sats),
        label = stringResource(Res.string.sats),
        text = "$satCount",
        contentColor = contentColor,
    )
}

@PreviewLightDark
@Composable
private fun SatelliteCountInfoPreview() {
    AppTheme { SatelliteCountInfo(satCount = 5) }
}
