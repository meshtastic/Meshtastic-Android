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

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.geeksville.mesh.ui.connections.ConnectionsScreen
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen

/** Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoutes.Connections]. */
fun NavGraphBuilder.connectionsGraph(navController: NavHostController) {
    @Suppress("ktlint:standard:max-line-length")
    navigation<ConnectionsRoutes.ConnectionsGraph>(startDestination = ConnectionsRoutes.Connections) {
        composable<ConnectionsRoutes.Connections>(
            deepLinks = listOf(
                navDeepLink<ConnectionsRoutes.Connections>(basePath = "$DEEP_LINK_BASE_URI/connections"),
            ),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) { navController.getBackStackEntry(ConnectionsRoutes.ConnectionsGraph) }
            ConnectionsScreen(
                radioConfigViewModel = hiltViewModel(parentEntry),
                onClickNodeChip = {
                    navController.navigate(NodesRoutes.NodeDetailGraph(it)) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                onConfigNavigate = { route -> navController.navigate(route) },
            )
        }

        navController.configComposable<SettingsRoutes.LoRa, ConnectionsRoutes.ConnectionsGraph> {
            LoRaConfigScreen(viewModel = it, onBack = navController::popBackStack)
        }
    }
}
