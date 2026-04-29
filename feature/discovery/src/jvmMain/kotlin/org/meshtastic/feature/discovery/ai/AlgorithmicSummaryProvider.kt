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

/** JVM/Desktop fallback that delegates to the algorithmic [DiscoverySummaryGenerator]. */
@Single(binds = [DiscoverySummaryAiProvider::class])
class AlgorithmicSummaryProvider(private val generator: DiscoverySummaryGenerator) : DiscoverySummaryAiProvider {

    override val isAvailable: Boolean = true

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String = generator.generateSessionSummary(session, presetResults)

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String =
        generator.generatePresetSummary(result)
}
