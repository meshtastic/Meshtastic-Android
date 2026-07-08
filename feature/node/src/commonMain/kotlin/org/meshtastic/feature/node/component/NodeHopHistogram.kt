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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowInstant
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.a11y_nodes_at_hop
import org.meshtastic.core.resources.all_time
import org.meshtastic.core.resources.eight_hours
import org.meshtastic.core.resources.hop_histogram_empty
import org.meshtastic.core.resources.hop_histogram_title
import org.meshtastic.core.resources.one_hour
import org.meshtastic.core.resources.twenty_four_hours
import kotlin.time.Duration.Companion.hours

/**
 * Counts nodes at each hop distance, as a list indexed by hop (element 0 = direct nodes). The list is contiguous from
 * hop 0 up to the furthest hop observed, so zero-count gaps in the middle are preserved. Nodes with an unknown hop
 * (`hopsAway < 0`) are excluded. When [cutoffSecs] is non-null, only nodes with `lastHeard >= cutoffSecs` are counted.
 * Returns an empty list when nothing qualifies.
 */
fun hopHistogram(nodes: List<Node>, cutoffSecs: Int?): List<Int> {
    val counts =
        nodes
            .filter { it.hopsAway >= 0 && (cutoffSecs == null || it.lastHeard >= cutoffSecs) }
            .groupingBy { it.hopsAway }
            .eachCount()
    val maxHop = counts.keys.maxOrNull() ?: return emptyList()
    return (0..maxHop).map { counts[it] ?: 0 }
}

/**
 * Bottom sheet showing how many mesh nodes sit at each hop distance, filtered to a "last heard" [HopWindow]. Gives a
 * quick sense of how busy and dispersed the local mesh is (see issue #5745).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeHopHistogramSheet(nodes: List<Node>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var window by remember { mutableStateOf(HopWindow.EIGHT_HOURS) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        HopHistogramContent(nodes = nodes, window = window, onSelectWindow = { window = it })
    }
}

/** Stateless sheet content — split out from [NodeHopHistogramSheet] so it can be previewed without a sheet host. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HopHistogramContent(nodes: List<Node>, window: HopWindow, onSelectWindow: (HopWindow) -> Unit) {
    val cutoffSecs =
        remember(window) { window.hours?.let { (nowInstant.epochSeconds - it.hours.inWholeSeconds).toInt() } }
    val buckets = remember(nodes, cutoffSecs) { hopHistogram(nodes, cutoffSecs) }
    val maxCount = buckets.maxOrNull() ?: 0

    Column(
        modifier =
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.hop_histogram_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HopWindow.entries.forEach { option ->
                FilterChip(
                    selected = option == window,
                    onClick = { onSelectWindow(option) },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }

        if (buckets.isEmpty()) {
            Text(
                text = stringResource(Res.string.hop_histogram_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            buckets.forEachIndexed { hop, count -> HopBar(hop = hop, count = count, maxCount = maxCount) }
        }
    }
}

@Composable
private fun HopBar(hop: Int, count: Int, maxCount: Int) {
    // Merge the row into one spoken node so a screen reader announces "Hop 3: 2 nodes" rather than
    // reading the hop number, progress-bar percentage, and count as three disjoint elements.
    val description = stringResource(Res.string.a11y_nodes_at_hop, hop, count)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = hop.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 20.dp),
        )
        LinearProgressIndicator(
            progress = { if (maxCount > 0) count.toFloat() / maxCount else 0f },
            modifier = Modifier.weight(1f),
            trackColor = ProgressIndicatorDefaults.linearTrackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
    }
}

/** "Last heard" windows offered above the histogram. A `null` [hours] means no time filter (all known nodes). */
@Suppress("MagicNumber")
enum class HopWindow(val hours: Long?, val label: StringResource) {
    ALL(null, Res.string.all_time),
    ONE_HOUR(1, Res.string.one_hour),
    EIGHT_HOURS(8, Res.string.eight_hours),
    ONE_DAY(24, Res.string.twenty_four_hours),
}
