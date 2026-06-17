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
package org.meshtastic.app.discovery

import co.touchlab.kermit.Logger
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.DiscoverySummaryGenerator
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider

/**
 * Google-flavor provider that uses Gemini Nano via the ML Kit GenAI Prompt API for on-device AI summaries.
 *
 * Lives in the Google flavor source set (not the shared `:feature:discovery` module) so the proprietary ML Kit GenAI
 * dependency never reaches the F-Droid build. The F-Droid flavor binds
 * [org.meshtastic.feature.discovery.ai. AlgorithmicSummaryProvider] instead.
 *
 * Falls back to [DiscoverySummaryGenerator] when:
 * - The on-device model is unavailable (unsupported hardware or not downloaded)
 * - Generation fails for any reason
 */
class GeminiNanoSummaryProvider(private val generator: DiscoverySummaryGenerator) : DiscoverySummaryAiProvider {

    private val log = Logger.withTag("GeminiNanoSummary")

    private val generativeModel: GenerativeModel? by lazy {
        @Suppress("TooGenericExceptionCaught") // ML Kit throws undocumented RuntimeExceptions
        try {
            Generation.getClient()
        } catch (e: Exception) {
            log.w(e) { "Failed to get GenerativeModel client" }
            null
        }
    }

    override val isAvailable: Boolean
        get() = checkAvailability()

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String {
        val model = generativeModel
        if (model == null || !isAvailable) {
            log.d { "Gemini Nano unavailable, using algorithmic fallback" }
            return generator.generateSessionSummary(session, presetResults)
        }

        val prompt = generator.buildSessionPrompt(session, presetResults)
        return generateOrFallback(model, prompt) { generator.generateSessionSummary(session, presetResults) }
    }

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String {
        val model = generativeModel
        if (model == null || !isAvailable) {
            return generator.generatePresetSummary(result)
        }

        val prompt = generator.buildPresetPrompt(result)
        return generateOrFallback(model, prompt) { generator.generatePresetSummary(result) }
    }

    private suspend fun generateOrFallback(model: GenerativeModel, prompt: String, fallback: () -> String): String =
        try {
            val request =
                generateContentRequest(TextPart(prompt)) {
                    temperature = TEMPERATURE
                    topK = TOP_K
                    maxOutputTokens = MAX_OUTPUT_TOKENS
                }
            val response = model.generateContent(request)
            val text = response.candidates.firstOrNull()?.text
            if (text.isNullOrBlank()) {
                log.w { "Gemini Nano returned empty response, using fallback" }
                fallback()
            } else {
                text
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.w(e) { "Gemini Nano generation failed, using fallback" }
            fallback()
        }

    private fun checkAvailability(): Boolean = try {
        // FeatureStatus is an IntDef — check synchronously via the lazy model field.
        // Note: checkStatus() is suspend in the API; we use a non-suspend heuristic here
        // by catching and falling back if unavailable. The actual availability is confirmed
        // in generateOrFallback when the suspend call succeeds.
        generativeModel != null
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val TEMPERATURE = 0.3f
        const val TOP_K = 16
        const val MAX_OUTPUT_TOKENS = 200
    }
}
