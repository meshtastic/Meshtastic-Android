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
package org.meshtastic.feature.discovery.ai

import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.DiscoverySummaryGenerator

/**
 * Algorithmic [DiscoverySummaryAiProvider] that delegates to the deterministic [DiscoverySummaryGenerator].
 *
 * Used wherever no on-device AI model is available: Desktop, iOS, and the Android F-Droid flavor. Registered with
 * `binds = []` so it is injectable by concrete type but is not auto-bound to [DiscoverySummaryAiProvider]; each
 * platform binds the interface explicitly (the Google flavor binds the Gemini Nano provider instead).
 */
@Single(binds = [])
class AlgorithmicSummaryProvider(private val generator: DiscoverySummaryGenerator) : DiscoverySummaryAiProvider {

    override val isAvailable: Boolean = true

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String = generator.generateSessionSummary(session, presetResults)

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String =
        generator.generatePresetSummary(result)
}
