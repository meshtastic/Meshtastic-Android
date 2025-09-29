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

package org.meshtastic.core.prefs.ui

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.di.UiSharedPreferences
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface UiPrefs {
    var hasShownNotPairedWarning: Boolean
    var nodeSortOption: Int
    var includeUnknown: Boolean
    var showDetails: Boolean
    var onlyOnline: Boolean
    var onlyDirect: Boolean
    var showIgnored: Boolean

    fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean>

    fun setShouldProvideNodeLocation(nodeNum: Int, value: Boolean)
}

@Singleton
class UiPrefsImpl @Inject constructor(@UiSharedPreferences private val prefs: SharedPreferences) : UiPrefs {

    // Maps nodeNum to a flow for the for the "provide-location-nodeNum" pref
    private val provideNodeLocationFlows = ConcurrentHashMap<Int, MutableStateFlow<Boolean>>()

    private val sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                // Check if the changed key is one of our node location keys
                else ->
                    provideNodeLocationFlows.keys.forEach { nodeNum ->
                        if (key == provideLocationKey(nodeNum)) {
                            val newValue = sharedPreferences.getBoolean(key, false)
                            provideNodeLocationFlows[nodeNum]?.tryEmit(newValue)
                        }
                    }
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    override var hasShownNotPairedWarning: Boolean by PrefDelegate(prefs, "has_shown_not_paired_warning", false)
    override var nodeSortOption: Int by PrefDelegate(prefs, "node-sort-option", -1)
    override var includeUnknown: Boolean by PrefDelegate(prefs, "include-unknown", false)
    override var showDetails: Boolean by PrefDelegate(prefs, "show-details", false)
    override var onlyOnline: Boolean by PrefDelegate(prefs, "only-online", false)
    override var onlyDirect: Boolean by PrefDelegate(prefs, "only-direct", false)
    override var showIgnored: Boolean by PrefDelegate(prefs, "show-ignored", false)

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> = provideNodeLocationFlows
        .getOrPut(nodeNum) { MutableStateFlow(prefs.getBoolean(provideLocationKey(nodeNum), false)) }
        .asStateFlow()

    override fun setShouldProvideNodeLocation(nodeNum: Int, value: Boolean) {
        prefs.edit { putBoolean(provideLocationKey(nodeNum), value) }
        provideNodeLocationFlows[nodeNum]?.tryEmit(value)
    }

    private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"
}
