/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.node.list

import kotlinx.coroutines.flow.map
import org.meshtastic.core.database.model.NodeSortOption
import org.meshtastic.core.datastore.UiPreferencesDataSource
import javax.inject.Inject

class NodeFilterPreferences @Inject constructor(private val uiPreferencesDataSource: UiPreferencesDataSource) {
    val includeUnknown = uiPreferencesDataSource.includeUnknown
    val excludeInfrastructure = uiPreferencesDataSource.excludeInfrastructure
    val onlyOnline = uiPreferencesDataSource.onlyOnline
    val onlyDirect = uiPreferencesDataSource.onlyDirect
    val showIgnored = uiPreferencesDataSource.showIgnored

    val nodeSortOption =
        uiPreferencesDataSource.nodeSort.map { NodeSortOption.entries.getOrElse(it) { NodeSortOption.VIA_FAVORITE } }

    fun setNodeSort(option: NodeSortOption) {
        uiPreferencesDataSource.setNodeSort(option.ordinal)
    }

    fun toggleIncludeUnknown() {
        uiPreferencesDataSource.setIncludeUnknown(!includeUnknown.value)
    }

    fun toggleExcludeInfrastructure() {
        uiPreferencesDataSource.setExcludeInfrastructure(!excludeInfrastructure.value)
    }

    fun toggleOnlyOnline() {
        uiPreferencesDataSource.setOnlyOnline(!onlyOnline.value)
    }

    fun toggleOnlyDirect() {
        uiPreferencesDataSource.setOnlyDirect(!onlyDirect.value)
    }

    fun toggleShowIgnored() {
        uiPreferencesDataSource.setShowIgnored(!showIgnored.value)
    }
}
