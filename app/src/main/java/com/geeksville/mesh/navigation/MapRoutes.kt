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

import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.MapView
import com.geeksville.mesh.ui.map.MapViewModel
import kotlinx.serialization.Serializable

sealed class MapRoutes {
    @Serializable data object Map : Route
}

fun NavGraphBuilder.mapGraph(navController: NavHostController, uiViewModel: UIViewModel, mapViewModel: MapViewModel) {
    composable<MapRoutes.Map>(
        deepLinks =
        listOf(
            navDeepLink {
                uriPattern = "$DEEP_LINK_BASE_URI/map"
                action = Intent.ACTION_VIEW
            },
        ),
    ) {
        MapView(
            uiViewModel = uiViewModel,
            mapViewModel = mapViewModel,
            navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
        )
    }
}
