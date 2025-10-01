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
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun DistanceInfo(distance: String, modifier: Modifier = Modifier) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.SocialDistance,
        contentDescription = stringResource(R.string.distance),
        text = distance,
    )
}

@PreviewLightDark
@Composable
private fun DistanceInfoPreview() {
    AppTheme { DistanceInfo(distance = "423 mi.") }
}
