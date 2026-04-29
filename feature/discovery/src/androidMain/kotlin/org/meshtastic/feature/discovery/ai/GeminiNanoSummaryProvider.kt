/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.discovery.ai

import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.DiscoverySummaryGenerator

// TODO: Replace with real Gemini Nano on-device implementation once
//  `com.google.ai.edge:aicore` or `com.google.android.gms:play-services-generativeai`
//  is added to libs.versions.toml. The implementation should:
//  1. Check model availability via GenerativeModel.isAvailable()
//  2. Build a structured prompt with session metrics (nodes, utilization, presets)
//  3. Call generateContent() with the prompt
//  4. Fall back to the algorithmic generator on any error

/**
 * Android provider that will use Gemini Nano for on-device AI summaries.
 *
 * Currently delegates to [DiscoverySummaryGenerator] because the Gemini Nano SDK dependency is not yet in the version
 * catalog.
 */
@Single(binds = [DiscoverySummaryAiProvider::class])
class GeminiNanoSummaryProvider(private val generator: DiscoverySummaryGenerator) : DiscoverySummaryAiProvider {

    // Delegates to DiscoverySummaryGenerator (algorithmic) so results are always available.
    // When real Gemini Nano SDK is wired, this should check GenerativeModel.isAvailable() at runtime.
    override val isAvailable: Boolean = true

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String = generator.generateSessionSummary(session, presetResults)

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String =
        generator.generatePresetSummary(result)
}
