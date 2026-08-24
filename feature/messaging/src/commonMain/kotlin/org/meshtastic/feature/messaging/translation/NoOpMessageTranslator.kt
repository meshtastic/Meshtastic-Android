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
 * No-op message translation service for platforms without on-device translation capability (F-Droid, Desktop, iOS).
 *
 * Always reports translation as unavailable, which hides the translate action in the message UI.
 */
class NoOpMessageTranslator : MessageTranslationService {
    override suspend fun translate(text: String, targetLocale: String): TranslationResult =
        TranslationResult.Unavailable

    override suspend fun isLanguageAvailable(locale: String): Boolean = false

    override suspend fun downloadLanguageModels(languageTags: List<String>): DownloadResult =
        DownloadResult.Failed("Translation not available on this platform")
}
