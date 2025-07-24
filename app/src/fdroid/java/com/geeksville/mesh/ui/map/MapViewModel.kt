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

package com.geeksville.mesh.ui.map

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.model.Node
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class MapViewModel
@Inject
constructor(
    private val preferences: SharedPreferences,
    private val nodeRepository: NodeRepository,
) : ViewModel() {
    val nodesWithPosition
        get() = nodeRepository.nodeDBbyNum.value.values.filter { it.validPosition != null }

    var mapStyleId: Int
        get() = preferences.getInt(MAP_STYLE_ID, 0)
        set(value) = preferences.edit { putInt(MAP_STYLE_ID, value) }

    private val onlyFavorites = MutableStateFlow(preferences.getBoolean("only-favorites", false))
    val nodes: StateFlow<List<Node>> =
        nodeRepository
            .getNodes()
            .onEach { it.filter { !it.isIgnored } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
    private val showWaypointsOnMap = MutableStateFlow(preferences.getBoolean("show-waypoints-on-map", true))
    private val showPrecisionCircleOnMap =
        MutableStateFlow(preferences.getBoolean("show-precision-circle-on-map", true))

    fun setOnlyFavorites(value: Boolean) {
        viewModelScope.launch {
            onlyFavorites.value = value
            preferences.edit { putBoolean("only-favorites", onlyFavorites.value) }
        }
    }

    fun setShowWaypointsOnMap(value: Boolean) {
        viewModelScope.launch {
            showWaypointsOnMap.value = value
            preferences.edit { putBoolean("show-waypoints-on-map", value) }
        }
    }

    fun setShowPrecisionCircleOnMap(value: Boolean) {
        viewModelScope.launch {
            showPrecisionCircleOnMap.value = value
            preferences.edit { putBoolean("show-precision-circle-on-map", value) }
        }
    }

    data class MapFilterState(val onlyFavorites: Boolean, val showWaypoints: Boolean, val showPrecisionCircle: Boolean)

    val mapFilterStateFlow: StateFlow<MapFilterState> =
        combine(onlyFavorites, showWaypointsOnMap, showPrecisionCircleOnMap) {
                favoritesOnly,
                showWaypoints,
                showPrecisionCircle,
            ->
            MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MapFilterState(false, true, true),
            )
}
