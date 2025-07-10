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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.connections.ConnectionsScreen
import com.geeksville.mesh.ui.radioconfig.components.LoRaConfigScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class ConnectionsRoutes {
    @Serializable
    data object ConnectionsGraph : Graph

    @Serializable
    data object Connections : Route
}

/**
 * Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoutes.Connections].
 */
fun NavGraphBuilder.connectionsGraph(
    navController: NavHostController,
    uiViewModel: UIViewModel,
    bluetoothViewModel: BluetoothViewModel
) {
    navigation<ConnectionsRoutes.ConnectionsGraph>(
        startDestination = ConnectionsRoutes.Connections,
    ) {
        composable<ConnectionsRoutes.Connections>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/connections"
                    action = "android.intent.action.VIEW"
                }
            )
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                val parentRoute = backStackEntry.destination.parent!!.route!!
                navController.getBackStackEntry(parentRoute)
            }
            ConnectionsScreen(
                uiViewModel = uiViewModel,
                bluetoothViewModel = bluetoothViewModel,
                radioConfigViewModel = hiltViewModel(parentEntry),
                onNavigateToRadioConfig = { navController.navigate(RadioConfigRoutes.RadioConfig()) },
                onNavigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                onConfigNavigate = { route -> navController.navigate(route) }
            )
        }
        configRoutes(navController)
    }
}

private fun NavGraphBuilder.configRoutes(
    navController: NavHostController,
) {
    composable<RadioConfigRoutes.LoRa> { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            val parentRoute = backStackEntry.destination.parent!!.route!!
            navController.getBackStackEntry(parentRoute)
        }
        LoRaConfigScreen(hiltViewModel(parentEntry))
    }
}
