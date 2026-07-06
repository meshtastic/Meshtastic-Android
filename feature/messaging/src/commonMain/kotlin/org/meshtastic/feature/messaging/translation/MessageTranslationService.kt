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
 * Service interface for translating chat messages on-device.
 *
 * Google flavor provides an ML Kit implementation; fdroid/desktop/iOS provide a no-op that returns
 * [TranslationResult.Unavailable], which hides the translate action entirely.
 */
interface MessageTranslationService {
    /**
     * Translate plain chat text to the target locale, detecting the source language on-device. Never downloads language
     * models — missing models are reported as [TranslationResult.ModelDownloadRequired] so the UI can ask the user
     * before pulling ~30MB per language.
     */
    suspend fun translate(text: String, targetLocale: String): TranslationResult

    /** Check whether translating into the given locale is possible on this platform (model downloadable). */
    suspend fun isLanguageAvailable(locale: String): Boolean

    /** Download the translation models for the given language tags. Only meaningful on google flavor. */
    suspend fun downloadLanguageModels(languageTags: List<String>): DownloadResult
}

/** Result of a message translation attempt. */
sealed class TranslationResult {
    /** Translation succeeded. [translatedText] contains the message text in the target locale. */
    data class Success(val translatedText: String) : TranslationResult()

    /** The message is already in the target language; there is nothing to translate or persist. */
    data object NotRequired : TranslationResult()

    /** The models for [languageTags] must be downloaded (an estimated [estimatedSizeMb]) before translating. */
    data class ModelDownloadRequired(val languageTags: List<String>, val estimatedSizeMb: Int) : TranslationResult()

    /** Translation is not available on this platform/flavor, or the source language could not be determined. */
    data object Unavailable : TranslationResult()
}

/** Result of a model download attempt. */
sealed class DownloadResult {
    data object Success : DownloadResult()

    data class Failed(val reason: String) : DownloadResult()
}
