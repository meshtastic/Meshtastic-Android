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
package org.meshtastic.feature.messaging.translation

/**
 * Service interface for translating chat message text at runtime.
 *
 * Mirrors `DocTranslationService` in `:feature:docs`. Google flavor provides an ML Kit implementation; fdroid/desktop
 * provide a no-op that returns [TranslationResult.Unavailable].
 */
interface MessageTranslationService {
    /** Translate plain message text to the target locale. Returns the translated text or a status. */
    suspend fun translate(text: String, targetLocale: String): TranslationResult

    /** Whether [targetLocale] is a supported translation target on this platform/flavor. */
    suspend fun isLanguageAvailable(targetLocale: String): Boolean

    /** Download the translation model for a locale. Only meaningful on google flavor. */
    suspend fun downloadLanguageModel(targetLocale: String): DownloadResult
}

/** Result of a translation attempt. */
sealed class TranslationResult {
    /** Translation succeeded. */
    data class Success(val translatedText: String) : TranslationResult()

    /** Translation model needs to be downloaded before translating. */
    data class ModelDownloadRequired(val locale: String, val estimatedSizeMb: Int) : TranslationResult()

    /** Translation is not available on this platform/flavor. */
    data object Unavailable : TranslationResult()
}

/** Result of a model download attempt. */
sealed class DownloadResult {
    data object Success : DownloadResult()

    data class Failed(val reason: String) : DownloadResult()
}
