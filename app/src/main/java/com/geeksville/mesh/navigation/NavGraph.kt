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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.debug.DebugScreen
import kotlinx.serialization.Serializable

enum class AdminRoute(@StringRes val title: Int) {
    REBOOT(R.string.reboot),
    SHUTDOWN(R.string.shutdown),
    FACTORY_RESET(R.string.factory_reset),
    NODEDB_RESET(R.string.nodedb_reset),
}

const val DEEP_LINK_BASE_URI = "meshtastic://meshtastic"

@Serializable
sealed interface Graph : Route
@Serializable
sealed interface Route {
    @Serializable
    data object DebugPanel : Route
}

fun NavDestination.isConfigRoute(): Boolean {
    return ConfigRoute.entries.any { hasRoute(it.route::class) } ||
            ModuleRoute.entries.any { hasRoute(it.route::class) }
}

fun NavDestination.isNodeDetailRoute(): Boolean {
    return NodeDetailRoute.entries.any { hasRoute(it.route::class) }
}

fun NavDestination.showLongNameTitle(): Boolean {

    return !this.isTopLevel() && (
            this.hasRoute<RadioConfigRoutes.RadioConfig>() ||
                    this.hasRoute<NodesRoutes.NodeDetail>() ||
                    this.isConfigRoute() ||
                    this.isNodeDetailRoute()
            )
}

@Suppress("LongMethod")
@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    uIViewModel: UIViewModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (uIViewModel.isConnected()) {
            NodesRoutes.NodesGraph
        } else {
            ConnectionsRoutes.ConnectionsGraph
        },
        modifier = modifier,
    ) {
        contactsGraph(navController, uIViewModel)
        nodesGraph(navController, uIViewModel,)
        mapGraph(navController, uIViewModel)
        channelsGraph(navController, uIViewModel)
        connectionsGraph(navController, uIViewModel, bluetoothViewModel)
        composable<Route.DebugPanel> { DebugScreen() }
        radioConfigGraph(navController, uIViewModel)
    }
}
