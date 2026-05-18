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
package org.meshtastic.app.map.node

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.app.map.GoogleMapMode
import org.meshtastic.app.map.MapView
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.map.node.NodeMapViewModel

@Composable
fun NodeMapScreen(nodeMapViewModel: NodeMapViewModel, onNavigateUp: () -> Unit) {
    val node by nodeMapViewModel.node.collectAsStateWithLifecycle()
    val positions by nodeMapViewModel.positionLogs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = node?.user?.long_name ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        MapView(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            mode = GoogleMapMode.NodeTrack(focusedNode = node, positions = positions),
        )
    }
}
