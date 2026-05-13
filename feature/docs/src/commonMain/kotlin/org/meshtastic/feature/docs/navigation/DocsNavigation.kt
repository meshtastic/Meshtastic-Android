/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.docs.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.koin.compose.koinInject
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.ui.DocsBrowserScreen
import org.meshtastic.feature.docs.ui.DocsPageRouteScreen

/** Registers docs navigation entries into the Settings navigation graph. */
fun EntryProviderScope<NavKey>.docsEntries(backStack: NavBackStack<NavKey>) {
    entry<SettingsRoute.HelpDocs> { DocsHelpScreen(backStack = backStack) }

    entry<SettingsRoute.HelpDocPage> { route -> DocsPageScreen(pageId = route.pageId, backStack = backStack) }
}

@Composable
private fun DocsHelpScreen(backStack: NavBackStack<NavKey>) {
    val bundleLoader = koinInject<DocBundleLoader>()
    val searchEngine = koinInject<KeywordSearchEngine>()

    var pages by remember { mutableStateOf<List<DocPage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val bundle = bundleLoader.load()
        pages = bundle.pages.sortedWith(compareBy({ it.section.toString() }, { it.navOrder }))
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            val bundle = bundleLoader.load()
            pages = bundle.pages.sortedWith(compareBy({ it.section.toString() }, { it.navOrder }))
        } else {
            val results = searchEngine.search(searchQuery)
            pages = results.map { it.page }
        }
    }

    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, onBackCompleted = { backStack.removeLastOrNull() })

    DocsBrowserScreen(
        pages = pages,
        isLoading = isLoading,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onSelectPage = { pageId -> backStack.add(SettingsRoute.HelpDocPage(pageId)) },
        onBack = { backStack.removeLastOrNull() },
    )
}

@Composable
private fun DocsPageScreen(pageId: String, backStack: NavBackStack<NavKey>) {
    val bundleLoader = koinInject<DocBundleLoader>()

    var content by remember { mutableStateOf<DocPageContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageId) {
        content = bundleLoader.readPage(pageId)
        isLoading = false
    }

    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, onBackCompleted = { backStack.removeLastOrNull() })

    val uriHandler = LocalUriHandler.current
    DocsPageRouteScreen(
        pageId = pageId,
        content = content,
        isLoading = isLoading,
        onBack = { backStack.removeLastOrNull() },
        onNavigateToPage = { targetPageId -> backStack.add(SettingsRoute.HelpDocPage(targetPageId)) },
        onDeepLink = { deepLinkUri -> uriHandler.openUri(deepLinkUri) },
    )
}
