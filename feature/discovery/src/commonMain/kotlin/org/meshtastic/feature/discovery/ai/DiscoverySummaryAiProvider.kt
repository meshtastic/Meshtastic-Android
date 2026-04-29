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

import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity

/**
 * Abstraction for generating natural-language summaries of discovery scan results.
 *
 * Platform implementations may use on-device AI (e.g. Gemini Nano on Android) or fall back to the algorithmic
 * [org.meshtastic.feature.discovery.DiscoverySummaryGenerator].
 */
interface DiscoverySummaryAiProvider {
    /** Whether this provider is ready to generate AI summaries. */
    val isAvailable: Boolean

    /** Generate a session-level summary across all preset results. Returns `null` on failure. */
    suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String?

    /** Generate a per-preset summary. Returns `null` on failure. */
    suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String?
}
