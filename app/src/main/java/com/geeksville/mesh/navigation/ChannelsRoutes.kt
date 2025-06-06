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
import androidx.navigation.navigation
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.radioconfig.components.ChannelConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.LoRaConfigScreen
import com.geeksville.mesh.ui.sharing.ChannelScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class ChannelsRoutes {
    @Serializable
    data object ChannelsGraph : Graph

    @Serializable
    data object Channels : Route
}

/**
 * Navigation graph for for the top level ChannelScreen - [ChannelsRoutes.Channels].
 */
fun NavGraphBuilder.channelsGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<ChannelsRoutes.ChannelsGraph>(
        startDestination = ChannelsRoutes.Channels,
    ) {
        composable<ChannelsRoutes.Channels> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<ChannelsRoutes.ChannelsGraph>()
            }
            ChannelScreen(
                viewModel = uiViewModel,
                radioConfigViewModel = hiltViewModel(parentEntry),
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        configRoutes(navController)
    }
}

private fun NavGraphBuilder.configRoutes(
    navController: NavHostController,
) {
    ConfigRoute.entries.forEach { configRoute ->
        composable(configRoute.route::class) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<ChannelsRoutes.ChannelsGraph>()
            }
            when (configRoute) {
                ConfigRoute.CHANNELS -> ChannelConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.LORA -> LoRaConfigScreen(hiltViewModel(parentEntry))
                else -> Unit
            }
        }
    }
}
