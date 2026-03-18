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
package org.meshtastic.feature.node.list

import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.common.UiPreferences
import org.meshtastic.core.model.NodeSortOption

@Single
open class NodeFilterPreferences constructor(private val uiPreferences: UiPreferences) {
    open val includeUnknown = uiPreferences.includeUnknown
    open val excludeInfrastructure = uiPreferences.excludeInfrastructure
    open val onlyOnline = uiPreferences.onlyOnline
    open val onlyDirect = uiPreferences.onlyDirect
    open val showIgnored = uiPreferences.showIgnored
    open val excludeMqtt = uiPreferences.excludeMqtt

    open val nodeSortOption =
        uiPreferences.nodeSort.map { NodeSortOption.entries.getOrElse(it) { NodeSortOption.VIA_FAVORITE } }

    open fun setNodeSort(option: NodeSortOption) {
        uiPreferences.setNodeSort(option.ordinal)
    }

    open fun toggleIncludeUnknown() {
        uiPreferences.setIncludeUnknown(!includeUnknown.value)
    }

    open fun toggleExcludeInfrastructure() {
        uiPreferences.setExcludeInfrastructure(!excludeInfrastructure.value)
    }

    open fun toggleOnlyOnline() {
        uiPreferences.setOnlyOnline(!onlyOnline.value)
    }

    open fun toggleOnlyDirect() {
        uiPreferences.setOnlyDirect(!onlyDirect.value)
    }

    open fun toggleShowIgnored() {
        uiPreferences.setShowIgnored(!showIgnored.value)
    }

    open fun toggleExcludeMqtt() {
        uiPreferences.setExcludeMqtt(!excludeMqtt.value)
    }
}
