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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.ui.theme.AppTheme

private val previewToggles =
    NodeFilterToggles(
        includeUnknown = true,
        onToggleIncludeUnknown = {},
        excludeInfrastructure = false,
        onToggleExcludeInfrastructure = {},
        onlyOnline = false,
        onToggleOnlyOnline = {},
        onlyDirect = false,
        onToggleOnlyDirect = {},
        showIgnored = false,
        onToggleShowIgnored = {},
        ignoredNodeCount = 0,
        excludeUnheard = false,
        onToggleExcludeUnheard = {},
        excludeMqtt = false,
        onToggleExcludeMqtt = {},
        onlySigned = false,
        onToggleOnlySigned = {},
        onlyEncrypted = false,
        onToggleOnlyEncrypted = {},
    )

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun NodeFilterSearchBarEmptyPreview() {
    AppTheme {
        Surface {
            NodeFilterSearchBar(
                filterText = "",
                onTextChange = {},
                currentSortOption = NodeSortOption.LAST_HEARD,
                onSortSelect = {},
                toggles = previewToggles,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun NodeFilterSearchBarWithQueryPreview() {
    AppTheme {
        Surface {
            NodeFilterSearchBar(
                filterText = "kolsås",
                onTextChange = {},
                currentSortOption = NodeSortOption.LAST_HEARD,
                onSortSelect = {},
                toggles = previewToggles,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
