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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.feature.discovery.export.DiscoveryExportData
import org.meshtastic.feature.discovery.export.DiscoveryExporter
import org.meshtastic.feature.discovery.export.ExportResult

@KoinViewModel
class DiscoverySummaryViewModel(
    @InjectedParam private val sessionId: Long,
    private val discoveryDao: DiscoveryDao,
    private val summaryGenerator: DiscoverySummaryGenerator,
    private val aiProvider: DiscoverySummaryAiProvider,
    private val exporter: DiscoveryExporter,
) : ViewModel() {

    val session: StateFlow<DiscoverySessionEntity?> =
        discoveryDao.getSessionFlow(sessionId).stateInWhileSubscribed(initialValue = null)

    val presetResults: StateFlow<List<DiscoveryPresetResultEntity>> =
        discoveryDao.getPresetResultsFlow(sessionId).stateInWhileSubscribed(initialValue = emptyList())

    private val _nodesByPreset = MutableStateFlow<Map<Long, List<DiscoveredNodeEntity>>>(emptyMap())
    val nodesByPreset: StateFlow<Map<Long, List<DiscoveredNodeEntity>>> = _nodesByPreset.asStateFlow()

    private val _algorithmicSummary = MutableStateFlow<String?>(null)
    val algorithmicSummary: StateFlow<String?> = _algorithmicSummary.asStateFlow()

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    private val _presetAiSummaries = MutableStateFlow<Map<Long, String>>(emptyMap())
    val presetAiSummaries: StateFlow<Map<Long, String>> = _presetAiSummaries.asStateFlow()

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    init {
        loadNodes()
    }

    fun exportReport() {
        safeLaunch(tag = "exportReport") {
            val currentSession =
                discoveryDao.getSession(sessionId)
                    ?: run {
                        _exportResult.value = ExportResult.Error("Session not found")
                        return@safeLaunch
                    }
            val results = discoveryDao.getPresetResults(sessionId)
            val exportData =
                DiscoveryExportData(
                    session = currentSession,
                    presetResults = results,
                    nodesByPreset = _nodesByPreset.value,
                )
            _exportResult.value = exporter.export(exportData)
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    /** Re-run all AI analysis, clearing cached results first. */
    fun rerunAnalysis() {
        safeLaunch(tag = "rerunAnalysis") {
            _isGeneratingAi.value = true
            _aiSummary.value = null
            _presetAiSummaries.value = emptyMap()

            val currentSession = discoveryDao.getSession(sessionId) ?: return@safeLaunch
            val results = discoveryDao.getPresetResults(sessionId)

            // Clear persisted AI summaries
            discoveryDao.updateSession(currentSession.copy(aiSummary = null))
            for (result in results) {
                discoveryDao.updatePresetResult(result.copy(aiSummary = null))
            }

            // Regenerate algorithmic
            _algorithmicSummary.value = summaryGenerator.generateSessionSummary(currentSession, results)

            // Regenerate AI
            generateAiSummary(currentSession, results)
            generatePresetAiSummaries(results)

            _isGeneratingAi.value = false
        }
    }

    private fun loadNodes() {
        safeLaunch(tag = "loadNodes") {
            val results = discoveryDao.getPresetResults(sessionId)
            val nodesMap = mutableMapOf<Long, List<DiscoveredNodeEntity>>()
            for (result in results) {
                nodesMap[result.id] = discoveryDao.getDiscoveredNodes(result.id)
            }
            _nodesByPreset.value = nodesMap

            // Load cached per-preset AI summaries
            val cachedPresetSummaries =
                results.filter { !it.aiSummary.isNullOrBlank() }.associate { it.id to it.aiSummary!! }
            _presetAiSummaries.value = cachedPresetSummaries

            val session = discoveryDao.getSession(sessionId)
            if (session != null) {
                _algorithmicSummary.value = summaryGenerator.generateSessionSummary(session, results)

                // Use cached AI summary if available, otherwise generate
                if (!session.aiSummary.isNullOrBlank()) {
                    _aiSummary.value = session.aiSummary
                } else {
                    generateAiSummary(session, results)
                }

                // Generate per-preset summaries for any without cached results
                val uncached = results.filter { it.aiSummary.isNullOrBlank() && it.uniqueNodes > 0 }
                if (uncached.isNotEmpty()) {
                    generatePresetAiSummaries(uncached)
                }
            }
        }
    }

    private fun generateAiSummary(session: DiscoverySessionEntity, results: List<DiscoveryPresetResultEntity>) {
        if (!aiProvider.isAvailable) return
        safeLaunch(tag = "aiSummary") {
            val summary = aiProvider.generateSessionSummary(session, results)
            if (summary != null) {
                _aiSummary.value = summary
                discoveryDao.updateSession(session.copy(aiSummary = summary))
            }
        }
    }

    private fun generatePresetAiSummaries(results: List<DiscoveryPresetResultEntity>) {
        if (!aiProvider.isAvailable) return
        safeLaunch(tag = "presetAiSummaries") {
            for (result in results) {
                val summary = aiProvider.generatePresetSummary(result)
                if (summary != null) {
                    _presetAiSummaries.value = _presetAiSummaries.value + (result.id to summary)
                    discoveryDao.updatePresetResult(result.copy(aiSummary = summary))
                }
            }
        }
    }
}
