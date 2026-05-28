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
package org.meshtastic.app.di

import android.content.Context
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.app.ai.GeminiNanoDocAssistant
import org.meshtastic.app.translation.MlKitDocTranslator
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.data.DocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import org.meshtastic.feature.docs.translation.DocTranslationCache
import org.meshtastic.feature.docs.translation.DocTranslationService

// TODO: Enable Firebase App Check (with Play Integrity provider) if hybrid/cloud
//  fallback is ever adopted. App Check only gates cloud proxy requests — on-device
//  inference (ONLY_ON_DEVICE mode) bypasses it entirely, so no action needed today.

/** Provides the on-device Gemini Nano AI assistant for the Google flavor. */
@Module
class GoogleAiModule {
    @Single
    fun aiDocAssistant(
        searchEngine: KeywordSearchEngine,
        bundleLoader: DocBundleLoader,
        nodeRepository: NodeRepository,
    ): AIDocAssistant = GeminiNanoDocAssistant(searchEngine, bundleLoader, nodeRepository)

    @Single
    fun docTranslationCache(context: Context): DocTranslationCache =
        DocTranslationCache(cacheDir = context.cacheDir.toOkioPath(), fileSystem = FileSystem.SYSTEM)

    @Single fun docTranslationService(cache: DocTranslationCache): DocTranslationService = MlKitDocTranslator(cache)
}
