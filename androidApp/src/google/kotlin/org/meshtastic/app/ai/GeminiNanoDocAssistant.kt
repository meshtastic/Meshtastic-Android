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
import com.google.firebase.ai.DownloadStatus
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocsAiError

/**
 * Gemini on-device AI assistant for the Google flavor.
 *
 * Uses Firebase AI Logic hybrid SDK with [InferenceMode.PREFER_ON_DEVICE] — runs inference on-device via AICore when
 * available, falls back to cloud otherwise. Supported on Pixel 9+, Samsung Galaxy S25/S26, OnePlus 13/15, and other
 * devices with AICore.
 *
 * Context strategy: extracts only the **most relevant paragraphs** from each page (those containing query terms),
 * strips markdown formatting to maximize information density, and fits within the on-device token budget (~4K tokens).
 * This ensures fast, offline-capable answers even without cloud fallback.
 *
 * @see <a href="https://firebase.google.com/docs/ai-logic/hybrid/android/get-started">Firebase AI Logic Hybrid</a>
 */
@OptIn(PublicPreviewAPI::class)
class GeminiNanoDocAssistant(private val searchEngine: KeywordSearchEngine, private val bundleLoader: DocBundleLoader) :
    AIDocAssistant {

    /** On-device model for fast offline answers grounded in bundled docs. */
    private val onDeviceModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(SYSTEM_INSTRUCTION) },
                onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_ON_DEVICE),
            )
    }

    /** Cloud model with URL context — fetches only from meshtastic.org and github.com/meshtastic. */
    private val groundedModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(SYSTEM_INSTRUCTION) },
                tools = listOf(Tool.urlContext()),
            )
    }

    override suspend fun isSupported(): Boolean = try {
        // Always supported on Google flavor — cloud model with Google Search grounding is always available.
        // On-device model provides faster/offline answers when available; check status to trigger download.
        val ext = onDeviceModel.onDeviceExtension
        val status = ext?.checkStatus()
        Logger.d(tag = TAG) { "On-device model status: $status" }
        when (status) {
            OnDeviceModelStatus.AVAILABLE -> true

            OnDeviceModelStatus.DOWNLOADING -> true

            OnDeviceModelStatus.DOWNLOADABLE -> {
                Logger.i(tag = TAG) { "Model downloadable — requesting download" }
                ext.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted ->
                            Logger.d(tag = TAG) { "Download started: ${downloadStatus.bytesToDownload} bytes" }

                        is DownloadStatus.DownloadInProgress ->
                            Logger.d(tag = TAG) {
                                "Download progress: ${downloadStatus.totalBytesDownloaded} bytes"
                            }

                        is DownloadStatus.DownloadCompleted -> Logger.i(tag = TAG) { "Model download completed" }

                        is DownloadStatus.DownloadFailed ->
                            Logger.w(tag = TAG) { "Model download failed: $downloadStatus" }
                    }
                }
                true
            }

            else -> true // Cloud grounded model is always available even without on-device support.
        }
    } catch (e: Exception) {
        Logger.w(tag = TAG) { "isSupported() check failed, using cloud only: ${e.message}" }
        true // Cloud grounded model is always available.
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun answer(question: String, currentPageId: String?): AIDocAssistantResult = try {
        val bundle = bundleLoader.load()
        val queryTerms = extractQueryTerms(question)

        // Load all page content for full-text search ranking.
        val allContent = bundle.pages.associateWith { page -> bundleLoader.readPage(page.id)?.markdown.orEmpty() }

        // Rank pages by relevance: full-text content search + keyword/title matching.
        val rankedPages = rankPagesByRelevance(queryTerms, bundle.pages, allContent)
        Logger.d(tag = TAG) { "Ranked pages: ${rankedPages.take(5).map { "${it.first.id}(${it.second})" }}" }

        // Build compact context by extracting only relevant paragraphs.
        val contextResult = buildContext(currentPageId, queryTerms, rankedPages, allContent, MAX_CONTEXT_CHARS)
        Logger.d(tag = TAG) {
            "Context: ${contextResult.parts.size} pages, ${contextResult.totalChars} chars (budget $MAX_CONTEXT_CHARS)"
        }

        val prompt = buildPrompt(question, contextResult.parts)
        Logger.d(tag = TAG) { "Prompt: ${prompt.length} chars" }

        val answer = generateWithFallback(prompt, contextResult, currentPageId, queryTerms, rankedPages, allContent)
        Logger.d(tag = TAG) { "Response (${answer.length} chars): ${answer.take(200)}" }

        // Merge context pages with any pages mentioned by title in the response (à la Meshtastic-Apple).
        val mentionedPages =
            bundle.pages.filter { page ->
                page.id !in contextResult.usedPageIds && answer.contains(page.title, ignoreCase = true)
            }
        val allSourcePages =
            contextResult.usedPageIds.mapNotNull { id -> bundle.pages.find { it.id == id } } + mentionedPages

        AIDocAssistantResult.Success(answer = answer, sourcePages = allSourcePages, usedOnDeviceModel = true)
    } catch (e: Exception) {
        Logger.w(tag = TAG) { "Inference failed: ${e.message}" }
        val errorType =
            when {
                e.message?.contains("BUSY", ignoreCase = true) == true -> DocsAiError.Busy
                e.message?.contains("BATTERY", ignoreCase = true) == true -> DocsAiError.Busy
                e.message?.contains("BACKGROUND", ignoreCase = true) == true -> DocsAiError.Busy
                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true -> DocsAiError.ModelUnavailable
                else -> DocsAiError.Unknown
            }
        val fallbackPages = searchEngine.selectForTokenBudget(question, maxChars = MAX_CONTEXT_CHARS)
        AIDocAssistantResult.Error(reason = errorType, suggestedPages = fallbackPages)
    }

    /**
     * Attempts on-device inference first, then falls back to cloud with Google Search grounding if on-device fails or
     * returns a low-confidence "not in docs" response.
     */
    private suspend fun generateWithFallback(
        prompt: String,
        contextResult: ContextResult,
        currentPageId: String?,
        queryTerms: List<String>,
        rankedPages: List<Pair<DocPage, Int>>,
        allContent: Map<DocPage, String>,
    ): String {
        // Try on-device first (fast, offline, private).
        val onDeviceAnswer =
            try {
                val response = onDeviceModel.generateContent(prompt)
                response.text?.trimEnd()
            } catch (e: Exception) {
                Logger.d(tag = TAG) { "On-device inference failed, will try cloud: ${e.message}" }
                null
            }

        // If on-device gave a good answer, use it.
        if (onDeviceAnswer != null && !looksLikeNoAnswer(onDeviceAnswer)) {
            return onDeviceAnswer
        }

        // Fall back to cloud with Google Search grounding for broader knowledge.
        Logger.d(tag = TAG) { "Falling back to grounded cloud model" }
        return try {
            val groundedPrompt = prompt + MESHTASTIC_URL_HINT
            val response = groundedModel.generateContent(groundedPrompt)
            response.text?.trimEnd() ?: onDeviceAnswer ?: "I wasn't able to generate a response."
        } catch (e: Exception) {
            Logger.w(tag = TAG) { "Cloud grounded model also failed: ${e.message}" }
            // Last resort: retry on-device with reduced context.
            if (contextResult.parts.size > 1) {
                val reduced = buildContext(currentPageId, queryTerms, rankedPages, allContent, RETRY_CONTEXT_CHARS)
                val retryPrompt = buildPrompt(reduced.parts.firstOrNull().orEmpty(), reduced.parts)
                val retryResponse = onDeviceModel.generateContent(retryPrompt)
                retryResponse.text?.trimEnd() ?: "I wasn't able to generate a response."
            } else {
                onDeviceAnswer ?: throw e
            }
        }
    }

    /** Heuristic: detect when the model says it can't find the answer in the provided docs. */
    private fun looksLikeNoAnswer(answer: String): Boolean {
        val lower = answer.lowercase()
        return lower.contains("not in the docs") ||
            lower.contains("not found in") ||
            lower.contains("i don't have information") ||
            lower.contains("i couldn't find") ||
            lower.contains("not covered in the documentation")
    }

    private data class ContextResult(val parts: List<String>, val usedPageIds: Set<String>, val totalChars: Int)

    /** Builds context parts from ranked pages within the given char budget. */
    private fun buildContext(
        currentPageId: String?,
        queryTerms: List<String>,
        rankedPages: List<Pair<DocPage, Int>>,
        allContent: Map<DocPage, String>,
        budget: Int,
    ): ContextResult {
        val usedPageIds = mutableSetOf<String>()
        val contextParts = mutableListOf<String>()
        var totalChars = 0

        // Current page gets priority.
        if (currentPageId != null) {
            val content = allContent.entries.find { it.key.id == currentPageId }
            if (content != null && content.value.isNotBlank()) {
                val pageBudget = MAX_PAGE_CHARS.coerceAtMost(budget)
                val extracted = extractRelevantContent(content.key.title, content.value, queryTerms, pageBudget)
                if (extracted.isNotBlank()) {
                    contextParts.add(extracted)
                    totalChars += extracted.length
                    usedPageIds.add(currentPageId)
                }
            }
        }

        // Add relevant paragraphs from top-ranked pages within budget.
        for ((page, _) in rankedPages) {
            if (page.id in usedPageIds) continue
            if (totalChars >= budget) break
            val pageContent = allContent[page] ?: continue
            if (pageContent.isBlank()) continue

            val snippetBudget = (budget - totalChars).coerceAtMost(MAX_SNIPPET_CHARS)
            if (snippetBudget < MIN_USEFUL_SNIPPET) break

            val extracted = extractRelevantContent(page.title, pageContent, queryTerms, snippetBudget)
            if (extracted.isNotBlank()) {
                contextParts.add(extracted)
                totalChars += extracted.length
                usedPageIds.add(page.id)
            }
        }

        return ContextResult(parts = contextParts, usedPageIds = usedPageIds, totalChars = totalChars)
    }

    /** Extracts query terms from a question, filtering short/stop words. */
    private fun extractQueryTerms(question: String): List<String> = question
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .distinct()

    /**
     * Extracts the most relevant content from a page: the paragraphs that contain query terms, with markdown formatting
     * stripped for maximum information density.
     */
    private fun extractRelevantContent(
        title: String,
        markdown: String,
        queryTerms: List<String>,
        maxChars: Int,
    ): String {
        val plainText = stripMarkdown(markdown)

        // Split into paragraphs (double newline or section breaks).
        val paragraphs = plainText.split(Regex("\n{2,}")).map { it.trim() }.filter { it.length >= MIN_PARAGRAPH_LEN }

        // Score each paragraph by how many query terms it contains.
        val scored =
            paragraphs.map { paragraph ->
                val lower = paragraph.lowercase()
                val hits = queryTerms.count { term -> lower.contains(term) }
                paragraph to hits
            }

        // Take paragraphs with hits first (sorted by hits desc), then fill with top paragraphs for context.
        val withHits = scored.filter { it.second > 0 }.sortedByDescending { it.second }
        val withoutHits = scored.filter { it.second == 0 }

        val result = StringBuilder("$title: ")
        for ((paragraph, _) in withHits + withoutHits) {
            if (result.length + paragraph.length + 1 > maxChars) {
                // Try to fit a truncated version if we have room.
                val remaining = maxChars - result.length - 1
                if (remaining > MIN_USEFUL_SNIPPET) {
                    result.append(paragraph.take(remaining))
                }
                break
            }
            result.append(paragraph).append('\n')
        }
        return result.toString().trim()
    }

    /** Strips markdown formatting to produce dense plain text. */
    private fun stripMarkdown(markdown: String): String = markdown
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "") // headers
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1") // links → text
        .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1") // images → alt
        .replace(Regex("[*_]{1,3}([^*_]+)[*_]{1,3}"), "$1") // bold/italic
        .replace(Regex("`{1,3}[^`]*`{1,3}"), "") // inline code
        .replace(Regex("^[>|\\-*+]\\s?", RegexOption.MULTILINE), "") // block quotes, lists
        .replace(Regex("\\|"), " ") // table pipes
        .replace(Regex("-{3,}"), "") // horizontal rules
        .replace(Regex(" {2,}"), " ") // collapse whitespace
        .trim()

    /**
     * Ranks pages by relevance using full-text content search + keyword/title matching. Returns pages sorted by score
     * descending, filtering out zero-score pages.
     */
    private fun rankPagesByRelevance(
        queryTerms: List<String>,
        pages: List<DocPage>,
        allContent: Map<DocPage, String>,
    ): List<Pair<DocPage, Int>> = pages
        .map { page ->
            var score = 0
            val content = allContent[page]?.lowercase().orEmpty()

            for (term in queryTerms) {
                if (content.contains(term)) score += CONTENT_MATCH_SCORE
                if (page.title.lowercase().contains(term)) score += TITLE_MATCH_SCORE
                if (page.keywords.any { it.lowercase().contains(term) }) score += KEYWORD_MATCH_SCORE
                if (page.aliases.any { it.lowercase().contains(term) }) score += ALIAS_MATCH_SCORE
            }

            page to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

    private fun buildPrompt(question: String, contextParts: List<String>): String {
        val context =
            if (contextParts.isNotEmpty()) {
                contextParts.joinToString("\n\n")
            } else {
                FALLBACK_CONTEXT
            }
        return """
            |Bundled app documentation:
            |$context
            |
            |User question: $question
        """
            .trimMargin()
    }

    companion object {
        private const val TAG = "ChirpyAI"

        /** Gemini 3.1 Flash-Lite — latest stable model (2026-05-07), free tier, supports grounding. */
        private const val MODEL_NAME = "gemini-3.1-flash-lite"

        private const val SYSTEM_INSTRUCTION =
            """You are Chirpy, the friendly AI assistant built into the Meshtastic Android app. You help users understand mesh networking, configure their Meshtastic nodes, troubleshoot connectivity issues, and get the most out of the Meshtastic ecosystem.

Personality: Helpful, concise, enthusiastic about mesh networking. Use short paragraphs. Include relevant emoji sparingly (📡 🔋 📍).

Knowledge sources (in priority order):
1. Bundled app documentation provided as context below
2. Official Meshtastic documentation at meshtastic.org/docs
3. Official Meshtastic GitHub repositories (github.com/meshtastic)
4. General LoRa/mesh networking knowledge

Guidelines:
- Answer the user's question directly and helpfully
- When the bundled docs cover the topic, cite them
- When the bundled docs don't cover it, use your knowledge of official Meshtastic sources — don't refuse to help
- Only reference official Meshtastic sources (meshtastic.org, github.com/meshtastic) — never cite random forums, blogs, or third-party sites
- For firmware-specific or hardware-specific questions beyond app scope, point users to meshtastic.org/docs
- Keep answers concise (2-4 short paragraphs max) unless the user asks for detail
- If you're truly unsure about something Meshtastic-specific, say so honestly rather than guessing"""

        /** Total context char budget — sized for on-device Nano (~4K tokens ≈ 10K chars for context + prompt). */
        private const val MAX_CONTEXT_CHARS = 8_000

        /** Reduced context budget for retry after on-device context overflow. */
        private const val RETRY_CONTEXT_CHARS = 3_000

        /** Max chars for the current page (gets priority). */
        private const val MAX_PAGE_CHARS = 4_000

        /** Max chars per additional page snippet. */
        private const val MAX_SNIPPET_CHARS = 2_000

        /** Minimum useful snippet size — don't bother with tiny fragments. */
        private const val MIN_USEFUL_SNIPPET = 100

        /** Minimum paragraph length to consider. */
        private const val MIN_PARAGRAPH_LEN = 20

        // Scoring weights for page ranking
        private const val CONTENT_MATCH_SCORE = 3
        private const val TITLE_MATCH_SCORE = 10
        private const val KEYWORD_MATCH_SCORE = 7
        private const val ALIAS_MATCH_SCORE = 5

        private val STOP_WORDS =
            setOf(
                "the",
                "is",
                "at",
                "in",
                "on",
                "to",
                "of",
                "an",
                "it",
                "do",
                "me",
                "my",
                "or",
                "if",
                "be",
                "as",
                "by",
                "so",
                "we",
                "he",
                "up",
                "no",
                "am",
                "us",
            )

        private const val FALLBACK_CONTEXT =
            "Meshtastic is an open-source mesh networking platform for LoRa radios. " +
                "The app connects to Meshtastic devices via Bluetooth or WiFi to send messages, " +
                "share location, and manage mesh network settings like channels, nodes, and modules."

        /** URLs appended to prompts for the cloud model to leverage URL context tool. Only official sources. */
        private const val MESHTASTIC_URL_HINT =
            "\n\nFor additional context, you may reference these official sources:" +
                "\n- https://meshtastic.org/docs/" +
                "\n- https://github.com/meshtastic/Meshtastic-Android" +
                "\n- https://github.com/meshtastic/firmware"
    }
}
