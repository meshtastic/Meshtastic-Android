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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_count_online
import org.meshtastic.core.resources.node_count_shown
import org.meshtastic.core.resources.node_count_template
import org.meshtastic.core.resources.node_count_total

/** Horizontal gap between two count labels that share a line. */
private val LABEL_GAP = 12.dp

/** Vertical gap between wrapped lines of count labels. */
private val LINE_GAP = 2.dp

/**
 * Node totals for the Nodes list header: how many nodes are online, how many the current filter shows, and how many are
 * known in total.
 *
 * This used to live in the app bar `subtitle` slot, where it was clipped by the fixed app-bar height and squeezed
 * horizontally by the node chip plus two icon buttons — the single long string was ellipsized on most phones
 * (issue #6268). Here it gets the full content width and, crucially, is free to grow vertically: each count is an
 * independent label inside a [FlowRow], so at large font scales or in verbose locales the labels reflow onto extra
 * lines instead of truncating. No `maxLines`, no ellipsis, no autosizing anywhere in this component.
 *
 * Screen readers get one coherent sentence via [Res.string.node_count_template] rather than three disjointed fragments.
 */
@Composable
fun NodeCountSummary(onlineCount: Int, shownCount: Int, totalCount: Int, modifier: Modifier = Modifier) {
    NodeCountSummaryContent(
        labels =
        listOf(
            stringResource(Res.string.node_count_online, onlineCount),
            stringResource(Res.string.node_count_shown, shownCount),
            stringResource(Res.string.node_count_total, totalCount),
        ),
        contentDescription = stringResource(Res.string.node_count_template, onlineCount, shownCount, totalCount),
        modifier = modifier,
    )
}

/**
 * Layout half of [NodeCountSummary], split out so previews can inject deliberately long strings (long locales) without
 * needing a translated resource bundle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NodeCountSummaryContent(labels: List<String>, contentDescription: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
