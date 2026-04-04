/*
 * Copyright (c) 2026 Chris7X
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

package org.meshtastic.feature.ai.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.feature.ai.llm.LlmRepository
import org.meshtastic.feature.ai.llm.SmartReplyGenerator

/**
 * Koin module for AI feature (commonMain).
 * Platform-specific LlmRepository binding is in FeatureAiAndroidModule.
 */
@Module
class AiModule {
    @Single
    fun provideSmartReplyGenerator(repository: LlmRepository): SmartReplyGenerator =
        SmartReplyGenerator(repository)
}
