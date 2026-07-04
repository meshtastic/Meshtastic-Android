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
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_section_developer
import org.meshtastic.core.resources.doc_section_user

/** Top-level documentation section. */
@Serializable
sealed interface DocSection {
    @Serializable data object UserGuide : DocSection

    @Serializable data object DeveloperGuide : DocSection

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

        suspend fun displayName(section: DocSection): String = when (section) {
            UserGuide -> getString(Res.string.doc_section_user)
            DeveloperGuide -> getString(Res.string.doc_section_developer)
        }
    }
}

/** A single documentation page. */
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
    /** Icon identifier for TOC display (maps to MeshtasticIcons). */
    val iconId: String? = null,
)

/** Content wrapper that decouples metadata from rendered content. */
data class DocPageContent(
    val page: DocPage,
    val html: String? = null,
    val markdown: String? = null,
    val cssPath: String? = null,
)

/** Runtime aggregate of the full documentation corpus. */
data class DocBundle(
    val pages: List<DocPage>,
    val pageIndex: Map<String, DocPage>,
    val bundleVersion: String,
    val generatedAt: String,
    val totalBytes: Long,
)

/** Build-time keyword index entry decoded at runtime. */
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
    val iconId: String? = null,
)

/** Normalized user search query. */
data class DocSearchQuery(val rawText: String, val normalizedTerms: List<String>)

/** Ranked search result. */
data class DocSearchResult(val page: DocPage, val score: Int, val matchedTerms: List<String>)

/** AI assistant result model. */
sealed interface AIDocAssistantResult {
    data class Partial(val answer: String, val sourcePages: List<DocPage>, val usedOnDeviceModel: Boolean) :
        AIDocAssistantResult

    data class Success(val answer: String, val sourcePages: List<DocPage>, val usedOnDeviceModel: Boolean) :
        AIDocAssistantResult

    data class Fallback(val message: String, val suggestedPages: List<DocPage>) : AIDocAssistantResult

    data class Error(val reason: DocsAiError, val suggestedPages: List<DocPage> = emptyList()) : AIDocAssistantResult
}

/** AI error categories. */
sealed interface DocsAiError {
    data object UnsupportedPlatform : DocsAiError

    data object UnsupportedFlavor : DocsAiError

    data object ModelUnavailable : DocsAiError

    data object Busy : DocsAiError

    data object TokenBudgetExceeded : DocsAiError

    data object Unknown : DocsAiError
}

/** Model readiness state for download/lifecycle UX. */
sealed interface ModelReadiness {
    /** Initial status check in progress. */
    data object Checking : ModelReadiness

    /** Model is downloading. [totalBytes] of 0 means indeterminate progress. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelReadiness {
        /** Download progress as a fraction (0.0 to 1.0), or null if indeterminate. */
        val progress: Float?
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    /** Model is ready for inference. */
    data object Available : ModelReadiness

    /** Model is not available on this device. */
    data class Unavailable(val reason: String?) : ModelReadiness
}

/** Chirpy assistant session state. */
data class AIDocAssistantSessionState(
    val messages: List<ChirpyMessage> = emptyList(),
    val isLoading: Boolean = false,
    val draftQuestion: String = "",
)

/** Reference to a source doc page shown as a chip in Chirpy replies. */
@Serializable data class SourceRef(val id: String, val title: String)

/** A single message in the Chirpy conversation. */
@Serializable
data class ChirpyMessage(
    val id: String,
    val role: ChirpyRole,
    val text: String,
    val sources: List<SourceRef> = emptyList(),
)

/** Message author role. */
@Serializable
enum class ChirpyRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/** Indicates the source of displayed page content for translation attribution. */
enum class TranslationSource {
    /** English source or Crowdin community translation. */
    BUNDLED,

    /** ML Kit on-device auto-translation (Google flavor only). */
    ML_KIT,
}
