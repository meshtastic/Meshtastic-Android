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

package com.geeksville.mesh.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import org.meshtastic.core.strings.R

@Composable
fun MapScreen(
    onClickNodeChip: (Int) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = hiltViewModel(),
) {
    val ourNodeInfo by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()

    @Suppress("ViewModelForwarding")
    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(R.string.map),
                ourNode = ourNodeInfo,
                isConnected = isConnected,
                showNodeChip = ourNodeInfo != null && isConnected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onAction = { action ->
                    when (action) {
                        is NodeMenuAction.MoreDetails -> onClickNodeChip(action.node.num)
                        else -> {}
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            MapView(mapViewModel = mapViewModel, navigateToNodeDetails = navigateToNodeDetails)
        }
    }
}
