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
import com.geeksville.mesh.ui.sharing.ChannelScreen
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.radio.component.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen

/** Navigation graph for for the top level ChannelScreen - [ChannelsRoutes.Channels]. */
fun NavGraphBuilder.channelsGraph(navController: NavHostController) {
    navigation<ChannelsRoutes.ChannelsGraph>(startDestination = ChannelsRoutes.Channels) {
        composable<ChannelsRoutes.Channels>(
            deepLinks = listOf(navDeepLink<ChannelsRoutes.Channels>(basePath = "$DEEP_LINK_BASE_URI/channels")),
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ChannelsRoutes.ChannelsGraph) }
            ChannelScreen(
                radioConfigViewModel = hiltViewModel(parentEntry),
                onNavigate = { route -> navController.navigate(route) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        configRoutes(navController)
    }
}

private fun NavGraphBuilder.configRoutes(navController: NavHostController) {
    ConfigRoute.entries.forEach { configRoute ->
        composable(configRoute.route::class) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ChannelsRoutes.ChannelsGraph) }
            when (configRoute) {
                ConfigRoute.CHANNELS -> ChannelConfigScreen(hiltViewModel(parentEntry), navController::popBackStack)
                ConfigRoute.LORA -> LoRaConfigScreen(hiltViewModel(parentEntry), navController::popBackStack)
                else -> Unit // Should not happen if ConfigRoute enum is exhaustive for this context
            }
        }
    }
}
