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
package org.meshtastic.feature.settings.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.resources.getStringSuspend

/**
 * Backs the search field on the Settings screen.
 *
 * The catalog's text is resolved once, not per keystroke: reading a string resource suspends, and there are several
 * hundred of them. Matching then runs over the resolved copy.
 */
@KoinViewModel
class SettingsSearchViewModel : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val resolved = MutableStateFlow<List<ResolvedSettingsEntry>>(emptyList())

    /**
     * Whether this phone's own settings are offered. False while administering another node remotely: both settings
     * screens hide their app-settings section then, and search must not be a way back in to what they hide.
     */
    private val _includeAppLocal = MutableStateFlow(true)

    val results: StateFlow<List<ResolvedSettingsEntry>> =
        combine(resolved, _query, _includeAppLocal) { entries, query, includeAppLocal ->
            SettingsSearchMatcher.rank(entries.filter { includeAppLocal || !it.isAppLocal }, query)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch {
            resolved.value =
                SettingsSearchCatalog.entries().map { entry ->
                    ResolvedSettingsEntry(
                        title = getStringSuspend(entry.title),
                        description = entry.description?.let { getStringSuspend(it) },
                        screenTitle = getStringSuspend(entry.screenTitle),
                        route = entry.route,
                        isAppLocal = entry.isAppLocal,
                    )
                }
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setIncludeAppLocal(include: Boolean) {
        _includeAppLocal.value = include
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
