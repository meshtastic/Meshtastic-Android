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
package org.meshtastic.feature.docs.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.resources.chirpy_error_busy
import org.meshtastic.core.resources.chirpy_error_model_unavailable
import org.meshtastic.core.resources.chirpy_error_token_budget_exceeded
import org.meshtastic.core.resources.chirpy_error_unknown
import org.meshtastic.core.resources.chirpy_error_unsupported_flavor
import org.meshtastic.core.resources.chirpy_error_unsupported_platform
import org.meshtastic.core.resources.chirpy_suggested_pages_help
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState
import org.meshtastic.feature.docs.model.ChirpyMessage
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocsAiError
import org.meshtastic.feature.docs.model.SourceRef
import kotlin.uuid.Uuid
import org.meshtastic.core.resources.Res as CoreRes

/**
 * Global Chirpy conversation state shared across all docs panes.
 *
 * Registered as a Koin singleton so both the list and detail entries access the same conversation. Uses Compose
 * snapshot state so reads trigger recomposition automatically.
 *
 * The holder also *runs* the requests, on its own scope. Callers must not launch them from a
 * `rememberCoroutineScope()`: on-device inference takes seconds, and the pane that submitted a question is routinely
 * disposed before the answer lands — the adaptive docs layout swaps the list pane for a detail pane as soon as the user
 * opens a page. A composition scope is cancelled at that point, so the answer would be dropped mid-stream and
 * [AIDocAssistantSessionState.isLoading] would stay `true` forever in this singleton, permanently blocking the next
 * question. The session outlives any one pane, so the scope that fills it has to as well.
 */
@Single
class ChirpySessionHolder(private val aiAssistant: AIDocAssistant, dispatchers: CoroutineDispatchers) {
    var showSheet by mutableStateOf(false)
    var sessionState by mutableStateOf(AIDocAssistantSessionState())

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    /** The in-flight request, if any. Kept so a new request supersedes an abandoned one instead of racing it. */
    private var request: Job? = null

    /**
     * Auto-introduction, shown the first time the sheet opens on a ready model. Safe to call repeatedly: it no-ops once
     * the conversation has started or while a request is already running.
     */
    fun introduce() {
        if (sessionState.messages.isNotEmpty() || sessionState.isLoading) return
        sessionState = sessionState.copy(isLoading = true)
        launchRequest {
            aiAssistant.resetSession()
            val introMsg = chirpyResultToMessage(aiAssistant.answer(CHIRPY_INTRO_PROMPT, currentPageId = null))
            sessionState = sessionState.copy(messages = sessionState.messages + introMsg, isLoading = false)
        }
    }

    /**
     * Submits the trimmed draft question and streams the answer into [sessionState]. No-ops on a blank draft or while a
     * request is already running.
     */
    fun submit(currentPageId: String?) {
        val question = sessionState.draftQuestion.trim()
        if (question.isBlank() || sessionState.isLoading) return

        val userMsg = ChirpyMessage(id = Uuid.random().toString(), role = ChirpyRole.USER, text = question)
        sessionState =
            sessionState.copy(messages = sessionState.messages + userMsg, draftQuestion = "", isLoading = true)

        launchRequest {
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
                val currentList = sessionState.messages
                val newList =
                    if (currentList.any { it.id == msgId }) {
                        currentList.map { if (it.id == msgId) msg else it }
                    } else {
                        currentList + msg
                    }
                sessionState = sessionState.copy(messages = newList)
            }

            aiAssistant.answerStream(question, currentPageId = currentPageId).collect { result ->
                when (result) {
                    is AIDocAssistantResult.Partial -> updateAssistant(result.answer, result.sourcePages)

                    is AIDocAssistantResult.Success -> {
                        updateAssistant(result.answer, result.sourcePages)
                        sessionState = sessionState.copy(isLoading = false)
                    }

                    is AIDocAssistantResult.Fallback -> {
                        val finalMsg = chirpyResultToMessage(result)
                        updateAssistant(finalMsg.text, result.suggestedPages, finalMsg.role)
                        sessionState = sessionState.copy(isLoading = false)
                    }

                    is AIDocAssistantResult.Error -> {
                        val finalMsg = chirpyResultToMessage(result)
                        updateAssistant(finalMsg.text, result.suggestedPages, finalMsg.role)
                        sessionState = sessionState.copy(isLoading = false)
                    }
                }
            }
        }
    }

    /**
     * Runs [block] as the one current request. Every exit path — success, a stream that ended without a terminal
     * result, a thrown failure, or cancellation — clears [AIDocAssistantSessionState.isLoading], because leaving it set
     * blocks [submit] and [introduce] for the rest of the process. Only the current request may clear it: a superseded
     * one must not unblock the composer on behalf of the request that replaced it.
     */
    private fun launchRequest(block: suspend () -> Unit) {
        request?.cancel()
        // LAZY so `request` is assigned before the body can observe it.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    safeCatching { block() }.onFailure { Logger.e(it) { "[Chirpy] request failed" } }
                } finally {
                    if (request === coroutineContext[Job] && sessionState.isLoading) {
                        sessionState = sessionState.copy(isLoading = false)
                    }
                }
            }
        request = job
        job.start()
    }
}

/** Short intro prompt — kept minimal to skip heavy context ranking and generate in <1s. */
private const val CHIRPY_INTRO_PROMPT =
    "Say hi in 1-2 sentences. State your name is Chirpy and you help with Meshtastic. " +
        "Do not give the user a nickname. Be punchy and fun."

/** Maps an [AIDocAssistantResult] to a [ChirpyMessage]. */
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
