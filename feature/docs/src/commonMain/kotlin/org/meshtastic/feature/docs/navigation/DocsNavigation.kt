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
import org.meshtastic.core.common.util.currentLocaleCode
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.ai.ChirpySessionHolder
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.SourceRef
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.TranslationResult
import org.meshtastic.feature.docs.ui.DocsBrowserScreen
import org.meshtastic.feature.docs.ui.DocsPageRouteScreen
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Registers docs navigation entries into the Settings navigation graph. */
@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3AdaptiveApi::class)
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
    val showFab: Boolean,
    val showSheet: Boolean,
    val sessionState: org.meshtastic.feature.docs.model.AIDocAssistantSessionState,
    val onToggle: () -> Unit,
    val onDismiss: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onNavigateToPage: (String) -> Unit,
)

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun rememberChirpyState(
    backStack: NavBackStack<NavKey>,
    currentPageId: String?,
    showFab: Boolean,
): ChirpyUiState {
    val aiAssistant = koinInject<AIDocAssistant>()
    val holder = koinInject<ChirpySessionHolder>()
    val scope = rememberCoroutineScope()

    var isSupported by remember { mutableStateOf(false) }

    // Poll for AI availability.
    LaunchedEffect(Unit) {
        repeat(AI_SUPPORT_CHECK_RETRIES) {
            isSupported = aiAssistant.isSupported()
            if (isSupported) return@LaunchedEffect
            kotlinx.coroutines.delay(AI_SUPPORT_CHECK_INTERVAL_MS)
        }
    }

    // Auto-introduce Chirpy when the sheet first opens.
    LaunchedEffect(holder.showSheet) {
        if (holder.showSheet && holder.sessionState.messages.isEmpty() && !holder.sessionState.isLoading) {
            holder.sessionState = holder.sessionState.copy(isLoading = true)
            val result = aiAssistant.answer(CHIRPY_INTRO_PROMPT, currentPageId = currentPageId)
            val introMsg = chirpyResultToMessage(result)
            holder.sessionState =
                holder.sessionState.copy(messages = holder.sessionState.messages + introMsg, isLoading = false)
        }
    }

    fun submit() {
        val question = holder.sessionState.draftQuestion.trim()
        if (question.isNotBlank() && !holder.sessionState.isLoading) {
            val userMsg = ChirpyMessage(id = Uuid.random().toString(), role = ChirpyRole.USER, text = question)
            holder.sessionState =
                holder.sessionState.copy(
                    messages = holder.sessionState.messages + userMsg,
                    draftQuestion = "",
                    isLoading = true,
                )
            scope.launch {
                val result = aiAssistant.answer(question, currentPageId = currentPageId)
                val responseMsg = chirpyResultToMessage(result)
                holder.sessionState =
                    holder.sessionState.copy(messages = holder.sessionState.messages + responseMsg, isLoading = false)
            }
        }
    }

    return ChirpyUiState(
        isSupported = isSupported,
        showFab = showFab,
        showSheet = holder.showSheet,
        sessionState = holder.sessionState,
        onToggle = { holder.showSheet = !holder.showSheet },
        onDismiss = { holder.showSheet = false },
        onDraftChange = { holder.sessionState = holder.sessionState.copy(draftQuestion = it) },
        onSubmit = ::submit,
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

@Composable
private fun DocsPageScreen(pageId: String, backStack: NavBackStack<NavKey>, chirpy: ChirpyUiState) {
    val bundleLoader = koinInject<DocBundleLoader>()
    val translationService = koinInject<DocTranslationService>()

    var content by remember { mutableStateOf<DocPageContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var translationSource by remember { mutableStateOf<TranslationSource>(TranslationSource.BUNDLED) }

    LaunchedEffect(pageId) {
        isLoading = true
        val loaded = bundleLoader.readPage(pageId)
        if (loaded != null && currentLocaleCode() != "en") {
            // CMP may have already resolved a Crowdin translation (locale-qualified resource).
            // Attempt ML Kit translation as fallback — if Crowdin translation was served,
            // it's already in the loaded content. ML Kit only runs if we're on English source.
            val result = translationService.translatePage(pageId, loaded.markdown ?: "", currentLocaleCode())
            when (result) {
                is TranslationResult.Success -> {
                    content = loaded.copy(markdown = result.translatedMarkdown)
                    translationSource = TranslationSource.ML_KIT
                }

                else -> {
                    content = loaded
                    translationSource = TranslationSource.BUNDLED
                }
            }
        } else {
            content = loaded
        }
        isLoading = false
    }

    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, onBackCompleted = { backStack.removeLastOrNull() })

    DocsPageRouteScreen(
        pageId = pageId,
        content = content,
        isLoading = isLoading,
        isAiSupported = chirpy.isSupported,
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

// ── Constants & helpers ─────────────────────────────────────────────────────────

/** Indicates the source of the displayed page content. */
private enum class TranslationSource {
    BUNDLED,
    ML_KIT,
}

/** How often to re-check AI model availability while waiting for download. */
private const val AI_SUPPORT_CHECK_INTERVAL_MS = 3_000L

/** Maximum number of AI support checks before giving up. */
private const val AI_SUPPORT_CHECK_RETRIES = 15

/** Prompt sent automatically when the Chirpy sheet opens to generate a natural introduction. */
private const val CHIRPY_INTRO_PROMPT = "Introduce yourself briefly. Who are you and what can you help with?"

/** Maps an [AIDocAssistantResult] to a [ChirpyMessage]. */
@OptIn(ExperimentalUuidApi::class)
private fun chirpyResultToMessage(result: AIDocAssistantResult): ChirpyMessage = when (result) {
    is AIDocAssistantResult.Success ->
        ChirpyMessage(
            id = Uuid.random().toString(),
            role = ChirpyRole.ASSISTANT,
            text = result.answer,
            sources = result.sourcePages.map { SourceRef(id = it.id, title = it.title) },
        )

    is AIDocAssistantResult.Fallback ->
        ChirpyMessage(
            id = Uuid.random().toString(),
            role = ChirpyRole.ASSISTANT,
            text = result.message,
            sources = result.suggestedPages.map { SourceRef(id = it.id, title = it.title) },
        )

    is AIDocAssistantResult.Error ->
        ChirpyMessage(
            id = Uuid.random().toString(),
            role = ChirpyRole.SYSTEM,
            text = "Sorry, I couldn't answer that. ${result.reason}",
        )
}
