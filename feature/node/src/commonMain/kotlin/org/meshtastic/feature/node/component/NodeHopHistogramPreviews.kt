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
@file:Suppress("MagicNumber", "PreviewPublic")

package org.meshtastic.feature.node.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.theme.AppTheme

// A spread of hop distances with hop 4 intentionally missing, to show the preserved zero-count gap.
// Built inside a function (not an eager top-level val) so it can't poison this file's class initializer.
private fun sampleNodes(): List<Node> = buildList {
    listOf(0 to 8, 1 to 12, 2 to 5, 3 to 2, 5 to 3).forEach { (hop, count) ->
        repeat(count) { i -> add(Node(num = hop * 1000 + i, hopsAway = hop)) }
    }
}

@PreviewLightDark
@Composable
fun HopHistogramContentPreview() {
    val nodes = remember { sampleNodes() }
    AppTheme { Surface { HopHistogramContent(nodes = nodes, window = HopWindow.ALL, onSelectWindow = {}) } }
}

@PreviewLightDark
@Composable
fun HopHistogramEmptyPreview() {
    AppTheme { Surface { HopHistogramContent(nodes = emptyList(), window = HopWindow.ALL, onSelectWindow = {}) } }
}
