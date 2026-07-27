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

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.currentLocaleCode
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.ai.ChirpySessionHolder
import org.meshtastic.feature.docs.data.DefaultDocBundleLoader
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.ModelReadiness
import org.meshtastic.feature.docs.model.TranslationSource
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.TranslationResult
import org.meshtastic.feature.docs.ui.DocsBrowserScreen
import org.meshtastic.feature.docs.ui.DocsPageRouteScreen

/** Registers docs navigation entries into the Settings navigation graph. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.docsEntries(backStack: NavBackStack<NavKey>) {
    entry<SettingsRoute.HelpDocs>(metadata = { ListDetailSceneStrategy.listPane() }) {
        val hasDetailSelected = remember(backStack) { backStack.any { it is SettingsRoute.HelpDocPage } }
        val chirpy = rememberChirpyState(backStack = backStack, currentPageId = null, showFab = !hasDetailSelected)
        DocsHelpScreen(backStack = backStack, chirpy = chirpy)
    }

    entry<SettingsRoute.HelpDocPage>(metadata = { ListDetailSceneStrategy.detailPane() }) { route ->
        val chirpy = rememberChirpyState(backStack = backStack, currentPageId = route.pageId, showFab = true)
        DocsPageScreen(pageId = route.pageId, backStack = backStack, chirpy = chirpy)
    }
}

// ── Shared Chirpy state holder ──────────────────────────────────────────────────

/** All Chirpy UI state needed by screen composables. */
class ChirpyUiState(
    val isSupported: Boolean,
    val modelReadiness: ModelReadiness,
    val showFab: Boolean,
    val showSheet: Boolean,
    val sessionState: org.meshtastic.feature.docs.model.AIDocAssistantSessionState,
    val onToggle: () -> Unit,
    val onDismiss: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onNavigateToPage: (String) -> Unit,
)

@Composable
private fun rememberChirpyState(
    backStack: NavBackStack<NavKey>,
    currentPageId: String?,
    showFab: Boolean,
): ChirpyUiState {
    val aiAssistant = koinInject<AIDocAssistant>()
    val holder = koinInject<ChirpySessionHolder>()

    val modelReadiness by aiAssistant.modelStatus.collectAsState()
    var isSupported by remember { mutableStateOf(false) }

    // Trigger initial availability check and model download.
    LaunchedEffect(Unit) { isSupported = aiAssistant.isSupported() }

    // Show FAB for any non-Unavailable state so the expressive FAB can communicate progress.
    LaunchedEffect(modelReadiness) {
        if (modelReadiness !is ModelReadiness.Unavailable) {
            isSupported = true
        }
    }

    // Auto-introduce Chirpy when the sheet first opens on a ready model. Only the trigger is scoped to this pane; the
    // request itself runs on the holder's scope, so an answer in flight survives the pane being disposed.
    LaunchedEffect(holder.showSheet, modelReadiness) {
        if (holder.showSheet && modelReadiness is ModelReadiness.Available) {
            holder.introduce()
        }
    }

    return ChirpyUiState(
        isSupported = isSupported,
        modelReadiness = modelReadiness,
        showFab = showFab,
        showSheet = holder.showSheet,
        sessionState = holder.sessionState,
        onToggle = { holder.showSheet = !holder.showSheet },
        onDismiss = { holder.showSheet = false },
        onDraftChange = { holder.sessionState = holder.sessionState.copy(draftQuestion = it) },
        onSubmit = { holder.submit(currentPageId) },
        onNavigateToPage = { pageId ->
            holder.showSheet = false
            backStack.add(SettingsRoute.HelpDocPage(pageId))
        },
    )
}

// ── Screen composables ──────────────────────────────────────────────────────────

@Composable
private fun DocsHelpScreen(backStack: NavBackStack<NavKey>, chirpy: ChirpyUiState) {
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
        isAiSupported = chirpy.isSupported,
        modelReadiness = chirpy.modelReadiness,
        showFab = chirpy.showFab,
        showChirpy = chirpy.showSheet,
        chirpyState = chirpy.sessionState,
        onChirpyToggle = chirpy.onToggle,
        onChirpyDismiss = chirpy.onDismiss,
        onChirpyDraftChange = chirpy.onDraftChange,
        onChirpySubmit = chirpy.onSubmit,
        onChirpyNavigateToPage = chirpy.onNavigateToPage,
    )
}

@Suppress("LongMethod")
@Composable
private fun DocsPageScreen(pageId: String, backStack: NavBackStack<NavKey>, chirpy: ChirpyUiState) {
    val bundleLoader = koinInject<DocBundleLoader>()
    val translationService = koinInject<DocTranslationService>()

    var content by remember { mutableStateOf<DocPageContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var translationSource by remember { mutableStateOf<TranslationSource>(TranslationSource.BUNDLED) }

    val locale = currentLocaleCode()

    LaunchedEffect(pageId, locale) {
        isLoading = true
        val loader = bundleLoader as? DefaultDocBundleLoader

        // Try locale-aware loading: Crowdin bundle first, then English fallback
        val (loaded, wasCrowdinLocalized) =
            if (loader != null) {
                withContext(ioDispatcher) { loader.readPageLocalized(pageId, locale) }
            } else {
                withContext(ioDispatcher) { bundleLoader.readPage(pageId) } to false
            }

        when {
            // Crowdin provided a localized version — use it directly
            wasCrowdinLocalized && loaded != null -> {
                content = loaded
                translationSource = TranslationSource.BUNDLED
                isLoading = false
            }

            // Non-English with no Crowdin — attempt ML Kit runtime translation
            locale != "en" && loaded != null -> {
                // Show English content immediately while translation runs
                content = loaded
                translationSource = TranslationSource.BUNDLED
                isLoading = false

                val result =
                    withContext(ioDispatcher) {
                        translationService.translatePage(pageId, loaded.markdown ?: "", locale)
                    }
                when (result) {
                    is TranslationResult.Success -> {
                        content = loaded.copy(markdown = result.translatedMarkdown)
                        translationSource = TranslationSource.ML_KIT
                    }

                    else -> {
                        /* Keep English content already displayed */
                    }
                }
            }

            // English locale or load failure
            else -> {
                content = loaded
                translationSource = TranslationSource.BUNDLED
                isLoading = false
            }
        }
    }

    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, onBackCompleted = { backStack.removeLastOrNull() })

    DocsPageRouteScreen(
        pageId = pageId,
        content = content,
        isLoading = isLoading,
        translationSource = translationSource,
        isNonEnglish = locale != "en",
        isAiSupported = chirpy.isSupported,
        modelReadiness = chirpy.modelReadiness,
        showChirpy = chirpy.showSheet,
        chirpyState = chirpy.sessionState,
        onChirpyToggle = chirpy.onToggle,
        onChirpyDismiss = chirpy.onDismiss,
        onChirpyDraftChange = chirpy.onDraftChange,
        onChirpySubmit = chirpy.onSubmit,
        onChirpyNavigateToPage = chirpy.onNavigateToPage,
        onBack = { backStack.removeLastOrNull() },
        onNavigateToPage = { targetPageId -> backStack.add(SettingsRoute.HelpDocPage(targetPageId)) },
    )
}
