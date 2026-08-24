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

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.feature.discovery.ai.AlgorithmicSummaryProvider
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.ai.KeywordFallbackAssistant
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.NoOpDocTranslator
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator

/** Provides keyword-only fallback AI assistant for the F-Droid flavor (no on-device model). */
@Module
class FdroidAiModule {
    @Single fun aiDocAssistant(fallback: KeywordFallbackAssistant): AIDocAssistant = fallback

    @Single fun discoverySummaryAiProvider(fallback: AlgorithmicSummaryProvider): DiscoverySummaryAiProvider = fallback

    @Single fun docTranslationService(): DocTranslationService = NoOpDocTranslator()

    @Single fun messageTranslationService(): MessageTranslationService = NoOpMessageTranslator()
}
