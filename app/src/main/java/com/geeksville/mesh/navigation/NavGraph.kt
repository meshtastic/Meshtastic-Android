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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.map.MapViewModel
import kotlinx.serialization.Serializable

enum class AdminRoute(val icon: ImageVector, @StringRes val title: Int) {
    REBOOT(Icons.Rounded.RestartAlt, R.string.reboot),
    SHUTDOWN(Icons.Rounded.PowerSettingsNew, R.string.shutdown),
    FACTORY_RESET(Icons.Rounded.Restore, R.string.factory_reset),
    NODEDB_RESET(Icons.Rounded.Storage, R.string.nodedb_reset),
}

const val DEEP_LINK_BASE_URI = "meshtastic://meshtastic"

@Serializable sealed interface Graph : Route

@Serializable sealed interface Route

fun NavDestination.isConfigRoute(): Boolean =
    ConfigRoute.entries.any { hasRoute(it.route::class) } || ModuleRoute.entries.any { hasRoute(it.route::class) }

fun NavDestination.isNodeDetailRoute(): Boolean = NodeDetailRoute.entries.any { hasRoute(it.route::class) }

fun NavDestination.showLongNameTitle(): Boolean = !this.isTopLevel() &&
    (
        this.hasRoute<SettingsRoutes.Settings>() ||
            this.hasRoute<NodesRoutes.NodeDetail>() ||
            this.isConfigRoute() ||
            this.isNodeDetailRoute()
        )

@Suppress("LongMethod")
@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    uIViewModel: UIViewModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = NodesRoutes.NodesGraph, modifier = modifier) {
        contactsGraph(navController, uIViewModel)
        nodesGraph(navController, uIViewModel)
        mapGraph(navController, uIViewModel, mapViewModel)
        channelsGraph(navController, uIViewModel)
        connectionsGraph(navController, uIViewModel, bluetoothViewModel)
        settingsGraph(navController, uIViewModel)
    }
}
