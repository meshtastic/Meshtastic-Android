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
import com.google.firebase.ai.OnDeviceModelOption
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocsAiError
import org.meshtastic.feature.docs.model.ModelReadiness

/**
 * Gemini on-device AI assistant for the Google flavor.
 *
 * Runs entirely on-device using the Firebase AI Logic SDK with [InferenceMode.ONLY_ON_DEVICE].
 *
 * Context strategy: extracts only the **most relevant paragraphs** from each page (those containing query terms),
 * strips markdown formatting to maximize information density, and fits within the on-device token budget.
 *
 * Multi-turn history is supported locally by formatting the past conversation turns directly into the prompt.
 *
 * @see <a href="https://firebase.google.com/docs/ai-logic/hybrid/android/get-started">Firebase AI Logic Hybrid</a>
 */
@OptIn(PublicPreviewAPI::class)
class GeminiNanoDocAssistant(
    private val searchEngine: KeywordSearchEngine,
    private val bundleLoader: DocBundleLoader,
    private val nodeRepository: NodeRepository,
) : AIDocAssistant {

    /** On-device model for fast, local answers grounded in bundled docs. */
    private val onDeviceModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content { text(SYSTEM_INSTRUCTION) },
                onDeviceConfig =
                OnDeviceConfig(
                    mode = InferenceMode.ONLY_ON_DEVICE,
                    modelOption = OnDeviceModelOption.STABLE,
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    temperature = TEMPERATURE,
                    topK = TOP_K,
                ),
            )
    }

    private val _modelStatus = MutableStateFlow<ModelReadiness>(ModelReadiness.Checking)

    /** Exposes model download/readiness state for UI consumption. */
    override val modelStatus: StateFlow<ModelReadiness> = _modelStatus.asStateFlow()

    /** Conversation history stored as list of (Question, Answer) pairs. */
    private val history = mutableListOf<Pair<String, String>>()

    override suspend fun isSupported(): Boolean = try {
        val ext = onDeviceModel.onDeviceExtension
        if (ext == null) {
            _modelStatus.value = ModelReadiness.Unavailable("On-device extension not available")
            return false
        }
        _modelStatus.value = ModelReadiness.Checking
        val status = ext.checkStatus()
        Logger.d(tag = TAG) { "On-device model status: $status" }
        when (status) {
            OnDeviceModelStatus.AVAILABLE -> {
                warmUp(ext)
                _modelStatus.value = ModelReadiness.Available
                true
            }

            OnDeviceModelStatus.DOWNLOADING -> {
                _modelStatus.value = ModelReadiness.Downloading(bytesDownloaded = 0L, totalBytes = 0L)
                false
            }

            OnDeviceModelStatus.DOWNLOADABLE -> {
                Logger.i(tag = TAG) { "Model downloadable — requesting download" }
                var downloadCompleted = false
                var totalSize = 0L
                ext.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> {
                            totalSize = downloadStatus.bytesToDownload
                            _modelStatus.value = ModelReadiness.Downloading(0L, totalSize)
                            Logger.d(tag = TAG) { "Download started: $totalSize bytes" }
                        }

                        is DownloadStatus.DownloadInProgress -> {
                            _modelStatus.value =
                                ModelReadiness.Downloading(downloadStatus.totalBytesDownloaded, totalSize)
                            Logger.d(tag = TAG) {
                                "Download progress: ${downloadStatus.totalBytesDownloaded}/$totalSize"
                            }
                        }

                        is DownloadStatus.DownloadCompleted -> {
                            Logger.i(tag = TAG) { "Model download completed" }
                            warmUp(ext)
                            _modelStatus.value = ModelReadiness.Available
                            downloadCompleted = true
                        }

                        is DownloadStatus.DownloadFailed -> {
                            _modelStatus.value = ModelReadiness.Unavailable("Download failed")
                            Logger.w(tag = TAG) { "Model download failed: $downloadStatus" }
                        }
                    }
                }
                downloadCompleted
            }

            else -> {
                _modelStatus.value = ModelReadiness.Unavailable("Model unavailable on this device")
                false
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Logger.w(tag = TAG) { "isSupported() check failed: ${e.message}" }
        _modelStatus.value = ModelReadiness.Unavailable(e.message)
        false
    }

    private suspend fun warmUp(ext: com.google.firebase.ai.OnDeviceExtension) {
        try {
            ext.warmUp()
            Logger.i(tag = TAG) { "Model warmed up successfully" }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Logger.w(tag = TAG) { "Warmup failed (non-fatal): ${e.message}" }
        }
    }

    override suspend fun answer(question: String, currentPageId: String?): AIDocAssistantResult =
        answerStream(question, currentPageId).first { it !is AIDocAssistantResult.Partial }

    override fun answerStream(
        question: String,
        currentPageId: String?,
    ): kotlinx.coroutines.flow.Flow<AIDocAssistantResult> = kotlinx.coroutines.flow.flow {
        // Fast path: short prompts with no page context skip expensive doc loading.
        val isLightweight =
            question.length < MAX_LIGHTWEIGHT_PROMPT_LEN && currentPageId == null && history.isEmpty()

        val prompt: String
        val contextPages: List<DocPage>

        if (isLightweight) {
            prompt = "You are Chirpy, the Meshtastic mesh networking assistant mascot. $question"
            contextPages = emptyList()
            Logger.d(tag = TAG) { "Lightweight prompt (no context): ${question.length} chars" }
        } else {
            val bundle = bundleLoader.load()
            val queryTerms = extractQueryTerms(question)

            // Load all page content for full-text search ranking.
            val allContent =
                bundle.pages.associateWith { page -> bundleLoader.readPage(page.id)?.markdown.orEmpty() }

            // Rank pages by relevance: full-text content search + keyword/title matching.
            val rankedPages = rankPagesByRelevance(queryTerms, bundle.pages, allContent)
            Logger.d(tag = TAG) { "Ranked pages: ${rankedPages.take(5).map { "${it.first.id}(${it.second})" }}" }

            // Build compact context by extracting only relevant paragraphs.
            val contextResult = buildContext(currentPageId, queryTerms, rankedPages, allContent, MAX_CONTEXT_CHARS)
            Logger.d(tag = TAG) {
                "Context: ${contextResult.parts.size} pages, ${contextResult.totalChars} chars (budget $MAX_CONTEXT_CHARS)"
            }

            prompt = buildPrompt(question, contextResult.parts)
            contextPages = contextResult.usedPageIds.mapNotNull { id -> bundle.pages.find { it.id == id } }
            Logger.d(tag = TAG) { "Prompt: ${prompt.length} chars, history count: ${history.size}" }
        }

        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                Logger.i(tag = TAG) {
                    "Retrying inference in ${backoffMs}ms (attempt ${attempt + 1}/${MAX_RETRIES + 1})"
                }
                delay(backoffMs)
            }
            try {
                val accumulatedText = StringBuilder()
                var lastEmitTime = 0L
                onDeviceModel.generateContentStream(prompt).collect { chunk ->
                    val text = chunk.text
                    if (!text.isNullOrEmpty()) {
                        accumulatedText.append(text)
                        val now = System.nanoTime()
                        val elapsedMs = (now - lastEmitTime) / 1_000_000
                        if (elapsedMs >= STREAM_THROTTLE_MS) {
                            lastEmitTime = now
                            emit(
                                AIDocAssistantResult.Partial(
                                    answer = cleanResponse(accumulatedText.toString()),
                                    sourcePages = contextPages,
                                    usedOnDeviceModel = true,
                                ),
                            )
                        }
                    }
                    // Log token usage from last chunk
                    chunk.usageMetadata?.let { meta ->
                        Logger.d(tag = TAG) {
                            "Tokens — prompt: ${meta.promptTokenCount}, response: ${meta.candidatesTokenCount}, total: ${meta.totalTokenCount}"
                        }
                    }
                }
                // Emit final partial to ensure UI has the complete text before Success
                emit(
                    AIDocAssistantResult.Partial(
                        answer = cleanResponse(accumulatedText.toString()),
                        sourcePages = contextPages,
                        usedOnDeviceModel = true,
                    ),
                )
                val onDeviceAnswer = cleanResponse(accumulatedText.toString().trimEnd())
                val allSourcePages = contextPages

                // Keep short history because on-device inference has a smaller request token budget.
                if (!isLightweight) {
                    history.add(question.take(MAX_HISTORY_CHARS) to onDeviceAnswer.take(MAX_HISTORY_CHARS))
                    if (history.size > MAX_HISTORY_TURNS) {
                        history.removeAt(0)
                    }
                }

                emit(
                    AIDocAssistantResult.Success(
                        answer = onDeviceAnswer,
                        sourcePages = allSourcePages,
                        usedOnDeviceModel = true,
                    ),
                )
                return@flow // Success — exit retry loop
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                val isBusy =
                    e.message?.contains("BUSY", ignoreCase = true) == true ||
                        e.message?.contains("BATTERY", ignoreCase = true) == true ||
                        e.message?.contains("BACKGROUND", ignoreCase = true) == true
                if (!isBusy || attempt >= MAX_RETRIES) {
                    Logger.w(tag = TAG) { "On-device inference failed: ${e.message}" }
                    val errorType =
                        when {
                            isBusy -> DocsAiError.Busy

                            e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ->
                                DocsAiError.ModelUnavailable

                            else -> DocsAiError.Unknown
                        }
                    val fallbackPages = searchEngine.selectForTokenBudget(question, maxChars = MAX_CONTEXT_CHARS)
                    emit(AIDocAssistantResult.Error(reason = errorType, suggestedPages = fallbackPages))
                    return@flow
                }
                Logger.i(tag = TAG) { "BUSY error, will retry (attempt ${attempt + 1})" }
            }
        }
    }

    override fun resetSession() {
        history.clear()
        Logger.d(tag = TAG) { "Chat session reset" }
    }

    /** Cleans on-device model response artifacts (markdown fences, excessive newlines). */
    private fun cleanResponse(text: String): String = text
        .removePrefix("```markdown\n")
        .removePrefix("```\n")
        .removeSuffix("\n```")
        .removeSuffix("```")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

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

        val myNode = nodeRepository.myNodeInfo.value
        val ourNode = nodeRepository.ourNodeInfo.value
        val nodeDb = nodeRepository.nodeDBbyNum.value

        val hardwareInfo =
            if (myNode != null) {
                """
            |- Connected Hardware: ${myNode.model ?: "Unknown model"} (Firmware: ${myNode.firmwareVersion ?: "Unknown"})
            |- Local Node Number: ${myNode.myNodeNum}
            |- GPS Capabilities: ${if (myNode.hasGPS) "Equipped/Active" else "No GPS"}
            |- WiFi Enabled: ${if (myNode.hasWifi) "Yes" else "No"}
            |- Channel Utilization: ${myNode.channelUtilization}%
            |- Airtime Tx: ${myNode.airUtilTx}%
            """
                    .trimIndent()
            } else {
                "- Connected Hardware: No local node connected right now (user should pair their radio over Bluetooth)."
            }

        val metricsInfo =
            if (ourNode != null) {
                val battery = ourNode.batteryLevel
                val batteryStr = if (battery != null && battery in 1..100) "$battery%" else "Unknown (External Power)"
                val voltage = ourNode.voltage
                val voltStr = if (voltage != null && voltage > 0f) "${voltage}V" else "Unknown"

                """
            |- Node Battery Status: $batteryStr ($voltStr)
            |- User Identity: ${ourNode.user.short_name} (${ourNode.user.long_name})
            |- Position: ${if (ourNode.isUnknownUser) "Unknown user" else "Valid user profile synced"}
            """
                    .trimIndent()
            } else {
                "- Node Status: Unknown user identity (no radio profile synced yet)"
            }

        val meshInfo =
            if (nodeDb.isNotEmpty()) {
                val totalNodes = nodeDb.size
                val onlineNodes = nodeDb.values.count { it.isOnline }
                """
            |- Mesh Network Size: $totalNodes nodes registered in local DB
            |- Active Peers Online: $onlineNodes nodes currently active on the air
            """
                    .trimIndent()
            } else {
                "- Mesh Network: Empty node database (offline or newly installed app)"
            }

        val liveStatusContext =
            """
            |Active Live Radio Node & Mesh Network Status:
            |$hardwareInfo
            |$metricsInfo
            |$meshInfo
        """
                .trimMargin()

        val historyStr =
            if (history.isNotEmpty()) {
                "Previous conversation history:\n" +
                    history.takeLast(MAX_HISTORY_TURNS).joinToString("\n") { (q, a) -> "User: $q\nAssistant: $a" } +
                    "\n\n"
            } else {
                ""
            }

        return """
            |System Instruction:
            |$SYSTEM_INSTRUCTION
            |
            |$liveStatusContext
            |
            |Bundled app documentation:
            |$context
            |
            |${historyStr}User question: $question
        """
            .trimMargin()
    }

    companion object {
        private const val TAG = "ChirpyAI"

        /** Cloud model identifier; with ONLY_ON_DEVICE mode, inference remains local. */
        private const val MODEL_NAME = "gemini-2.5-flash-lite"

        private const val SYSTEM_INSTRUCTION =
            """You are Chirpy, the friendly AI assistant and official Node mascot built into the Meshtastic Android app. You help users understand mesh networking, configure their Meshtastic nodes, troubleshoot connectivity issues, and get the most out of the Meshtastic ecosystem.

Personality: Helpful, concise, cheerful, and highly enthusiastic about mesh networking. You represent a cute, helpful little LoRa radio node! Use short paragraphs. Include relevant emoji (📡 🔋 📍 📱 📶 🔌). Always refer to yourself as Chirpy. Never call yourself Gemma or Gemini. Keep a light, highly energetic, and friendly tone, actively packing your responses with fun, clever radio, hardware, and mesh networking puns! Work them in naturally but frequently (e.g., "staying connected!", "fully charged and ready to assist!", "let's relay some knowledge!", "time to hop to it!", "we're totally on the same frequency!", "attenuation got you down?", "let's boost that signal!", "channeling my inner router!"). Balance your cheerful node humor with clear, technically accurate help first and foremost!

Knowledge sources (in priority order):
1. Bundled app documentation provided as context below
2. Your pre-trained knowledge of Meshtastic concepts (may not reflect latest changes)
3. General LoRa/mesh networking knowledge from training data

Guidelines:
- Answer the user's question directly and helpfully
- When the bundled docs cover the topic, cite them
- If the bundled docs don't cover a topic, use your training knowledge but note it may be outdated — suggest checking meshtastic.org/docs for the latest information
- Only reference official Meshtastic sources (meshtastic.org, github.com/meshtastic) — never cite random forums, blogs, or third-party sites
- For firmware-specific or hardware-specific questions beyond app scope, point users to meshtastic.org/docs
- Keep answers concise (2-4 short paragraphs max) unless the user asks for detail
- Never give the user a nickname or pet name — just address them naturally
- If you're truly unsure about something Meshtastic-specific, say so honestly rather than guessing"""

        /**
         * Total prompt context char budget. Keep this conservative because on-device requests are limited to about 4k
         * tokens.
         */
        private const val MAX_CONTEXT_CHARS = 9_000

        /** Max chars for the current page (gets priority). */
        private const val MAX_PAGE_CHARS = 4_000

        /** Max chars per additional page snippet. */
        private const val MAX_SNIPPET_CHARS = 1_500

        /** Keep only a short, bounded history for on-device prompt assembly. */
        private const val MAX_HISTORY_TURNS = 2
        private const val MAX_HISTORY_CHARS = 600

        /** Minimum useful snippet size — don't bother with tiny fragments. */
        private const val MIN_USEFUL_SNIPPET = 100

        /** Minimum paragraph length to consider. */
        private const val MIN_PARAGRAPH_LEN = 20

        /** Maximum output tokens for on-device generation. */
        private const val MAX_OUTPUT_TOKENS = 256

        /** Prompts shorter than this with no page context use the fast (no-doc-loading) path. */
        private const val MAX_LIGHTWEIGHT_PROMPT_LEN = 100

        /** Temperature for response generation (0.0 = deterministic, 1.0 = creative). */
        private const val TEMPERATURE = 0.7f

        /** Top-K for token selection. */
        private const val TOP_K = 40

        /** Max retry attempts for BUSY errors. */
        private const val MAX_RETRIES = 3

        /** Initial backoff delay in milliseconds (doubles each retry). */
        private const val INITIAL_BACKOFF_MS = 500L

        /** Minimum interval between partial stream emissions to avoid UI jank. */
        private const val STREAM_THROTTLE_MS = 80L

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
    }
}
