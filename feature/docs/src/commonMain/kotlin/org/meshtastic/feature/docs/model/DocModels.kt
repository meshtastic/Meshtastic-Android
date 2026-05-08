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
package org.meshtastic.feature.docs.model

import kotlinx.serialization.Serializable

/**
 * Top-level documentation section.
 */
@Serializable
sealed interface DocSection {
    @Serializable
    data object UserGuide : DocSection

    @Serializable
    data object DeveloperGuide : DocSection

    companion object {
        fun fromString(value: String): DocSection = when (value.lowercase()) {
            "user" -> UserGuide
            "developer" -> DeveloperGuide
            else -> UserGuide
        }

        fun toSlug(section: DocSection): String = when (section) {
            UserGuide -> "user"
            DeveloperGuide -> "developer"
        }

        fun displayName(section: DocSection): String = when (section) {
            UserGuide -> "User Guide"
            DeveloperGuide -> "Developer Guide"
        }
    }
}

/**
 * A single documentation page.
 */
@Serializable
data class DocPage(
    val id: String,
    val title: String,
    val section: DocSection,
    val navOrder: Int,
    val resourcePath: String,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val charCount: Int,
)

/**
 * Content wrapper that decouples metadata from rendered content.
 */
data class DocPageContent(
    val page: DocPage,
    val html: String? = null,
    val markdown: String? = null,
    val cssPath: String? = null,
)

/**
 * Runtime aggregate of the full documentation corpus.
 */
data class DocBundle(
    val pages: List<DocPage>,
    val pageIndex: Map<String, DocPage>,
    val bundleVersion: String,
    val generatedAt: String,
    val totalBytes: Long,
)

/**
 * Build-time keyword index entry decoded at runtime.
 */
@Serializable
data class KeywordIndexEntry(
    val id: String,
    val title: String,
    val section: String,
    val resourcePath: String,
    val navOrder: Int,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val charCount: Int,
)

/**
 * Normalized user search query.
 */
data class DocSearchQuery(
    val rawText: String,
    val normalizedTerms: List<String>,
)

/**
 * Ranked search result.
 */
data class DocSearchResult(
    val page: DocPage,
    val score: Int,
    val matchedTerms: List<String>,
)

/**
 * AI assistant result model.
 */
sealed interface AIDocAssistantResult {
    data class Success(
        val answer: String,
        val sourcePages: List<DocPage>,
        val usedOnDeviceModel: Boolean,
    ) : AIDocAssistantResult

    data class Fallback(
        val message: String,
        val suggestedPages: List<DocPage>,
    ) : AIDocAssistantResult

    data class Error(
        val reason: DocsAiError,
        val suggestedPages: List<DocPage> = emptyList(),
    ) : AIDocAssistantResult
}

/**
 * AI error categories.
 */
sealed interface DocsAiError {
    data object UnsupportedPlatform : DocsAiError
    data object UnsupportedFlavor : DocsAiError
    data object ModelUnavailable : DocsAiError
    data object Busy : DocsAiError
    data object TokenBudgetExceeded : DocsAiError
    data object Unknown : DocsAiError
}

/**
 * Chirpy assistant session state.
 */
data class AIDocAssistantSessionState(
    val messages: List<ChirpyMessage> = emptyList(),
    val isLoading: Boolean = false,
    val draftQuestion: String = "",
)

/**
 * A single message in the Chirpy conversation.
 */
@Serializable
data class ChirpyMessage(
    val id: String,
    val role: ChirpyRole,
    val text: String,
    val sourcePageIds: List<String> = emptyList(),
)

/**
 * Message author role.
 */
@Serializable
enum class ChirpyRole { USER, ASSISTANT, SYSTEM }

