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
package org.meshtastic.app.ai

import co.touchlab.kermit.Logger
import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.DocsAiError

/**
 * Gemini Nano on-device AI assistant for the Google flavor.
 *
 * Uses Firebase AI Logic with [InferenceMode.ONLY_ON_DEVICE] for privacy — no data leaves the device. Falls back to
 * keyword search results when the on-device model is unavailable.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiNanoDocAssistant(private val searchEngine: KeywordSearchEngine, private val bundleLoader: DocBundleLoader) :
    AIDocAssistant {

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.0-flash-lite",
                onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE),
            )
    }

    override suspend fun isSupported(): Boolean = try {
        // Attempt a no-op to verify the model is reachable
        model.countTokens("test")
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun answer(question: String): AIDocAssistantResult = try {
        val contextPages = searchEngine.selectForTokenBudget(question, maxChars = MAX_CONTEXT_CHARS)
        val contextSnippets =
            contextPages.mapNotNull { page ->
                bundleLoader.readPage(page.id)?.markdown?.take(MAX_SNIPPET_CHARS)?.let { snippet ->
                    "## ${page.title}\n$snippet"
                }
            }

        val prompt = buildPrompt(question, contextSnippets)
        val response = model.generateContent(prompt)
        val answer = response.text ?: "I wasn't able to generate a response."

        AIDocAssistantResult.Success(answer = answer, sourcePages = contextPages, usedOnDeviceModel = true)
    } catch (e: Exception) {
        Logger.w(tag = "GeminiNanoDocAssistant") { "On-device inference failed: ${e.message}" }
        val errorType =
            when {
                e.message?.contains("BUSY", ignoreCase = true) == true -> DocsAiError.Busy
                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true -> DocsAiError.ModelUnavailable
                else -> DocsAiError.Unknown
            }
        val fallbackPages = searchEngine.selectForTokenBudget(question, maxChars = MAX_CONTEXT_CHARS)
        AIDocAssistantResult.Error(reason = errorType, suggestedPages = fallbackPages)
    }

    private fun buildPrompt(question: String, contextSnippets: List<String>): String {
        val context =
            if (contextSnippets.isNotEmpty()) {
                contextSnippets.joinToString("\n\n")
            } else {
                "No specific documentation context available."
            }
        return """
            |You are Chirpy, a helpful assistant for the Meshtastic mesh networking app.
            |Answer the user's question using ONLY the documentation context below.
            |If the answer is not in the context, say so honestly.
            |Keep answers concise (under 200 words).
            |
            |--- Documentation Context ---
            |$context
            |
            |--- User Question ---
            |$question
        """
            .trimMargin()
    }

    companion object {
        /** Conservative char budget for doc context (~3000 tokens ≈ 12000 chars, leaving room for prompt). */
        private const val MAX_CONTEXT_CHARS = 10_000

        /** Max chars per individual doc snippet to keep context diverse. */
        private const val MAX_SNIPPET_CHARS = 3_000
    }
}
