/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.navigation

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.settings.AndroidRadioConfigViewModel
import org.meshtastic.app.ui.sharing.ChannelScreen
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.feature.settings.radio.channel.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen

/** Navigation graph for for the top level ChannelScreen - [ChannelsRoutes.Channels]. */
fun NavGraphBuilder.channelsGraph(navController: NavHostController) {
    navigation<ChannelsRoutes.ChannelsGraph>(startDestination = ChannelsRoutes.Channels) {
        composable<ChannelsRoutes.Channels>(
            deepLinks = listOf(navDeepLink<ChannelsRoutes.Channels>(basePath = "$DEEP_LINK_BASE_URI/channels")),
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ChannelsRoutes.ChannelsGraph) }
            ChannelScreen(
                radioConfigViewModel = koinViewModel<AndroidRadioConfigViewModel>(viewModelStoreOwner = parentEntry),
                onNavigate = { route -> navController.navigate(route) },
                onNavigateUp = { navController.navigateUp() },
            )
        }

        navController.configComposable<SettingsRoutes.ChannelConfig, ChannelsRoutes.ChannelsGraph> {
            ChannelConfigScreen(viewModel = it, onBack = navController::popBackStack)
        }

        navController.configComposable<SettingsRoutes.LoRa, ChannelsRoutes.ChannelsGraph> {
            LoRaConfigScreen(viewModel = it, onBack = navController::popBackStack)
        }
    }
}
