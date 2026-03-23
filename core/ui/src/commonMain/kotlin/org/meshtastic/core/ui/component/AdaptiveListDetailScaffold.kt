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
package org.meshtastic.core.ui.component

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> AdaptiveListDetailScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    onBackToGraph: () -> Unit,
    onTabPressedEvent: (ScrollToTopEvent) -> Boolean,
    initialKey: T? = null,
    listPane: @Composable (isActive: Boolean, contentKey: T?) -> Unit,
    detailPane: @Composable (contentKey: T, handleBack: () -> Unit) -> Unit,
    emptyDetailPane: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    val handleBack: () -> Unit = {
        if (navigator.canNavigateBack(backNavigationBehavior)) {
            scope.launch { navigator.navigateBack(backNavigationBehavior) }
        } else {
            onBackToGraph()
        }
    }

    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navState,
        isBackEnabled = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail,
        onBackCancelled = {},
        onBackCompleted = { handleBack() },
    )

    LaunchedEffect(initialKey) {
        if (initialKey != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, initialKey)
        }
    }

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents.collect { event ->
            if (onTabPressedEvent(event) && navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
                if (navigator.canNavigateBack(backNavigationBehavior)) {
                    navigator.navigateBack(backNavigationBehavior)
                } else {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                }
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                val focusManager = LocalFocusManager.current
                // Prevent TextFields from auto-focusing when pane animates in
                LaunchedEffect(Unit) { focusManager.clearFocus() }

                listPane(
                    navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.List,
                    navigator.currentDestination?.contentKey,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val focusManager = LocalFocusManager.current

                navigator.currentDestination?.contentKey?.let { contentKey ->
                    key(contentKey) {
                        LaunchedEffect(contentKey) { focusManager.clearFocus() }
                        detailPane(contentKey, handleBack)
                    }
                } ?: emptyDetailPane()
            }
        },
    )
}
