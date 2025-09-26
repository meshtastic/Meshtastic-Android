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

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.geeksville.mesh.ui.map.MapScreen
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.core.navigation.NodesRoutes

fun NavGraphBuilder.mapGraph(navController: NavHostController) {
    composable<MapRoutes.Map>(deepLinks = listOf(navDeepLink<MapRoutes.Map>(basePath = "$DEEP_LINK_BASE_URI/map"))) {
        MapScreen(
            onClickNodeChip = {
                navController.navigate(NodesRoutes.NodeDetailGraph(it)) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
        )
    }
}
