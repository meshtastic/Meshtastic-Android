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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.ModelReadiness

/** Keyword-search-only fallback AI assistant implementation. Used on Desktop, iOS, and Android fdroid flavor. */
@Single(binds = [])
class KeywordFallbackAssistant(private val searchEngine: KeywordSearchEngine) : AIDocAssistant {

    override val modelStatus: StateFlow<ModelReadiness> = MutableStateFlow(ModelReadiness.Unavailable(null))

    override suspend fun isSupported(): Boolean = false

    override suspend fun answer(question: String, currentPageId: String?): AIDocAssistantResult {
        val pages = searchEngine.selectForTokenBudget(question, maxChars = 20_000)
        return if (pages.isNotEmpty()) {
            AIDocAssistantResult.Fallback(
                message = "AI assistant is not available on this platform. Here are pages that may help:",
                suggestedPages = pages,
            )
        } else {
            AIDocAssistantResult.Error(reason = org.meshtastic.feature.docs.model.DocsAiError.UnsupportedPlatform)
        }
    }

    override fun answerStream(
        question: String,
        currentPageId: String?,
    ): kotlinx.coroutines.flow.Flow<AIDocAssistantResult> =
        kotlinx.coroutines.flow.flow { emit(answer(question, currentPageId)) }

    override fun resetSession() {
        /* No-op for keyword fallback */
    }
}
