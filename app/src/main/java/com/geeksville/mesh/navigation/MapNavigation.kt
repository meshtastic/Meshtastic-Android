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

package com.geeksville.mesh.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.map.MapView
import com.geeksville.mesh.ui.node.components.NodeMenuAction

fun NavGraphBuilder.mapGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    composable<MapRoutes.Map>(deepLinks = listOf(navDeepLink<MapRoutes.Map>(basePath = "$DEEP_LINK_BASE_URI/map"))) {
        val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
        val isConnected by uiViewModel.isConnectedStateFlow.collectAsStateWithLifecycle()

        Scaffold(
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
                            is NodeMenuAction.MoreDetails -> {
                                navController.navigate(NodesRoutes.NodeDetailGraph(action.node.num)) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            else -> {}
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                MapView(
                    uiViewModel = uiViewModel,
                    navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                )
            }
        }
    }
}
