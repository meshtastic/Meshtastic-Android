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
package org.meshtastic.core.prefs.filter

import android.content.SharedPreferences
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.StringSetPrefDelegate
import javax.inject.Inject
import javax.inject.Singleton

/** Interface for managing message filter preferences. */
interface FilterPrefs {
    /** Whether message filtering is enabled. */
    var filterEnabled: Boolean

    /** Set of words to filter messages on. */
    var filterWords: Set<String>

    companion object {
        /** Key for the filterEnabled preference. */
        const val KEY_FILTER_ENABLED = "filter_enabled"

        /** Key for the filterWords preference. */
        const val KEY_FILTER_WORDS = "filter_words"

        /** Name of the SharedPreferences file where filter preferences are stored. */
        const val FILTER_PREFS_NAME = "filter-prefs"
    }
}

@Singleton
class FilterPrefsImpl @Inject constructor(
    private val prefs: SharedPreferences,
) : FilterPrefs {
    override var filterEnabled: Boolean by PrefDelegate(prefs, FilterPrefs.KEY_FILTER_ENABLED, false)
    override var filterWords: Set<String> by StringSetPrefDelegate(prefs, FilterPrefs.KEY_FILTER_WORDS, emptySet())
}
