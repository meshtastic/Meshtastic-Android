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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun LastHeardInfo(modifier: Modifier = Modifier, lastHeard: Int, currentTimeMillis: Long) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            modifier = Modifier.height(18.dp),
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_antenna_24),
            contentDescription = null,
        )
        Text(text = formatAgo(lastHeard, currentTimeMillis), fontSize = MaterialTheme.typography.labelLarge.fontSize)
    }
}

@PreviewLightDark
@Composable
fun LastHeardInfoPreview() {
    AppTheme {
        LastHeardInfo(
            lastHeard = (System.currentTimeMillis() / 1000).toInt() - 8600,
            currentTimeMillis = System.currentTimeMillis(),
        )
    }
}
