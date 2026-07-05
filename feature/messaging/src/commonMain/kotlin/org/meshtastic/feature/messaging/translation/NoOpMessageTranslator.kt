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

/** No-op translator for flavors/platforms without ML Kit (fdroid, desktop). */
class NoOpMessageTranslator : MessageTranslationService {
    override suspend fun translate(text: String, targetLocale: String): TranslationResult =
        TranslationResult.Unavailable

    override suspend fun isLanguageAvailable(targetLocale: String): Boolean = false

    override suspend fun downloadLanguageModel(targetLocale: String): DownloadResult =
        DownloadResult.Failed("Translation not available on this platform")
}
