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

package com.geeksville.mesh.ui.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.map.MapView

const val DEG_D = 1e-7

@Composable
fun NodeMapScreen(
    navController: NavHostController,
    nodeMapViewModel: NodeMapViewModel = hiltViewModel(),
    metricsViewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by metricsViewModel.state.collectAsState()
    val positions = state.positionLogs
    val destNum = state.node?.num
    val ourNodeInfo by nodeMapViewModel.ourNodeInfo.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.longName ?: "",
                ourNode = ourNodeInfo,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = navController::navigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            MapView(focusedNodeNum = destNum, nodeTracks = positions, navigateToNodeDetails = {})
        }
    }
}
