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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_layout_help_signal_bad
import org.meshtastic.core.resources.node_layout_help_signal_fair
import org.meshtastic.core.resources.node_layout_help_signal_good
import org.meshtastic.core.resources.node_layout_help_signal_indicator
import org.meshtastic.core.resources.node_layout_help_signal_none
import org.meshtastic.core.resources.node_layout_signal_quality_indicator
import org.meshtastic.core.resources.node_list_help_node_details
import org.meshtastic.core.resources.node_list_help_title
import org.meshtastic.core.ui.component.Quality

private const val ICON_SIZE = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeListHelp(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.node_list_help_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )

            HorizontalDivider()

            Text(
                text = stringResource(Res.string.node_list_help_node_details),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            SignalQualityEntry(Quality.GOOD, stringResource(Res.string.node_layout_help_signal_good))
            SignalQualityEntry(Quality.FAIR, stringResource(Res.string.node_layout_help_signal_fair))
            SignalQualityEntry(Quality.BAD, stringResource(Res.string.node_layout_help_signal_bad))
            SignalQualityEntry(Quality.NONE, stringResource(Res.string.node_layout_help_signal_none))

            HorizontalDivider()

            Text(
                text = stringResource(Res.string.node_layout_signal_quality_indicator),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.node_layout_help_signal_indicator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalQualityEntry(quality: Quality, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(quality.icon),
            contentDescription = stringResource(quality.nameRes),
            modifier = Modifier.size(ICON_SIZE.dp),
            tint = quality.color(),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(quality.nameRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
