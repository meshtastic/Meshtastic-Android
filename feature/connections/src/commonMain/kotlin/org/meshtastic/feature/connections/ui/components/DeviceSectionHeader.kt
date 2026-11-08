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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Section header used to group content in the Connections list.
 *
 * Follows the Material 3 "header + trailing action" pattern: a [titleSmall] label on the left, an optional composable
 * [trailing] slot on the right (typically a [androidx.compose.material3.TextButton] for a scan toggle), and an optional
 * thin [LinearProgressIndicator] rendered underneath when [showProgress] is true.
 *
 * The title and the trailing action share a vertically centered line; when they don't fit side by side (a large font
 * scale, a long translation) the action wraps below the title instead of squeezing it. The title is a heading, so
 * screen readers can jump between sections.
 */
@Composable
fun DeviceSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            trailing()
        }
        if (showProgress) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
