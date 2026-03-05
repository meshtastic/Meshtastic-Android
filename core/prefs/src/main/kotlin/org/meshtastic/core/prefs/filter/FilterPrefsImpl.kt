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
package org.meshtastic.core.prefs.filter

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.FilterSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.FilterPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterPrefsImpl
@Inject
constructor(
    @FilterSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : FilterPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val filterEnabled: StateFlow<Boolean> =
        prefs
            .preferenceFlow(KEY_FILTER_ENABLED) { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(KEY_FILTER_ENABLED, false))

    override fun setFilterEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FILTER_ENABLED, enabled) }
    }

    override val filterWords: StateFlow<Set<String>> =
        prefs
            .preferenceFlow(KEY_FILTER_WORDS) { p, k -> p.getStringSet(k, emptySet()) ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getStringSet(KEY_FILTER_WORDS, emptySet()) ?: emptySet())

    override fun setFilterWords(words: Set<String>) {
        prefs.edit { putStringSet(KEY_FILTER_WORDS, words) }
    }

    companion object {
        const val KEY_FILTER_ENABLED = "filter_enabled"
        const val KEY_FILTER_WORDS = "filter_words"
        const val FILTER_PREFS_NAME = "filter-prefs"
    }
}
