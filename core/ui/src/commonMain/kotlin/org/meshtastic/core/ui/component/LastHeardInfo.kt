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
package org.meshtastic.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_antenna
import org.meshtastic.core.resources.node_sort_last_heard
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.formatAgo

@Composable
fun LastHeardInfo(
    modifier: Modifier = Modifier,
    lastHeard: Int,
    showLabel: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = vectorResource(Res.drawable.ic_antenna),
        contentDescription = stringResource(Res.string.node_sort_last_heard),
        label = if (showLabel) stringResource(Res.string.node_sort_last_heard) else null,
        text = formatAgo(lastHeard),
        contentColor = contentColor,
    )
}

@PreviewLightDark
@Composable
private fun LastHeardInfoPreview() {
    AppTheme { LastHeardInfo(lastHeard = nowSeconds.toInt() - 8600) }
}
