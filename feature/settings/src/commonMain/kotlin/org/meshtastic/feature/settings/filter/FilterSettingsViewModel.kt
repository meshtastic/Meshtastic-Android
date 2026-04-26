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
package org.meshtastic.feature.settings.filter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.UiPrefs

@KoinViewModel
class FilterSettingsViewModel(
    private val filterPrefs: FilterPrefs,
    private val uiPrefs: UiPrefs,
    private val messageFilter: MessageFilter,
) :
    ViewModel() {

    private val _filterEnabled = MutableStateFlow(filterPrefs.filterEnabled.value)
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    private val _filterWords = MutableStateFlow(filterPrefs.filterWords.value.toList().sorted())
    val filterWords: StateFlow<List<String>> = _filterWords.asStateFlow()

    private val _keywordMonitors = MutableStateFlow(uiPrefs.keywordMonitors.value.toList().sorted())
    val keywordMonitors: StateFlow<List<String>> = _keywordMonitors.asStateFlow()

    fun setFilterEnabled(enabled: Boolean) {
        filterPrefs.setFilterEnabled(enabled)
        _filterEnabled.value = enabled
    }

    fun addFilterWord(word: String) {
        if (word.isBlank()) return
        val trimmed = word.trim()
        val current = filterPrefs.filterWords.value.toMutableSet()
        if (current.add(trimmed)) {
            filterPrefs.setFilterWords(current)
            _filterWords.value = current.toList().sorted()
            messageFilter.rebuildPatterns()
        }
    }

    fun removeFilterWord(word: String) {
        val current = filterPrefs.filterWords.value.toMutableSet()
        if (current.remove(word)) {
            filterPrefs.setFilterWords(current)
            _filterWords.value = current.toList().sorted()
            messageFilter.rebuildPatterns()
        }
    }

    fun addKeywordMonitor(word: String) {
        if (word.isBlank()) return
        val trimmed = word.trim()
        val current = uiPrefs.keywordMonitors.value.toMutableSet()
        if (current.add(trimmed)) {
            uiPrefs.setKeywordMonitors(current)
            _keywordMonitors.value = current.toList().sorted()
        }
    }

    fun removeKeywordMonitor(word: String) {
        val current = uiPrefs.keywordMonitors.value.toMutableSet()
        if (current.remove(word)) {
            uiPrefs.setKeywordMonitors(current)
            _keywordMonitors.value = current.toList().sorted()
        }
    }
}
