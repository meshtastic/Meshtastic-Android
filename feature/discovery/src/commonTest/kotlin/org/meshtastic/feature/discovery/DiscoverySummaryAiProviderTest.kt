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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoverySummaryAiProviderTest {

    private val testSession =
        DiscoverySessionEntity(
            id = 1L,
            timestamp = 1_000_000L,
            presetsScanned = "LONG_FAST",
            homePreset = "LONG_FAST",
            totalUniqueNodes = 5,
            completionStatus = "complete",
        )

    private val testPresetResult =
        DiscoveryPresetResultEntity(
            id = 1L,
            sessionId = 1L,
            presetName = "LONG_FAST",
            dwellDurationSeconds = 30L,
            uniqueNodes = 3,
            directNeighborCount = 2,
            meshNeighborCount = 1,
            messageCount = 5,
            sensorPacketCount = 2,
        )

    // --- Supported case: provider available and returns results ---

    @Test
    fun supported_provider_returns_session_summary() = runTest {
        val provider = AvailableAiProvider(sessionResult = "AI recommends LONG_FAST")
        assertTrue(provider.isAvailable)
        val result = provider.generateSessionSummary(testSession, listOf(testPresetResult))
        assertEquals("AI recommends LONG_FAST", result)
    }

    @Test
    fun supported_provider_returns_preset_summary() = runTest {
        val provider = AvailableAiProvider(presetResult = "LONG_FAST: Good range, low congestion")
        assertTrue(provider.isAvailable)
        val result = provider.generatePresetSummary(testPresetResult)
        assertEquals("LONG_FAST: Good range, low congestion", result)
    }

    // --- Unsupported case: provider not available ---

    @Test
    fun unsupported_provider_reports_not_available() {
        val provider = UnavailableAiProvider()
        assertTrue(!provider.isAvailable)
    }

    @Test
    fun unsupported_provider_returns_null_for_session_summary() = runTest {
        val provider = UnavailableAiProvider()
        val result = provider.generateSessionSummary(testSession, listOf(testPresetResult))
        assertNull(result)
    }

    @Test
    fun unsupported_provider_returns_null_for_preset_summary() = runTest {
        val provider = UnavailableAiProvider()
        val result = provider.generatePresetSummary(testPresetResult)
        assertNull(result)
    }

    // --- Failure case: provider throws or returns null ---

    @Test
    fun failing_provider_returns_null_on_session_error() = runTest {
        val provider = FailingAiProvider()
        assertTrue(provider.isAvailable) // Provider thinks it's available but fails
        val result = provider.generateSessionSummary(testSession, listOf(testPresetResult))
        assertNull(result)
    }

    @Test
    fun failing_provider_returns_null_on_preset_error() = runTest {
        val provider = FailingAiProvider()
        val result = provider.generatePresetSummary(testPresetResult)
        assertNull(result)
    }

    // --- Algorithmic fallback always works ---

    @Test
    fun algorithmic_generator_produces_non_null_summary() {
        val generator = DiscoverySummaryGenerator()
        val summary = generator.generateSessionSummary(testSession, listOf(testPresetResult))
        assertNotNull(summary)
        assertTrue(summary.contains("LONG_FAST"))
    }

    @Test
    fun algorithmic_generator_handles_empty_presets() {
        val generator = DiscoverySummaryGenerator()
        val summary = generator.generateSessionSummary(testSession, emptyList())
        assertEquals("No presets were scanned during this session.", summary)
    }
}

// --- Test doubles ---

private class AvailableAiProvider(
    private val sessionResult: String? = "AI summary",
    private val presetResult: String? = "Preset summary",
) : DiscoverySummaryAiProvider {
    override val isAvailable: Boolean = true

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String? = sessionResult

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = presetResult
}

private class UnavailableAiProvider : DiscoverySummaryAiProvider {
    override val isAvailable: Boolean = false

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String? = null

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = null
}

private class FailingAiProvider : DiscoverySummaryAiProvider {
    override val isAvailable: Boolean = true

    override suspend fun generateSessionSummary(
        session: DiscoverySessionEntity,
        presetResults: List<DiscoveryPresetResultEntity>,
    ): String? = null // Simulates internal failure returning null

    override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = null
}
