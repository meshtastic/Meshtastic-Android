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
package org.meshtastic.feature.map.node

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.map.component.NodeTrackMap

/**
 * Full-screen map showing a single node's position history.
 *
 * Includes a Scaffold with AppBar showing the node's long name. Replaces both the Google Maps and OSMDroid
 * flavor-specific NodeMapScreen implementations.
 */
@Composable
fun NodeMapScreen(viewModel: NodeMapViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val node by viewModel.node.collectAsStateWithLifecycle()
    val positions by viewModel.positionLogs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = node?.user?.long_name ?: stringResource(Res.string.map),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        NodeTrackMap(positions = positions, modifier = Modifier.fillMaxSize().padding(paddingValues))
    }
}
