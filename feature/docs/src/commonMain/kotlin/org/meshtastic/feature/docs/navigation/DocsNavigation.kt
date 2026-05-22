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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.currentLocaleCode
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.chirpy_error_busy
import org.meshtastic.core.resources.chirpy_error_model_unavailable
import org.meshtastic.core.resources.chirpy_error_token_budget_exceeded
import org.meshtastic.core.resources.chirpy_error_unknown
import org.meshtastic.core.resources.chirpy_error_unsupported_flavor
import org.meshtastic.core.resources.chirpy_error_unsupported_platform
import org.meshtastic.core.resources.chirpy_suggested_pages_help
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.ai.ChirpySessionHolder
import org.meshtastic.feature.docs.data.DefaultDocBundleLoader
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.DocsAiError
import org.meshtastic.feature.docs.model.ModelReadiness
import org.meshtastic.feature.docs.model.SourceRef
import org.meshtastic.feature.docs.model.TranslationSource
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.TranslationResult
import org.meshtastic.feature.docs.ui.DocsBrowserScreen
import org.meshtastic.feature.docs.ui.DocsPageRouteScreen
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.meshtastic.core.resources.Res as CoreRes

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

    // Auto-introduce Chirpy when the sheet first opens.
    AutoIntroduceChirpy(
        showSheet = holder.showSheet,
        sessionState = holder.sessionState,
        aiAssistant = aiAssistant,
        onUpdateSessionState = { holder.sessionState = it },
    )

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
                var assistantMsgId: String? = null

                fun updateAssistant(text: String, pages: List<DocPage>, role: ChirpyRole = ChirpyRole.ASSISTANT) {
                    val msgId = assistantMsgId ?: Uuid.random().toString().also { assistantMsgId = it }
                    val msg =
                        ChirpyMessage(
                            id = msgId,
                            role = role,
                            text = text,
                            sources = pages.map { SourceRef(id = it.id, title = it.title) },
                        )
                    val currentList = holder.sessionState.messages
                    val newList =
                        if (currentList.any { it.id == msgId }) {
                            currentList.map { if (it.id == msgId) msg else it }
                        } else {
                            currentList + msg
                        }
                    holder.sessionState = holder.sessionState.copy(messages = newList)
                }

                aiAssistant.answerStream(question, currentPageId = currentPageId).collect { result ->
                    when (result) {
                        is AIDocAssistantResult.Partial -> {
                            updateAssistant(result.answer, result.sourcePages)
                        }

                        is AIDocAssistantResult.Success -> {
                            updateAssistant(result.answer, result.sourcePages)
                            holder.sessionState = holder.sessionState.copy(isLoading = false)
                        }

                        is AIDocAssistantResult.Fallback -> {
                            val finalMsg = chirpyResultToMessage(result)
                            updateAssistant(finalMsg.text, result.suggestedPages, finalMsg.role)
                            holder.sessionState = holder.sessionState.copy(isLoading = false)
                        }

                        is AIDocAssistantResult.Error -> {
                            val finalMsg = chirpyResultToMessage(result)
                            updateAssistant(finalMsg.text, result.suggestedPages, finalMsg.role)
                            holder.sessionState = holder.sessionState.copy(isLoading = false)
                        }
                    }
                }
            }
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
        onSubmit = ::submit,
        onNavigateToPage = { pageId ->
            holder.showSheet = false
            backStack.add(SettingsRoute.HelpDocPage(pageId))
        },
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun AutoIntroduceChirpy(
    showSheet: Boolean,
    sessionState: org.meshtastic.feature.docs.model.AIDocAssistantSessionState,
    aiAssistant: AIDocAssistant,
    onUpdateSessionState: (org.meshtastic.feature.docs.model.AIDocAssistantSessionState) -> Unit,
) {
    val currentOnUpdateSessionState by androidx.compose.runtime.rememberUpdatedState(onUpdateSessionState)
    val currentSessionState by androidx.compose.runtime.rememberUpdatedState(sessionState)

    val modelStatus by aiAssistant.modelStatus.collectAsState()

    LaunchedEffect(showSheet, modelStatus) {
        if (
            showSheet &&
            modelStatus is ModelReadiness.Available &&
            currentSessionState.messages.isEmpty() &&
            !currentSessionState.isLoading
        ) {
            aiAssistant.resetSession()
            currentOnUpdateSessionState(currentSessionState.copy(isLoading = true))
            val result = aiAssistant.answer(CHIRPY_INTRO_PROMPT, currentPageId = null)
            val introMsg = chirpyResultToMessage(result)
            currentOnUpdateSessionState(
                currentSessionState.copy(messages = currentSessionState.messages + introMsg, isLoading = false),
            )
        }
    }
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

// ── Constants & helpers ─────────────────────────────────────────────────────────

/** Short intro prompt — kept minimal to skip heavy context ranking and generate in <1s. */
private const val CHIRPY_INTRO_PROMPT =
    "Say hi in 1-2 sentences. State your name is Chirpy and you help with Meshtastic. " +
        "Do not give the user a nickname. Be punchy and fun."

/** Maps an [AIDocAssistantResult] to a [ChirpyMessage]. */
@OptIn(ExperimentalUuidApi::class)
private suspend fun chirpyResultToMessage(result: AIDocAssistantResult): ChirpyMessage = when (result) {
    is AIDocAssistantResult.Partial ->
        ChirpyMessage(
            id = Uuid.random().toString(),
            role = ChirpyRole.ASSISTANT,
            text = result.answer,
            sources = result.sourcePages.map { SourceRef(id = it.id, title = it.title) },
        )

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

    is AIDocAssistantResult.Error -> {
        val errorMessage =
            when (result.reason) {
                DocsAiError.UnsupportedPlatform -> getString(CoreRes.string.chirpy_error_unsupported_platform)
                DocsAiError.UnsupportedFlavor -> getString(CoreRes.string.chirpy_error_unsupported_flavor)
                DocsAiError.ModelUnavailable -> getString(CoreRes.string.chirpy_error_model_unavailable)
                DocsAiError.Busy -> getString(CoreRes.string.chirpy_error_busy)
                DocsAiError.TokenBudgetExceeded -> getString(CoreRes.string.chirpy_error_token_budget_exceeded)
                DocsAiError.Unknown -> getString(CoreRes.string.chirpy_error_unknown)
            }
        val text =
            if (result.suggestedPages.isNotEmpty()) {
                "$errorMessage ${getString(CoreRes.string.chirpy_suggested_pages_help)}"
            } else {
                errorMessage
            }
        ChirpyMessage(
            id = Uuid.random().toString(),
            role = ChirpyRole.SYSTEM,
            text = text,
            sources = result.suggestedPages.map { SourceRef(id = it.id, title = it.title) },
        )
    }
}
