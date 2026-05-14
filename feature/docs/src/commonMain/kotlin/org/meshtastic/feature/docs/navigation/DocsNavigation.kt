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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.ui.DocsBrowserScreen
import org.meshtastic.feature.docs.ui.DocsPageRouteScreen
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

@Suppress("LongMethod")
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun DocsPageScreen(pageId: String, backStack: NavBackStack<NavKey>) {
    val bundleLoader = koinInject<DocBundleLoader>()
    val aiAssistant = koinInject<AIDocAssistant>()
    val scope = rememberCoroutineScope()

    var content by remember { mutableStateOf<DocPageContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isAiSupported by remember { mutableStateOf(false) }
    var showChirpy by remember { mutableStateOf(false) }
    var chirpyState by remember { mutableStateOf(AIDocAssistantSessionState()) }

    LaunchedEffect(pageId) {
        content = bundleLoader.readPage(pageId)
        isLoading = false
    }

    LaunchedEffect(Unit) { isAiSupported = aiAssistant.isSupported() }

    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, onBackCompleted = { backStack.removeLastOrNull() })

    DocsPageRouteScreen(
        pageId = pageId,
        content = content,
        isLoading = isLoading,
        isAiSupported = isAiSupported,
        showChirpy = showChirpy,
        chirpyState = chirpyState,
        onChirpyToggle = { showChirpy = !showChirpy },
        onChirpyDismiss = { showChirpy = false },
        onChirpyDraftChange = { chirpyState = chirpyState.copy(draftQuestion = it) },
        onChirpySubmit = {
            val question = chirpyState.draftQuestion.trim()
            if (question.isNotBlank() && !chirpyState.isLoading) {
                val userMsg = ChirpyMessage(id = Uuid.random().toString(), role = ChirpyRole.USER, text = question)
                chirpyState =
                    chirpyState.copy(messages = chirpyState.messages + userMsg, draftQuestion = "", isLoading = true)
                scope.launch {
                    val result = aiAssistant.answer(question)
                    val responseMsg =
                        when (result) {
                            is AIDocAssistantResult.Success ->
                                ChirpyMessage(
                                    id = Uuid.random().toString(),
                                    role = ChirpyRole.ASSISTANT,
                                    text = result.answer,
                                    sourcePageIds = result.sourcePages.map { it.id },
                                )

                            is AIDocAssistantResult.Fallback ->
                                ChirpyMessage(
                                    id = Uuid.random().toString(),
                                    role = ChirpyRole.ASSISTANT,
                                    text = result.message,
                                    sourcePageIds = result.suggestedPages.map { it.id },
                                )

                            is AIDocAssistantResult.Error ->
                                ChirpyMessage(
                                    id = Uuid.random().toString(),
                                    role = ChirpyRole.SYSTEM,
                                    text = "Sorry, I couldn't answer that. ${result.reason}",
                                )
                        }
                    chirpyState = chirpyState.copy(messages = chirpyState.messages + responseMsg, isLoading = false)
                }
            }
        },
        onBack = { backStack.removeLastOrNull() },
        onNavigateToPage = { targetPageId -> backStack.add(SettingsRoute.HelpDocPage(targetPageId)) },
    )
}
