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
 * No-op translation service for platforms without on-device translation capability (F-Droid, Desktop, iOS).
 *
 * Always returns [TranslationResult.Unavailable], causing the caller to fall back to English.
 */
class NoOpDocTranslator : DocTranslationService {
    override suspend fun translatePage(pageId: String, markdown: String, targetLocale: String): TranslationResult =
        TranslationResult.Unavailable

    override suspend fun isLanguageAvailable(locale: String): Boolean = false

    override suspend fun downloadLanguageModel(locale: String): DownloadResult =
        DownloadResult.Failed("Translation not available on this platform")
}
