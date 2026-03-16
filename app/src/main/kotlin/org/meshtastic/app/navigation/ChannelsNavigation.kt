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

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.app.ui.sharing.ChannelScreen
import org.meshtastic.core.navigation.ChannelsRoutes

/** Navigation graph for for the top level ChannelScreen - [ChannelsRoutes.Channels]. */
fun EntryProviderScope<NavKey>.channelsGraph(backStack: NavBackStack<NavKey>) {
    entry<ChannelsRoutes.ChannelsGraph> {
        ChannelScreen(
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onNavigate = { route -> backStack.add(route) },
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    entry<ChannelsRoutes.Channels> {
        ChannelScreen(
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onNavigate = { route -> backStack.add(route) },
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }
}
