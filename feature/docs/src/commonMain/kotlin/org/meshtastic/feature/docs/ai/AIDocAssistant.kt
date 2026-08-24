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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.ModelReadiness

/**
 * Shared abstraction over the platform-specific docs AI assistant.
 *
 * Bindings:
 * - Android `google` flavor: Gemini Nano implementation
 * - Android `fdroid` flavor: keyword-search fallback
 * - Desktop/iOS: keyword-search fallback
 */
interface AIDocAssistant {
    /** Whether the AI assistant is available on the current platform/device. */
    suspend fun isSupported(): Boolean

    /** Current model readiness state for lifecycle/download UX. */
    val modelStatus: StateFlow<ModelReadiness>

    /** Answer a user question about Meshtastic using bundled documentation context. */
    suspend fun answer(question: String, currentPageId: String? = null): AIDocAssistantResult

    /** Answer a user question about Meshtastic, streaming the results as they arrive. */
    fun answerStream(
        question: String,
        currentPageId: String? = null,
    ): kotlinx.coroutines.flow.Flow<AIDocAssistantResult>

    /** Reset the conversation session. Call when starting a new conversation thread. */
    fun resetSession()
}
