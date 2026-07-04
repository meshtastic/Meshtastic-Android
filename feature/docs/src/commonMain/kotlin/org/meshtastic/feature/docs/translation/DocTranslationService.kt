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
package org.meshtastic.feature.docs.translation

/**
 * Service interface for translating documentation pages at runtime.
 *
 * Used as a fallback when Crowdin-translated markdown bundles are not available for the user's locale. Google flavor
 * provides ML Kit implementation; fdroid/desktop/iOS provide a no-op that returns [TranslationResult.Unavailable].
 */
interface DocTranslationService {
    /** Translate a markdown page to the target locale. Returns the fully-translated markdown or a status. */
    suspend fun translatePage(pageId: String, markdown: String, targetLocale: String): TranslationResult

    /** Check if translation to the given locale is possible (model downloaded or downloadable). */
    suspend fun isLanguageAvailable(locale: String): Boolean

    /** Download the translation model for a locale. Only meaningful on google flavor. */
    suspend fun downloadLanguageModel(locale: String): DownloadResult
}

/** Result of a translation attempt. */
sealed class TranslationResult {
    /** Translation succeeded. [translatedMarkdown] contains the complete translated markdown source. */
    data class Success(val translatedMarkdown: String) : TranslationResult()

    /** Translation model needs to be downloaded before translating. */
    data class ModelDownloadRequired(val locale: String, val estimatedSizeMb: Int) : TranslationResult()

    /** Translation is not available on this platform/flavor. Caller should fall back to English. */
    data object Unavailable : TranslationResult()
}

/** Result of a model download attempt. */
sealed class DownloadResult {
    data object Success : DownloadResult()

    data class Failed(val reason: String) : DownloadResult()
}
