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
package org.meshtastic.feature.node.list

import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.repository.UiPrefs

@Single
@Suppress("TooManyFunctions")
open class NodeFilterPreferences constructor(private val uiPrefs: UiPrefs) {
    open val includeUnknown = uiPrefs.includeUnknown
    open val excludeInfrastructure = uiPrefs.excludeInfrastructure
    open val onlyOnline = uiPrefs.onlyOnline
    open val onlyDirect = uiPrefs.onlyDirect
    open val showIgnored = uiPrefs.showIgnored
    open val excludeMqtt = uiPrefs.excludeMqtt

    // Node list layout preferences
    open val nodeListDensity = uiPrefs.nodeListDensity
    open val shouldShowPower = uiPrefs.shouldShowPower
    open val shouldShowLastHeard = uiPrefs.shouldShowLastHeard
    open val lastHeardIsRelative = uiPrefs.lastHeardIsRelative
    open val shouldShowLocation = uiPrefs.shouldShowLocation
    open val shouldShowHops = uiPrefs.shouldShowHops
    open val shouldShowSignal = uiPrefs.shouldShowSignal
    open val shouldShowChannel = uiPrefs.shouldShowChannel
    open val shouldShowRole = uiPrefs.shouldShowRole
    open val shouldShowTelemetry = uiPrefs.shouldShowTelemetry

    open val nodeSortOption =
        uiPrefs.nodeSort.map { NodeSortOption.entries.getOrElse(it) { NodeSortOption.VIA_FAVORITE } }

    open fun setNodeSort(option: NodeSortOption) {
        uiPrefs.setNodeSort(option.ordinal)
    }

    open fun setNodeListDensity(value: String) {
        uiPrefs.setNodeListDensity(value)
    }

    open fun setShouldShowPower(value: Boolean) {
        uiPrefs.setShouldShowPower(value)
    }

    open fun setShouldShowLastHeard(value: Boolean) {
        uiPrefs.setShouldShowLastHeard(value)
    }

    open fun setLastHeardIsRelative(value: Boolean) {
        uiPrefs.setLastHeardIsRelative(value)
    }

    open fun setShouldShowLocation(value: Boolean) {
        uiPrefs.setShouldShowLocation(value)
    }

    open fun setShouldShowHops(value: Boolean) {
        uiPrefs.setShouldShowHops(value)
    }

    open fun setShouldShowSignal(value: Boolean) {
        uiPrefs.setShouldShowSignal(value)
    }

    open fun setShouldShowChannel(value: Boolean) {
        uiPrefs.setShouldShowChannel(value)
    }

    open fun setShouldShowRole(value: Boolean) {
        uiPrefs.setShouldShowRole(value)
    }

    open fun setShouldShowTelemetry(value: Boolean) {
        uiPrefs.setShouldShowTelemetry(value)
    }

    open fun toggleIncludeUnknown() {
        uiPrefs.setIncludeUnknown(!includeUnknown.value)
    }

    open fun toggleExcludeInfrastructure() {
        uiPrefs.setExcludeInfrastructure(!excludeInfrastructure.value)
    }

    open fun toggleOnlyOnline() {
        uiPrefs.setOnlyOnline(!onlyOnline.value)
    }

    open fun toggleOnlyDirect() {
        uiPrefs.setOnlyDirect(!onlyDirect.value)
    }

    open fun toggleShowIgnored() {
        uiPrefs.setShowIgnored(!showIgnored.value)
    }

    open fun toggleExcludeMqtt() {
        uiPrefs.setExcludeMqtt(!excludeMqtt.value)
    }
}
