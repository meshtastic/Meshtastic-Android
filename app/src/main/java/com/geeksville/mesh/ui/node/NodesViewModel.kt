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

package com.geeksville.mesh.ui.node

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.meshtastic.core.prefs.ui.UiPrefs
import javax.inject.Inject

@HiltViewModel
class NodesViewModel @Inject constructor(private val uiPrefs: UiPrefs) : ViewModel() {
    private val nodeFilterText = MutableStateFlow("")
    private val includeUnknown = MutableStateFlow(uiPrefs.includeUnknown)
    private val onlyOnline = MutableStateFlow(uiPrefs.onlyOnline)
    private val onlyDirect = MutableStateFlow(uiPrefs.onlyDirect)
    private val _showIgnored = MutableStateFlow(uiPrefs.showIgnored)
    val showIgnored: StateFlow<Boolean> = _showIgnored

    val nodeFilterStateFlow: Flow<NodeFilterState> =
        combine(nodeFilterText, includeUnknown, onlyOnline, onlyDirect, showIgnored) {
                filterText,
                includeUnknown,
                onlyOnline,
                onlyDirect,
                showIgnored,
            ->
            NodeFilterState(filterText, includeUnknown, onlyOnline, onlyDirect, showIgnored)
        }

    fun setNodeFilterText(text: String) {
        nodeFilterText.value = text
    }

    fun toggleIncludeUnknown() = toggle(includeUnknown) { uiPrefs.includeUnknown = it }

    fun toggleOnlyOnline() = toggle(onlyOnline) { uiPrefs.onlyOnline = it }

    fun toggleOnlyDirect() = toggle(onlyDirect) { uiPrefs.onlyDirect = it }

    fun toggleShowIgnored() = toggle(_showIgnored) { uiPrefs.showIgnored = it }

    private fun toggle(state: MutableStateFlow<Boolean>, onChanged: (newValue: Boolean) -> Unit) {
        (!state.value).let { toggled ->
            state.update { toggled }
            onChanged(toggled)
        }
    }
}

data class NodeFilterState(
    val filterText: String,
    val includeUnknown: Boolean,
    val onlyOnline: Boolean,
    val onlyDirect: Boolean,
    val showIgnored: Boolean,
)
