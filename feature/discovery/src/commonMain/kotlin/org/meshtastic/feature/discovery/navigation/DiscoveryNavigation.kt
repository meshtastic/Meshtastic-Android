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
package org.meshtastic.feature.discovery.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.core.navigation.DiscoveryRoute
import org.meshtastic.feature.discovery.DiscoveryHistoryDetailViewModel
import org.meshtastic.feature.discovery.DiscoveryHistoryViewModel
import org.meshtastic.feature.discovery.DiscoveryMapViewModel
import org.meshtastic.feature.discovery.DiscoverySummaryViewModel
import org.meshtastic.feature.discovery.DiscoveryViewModel
import org.meshtastic.feature.discovery.ui.DiscoveryHistoryDetailScreen
import org.meshtastic.feature.discovery.ui.DiscoveryHistoryScreen
import org.meshtastic.feature.discovery.ui.DiscoveryMapScreen
import org.meshtastic.feature.discovery.ui.DiscoveryScanScreen
import org.meshtastic.feature.discovery.ui.DiscoverySummaryScreen

/** Registers the discovery feature screen entries into the Navigation 3 entry provider. */
fun EntryProviderScope<NavKey>.discoveryGraph(backStack: NavBackStack<NavKey>) {
    entry<DiscoveryRoute.DiscoveryGraph> { DiscoveryScanScreenEntry(backStack) }
    entry<DiscoveryRoute.DiscoveryScan> { DiscoveryScanScreenEntry(backStack) }
    entry<DiscoveryRoute.DiscoverySummary> { route ->
        val viewModel = koinViewModel<DiscoverySummaryViewModel> { parametersOf(route.sessionId) }
        DiscoverySummaryScreen(
            viewModel = viewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigateToMap = { sessionId -> backStack.add(DiscoveryRoute.DiscoveryMap(sessionId)) },
        )
    }
    entry<DiscoveryRoute.DiscoveryMap> { route ->
        val viewModel = koinViewModel<DiscoveryMapViewModel> { parametersOf(route.sessionId) }
        DiscoveryMapScreen(viewModel = viewModel, onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
    entry<DiscoveryRoute.DiscoveryHistory> {
        val viewModel = koinViewModel<DiscoveryHistoryViewModel>()
        val navigateToDetail: (Long) -> Unit = { sessionId ->
            backStack.add(DiscoveryRoute.DiscoveryHistoryDetail(sessionId))
        }
        DiscoveryHistoryScreen(
            viewModel = viewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigateToDetail = navigateToDetail,
        )
    }
    entry<DiscoveryRoute.DiscoveryHistoryDetail> { route ->
        val viewModel = koinViewModel<DiscoveryHistoryDetailViewModel> { parametersOf(route.sessionId) }
        DiscoveryHistoryDetailScreen(
            viewModel = viewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigateToMap = { sessionId -> backStack.add(DiscoveryRoute.DiscoveryMap(sessionId)) },
        )
    }
}

@Composable
private fun DiscoveryScanScreenEntry(backStack: NavBackStack<NavKey>) {
    val viewModel = koinViewModel<DiscoveryViewModel>()
    DiscoveryScanScreen(
        viewModel = viewModel,
        onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        onNavigateToSummary = { sessionId -> backStack.add(DiscoveryRoute.DiscoverySummary(sessionId)) },
        onNavigateToHistory = dropUnlessResumed { backStack.add(DiscoveryRoute.DiscoveryHistory) },
    )
}
