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
package org.meshtastic.feature.discovery.export

import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity

data class DiscoveryExportData(
    val session: DiscoverySessionEntity,
    val presetResults: List<DiscoveryPresetResultEntity>,
    val nodesByPreset: Map<Long, List<DiscoveredNodeEntity>>,
)

interface DiscoveryExporter {
    suspend fun export(data: DiscoveryExportData): ExportResult
}

sealed interface ExportResult {
    data class Success(val content: ByteArray, val mimeType: String, val fileName: String) : ExportResult

    data class Error(val message: String) : ExportResult
}
