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

package com.geeksville.mesh.android.prefs

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.geeksville.mesh.model.NodeSortOption
import com.geeksville.mesh.util.LanguageUtils

interface UiPrefs {
    var lang: String
    var theme: Int
    var appIntroCompleted: Boolean
    var hasShownNotPairedWarning: Boolean
    var nodeSortOption: Int
    var includeUnknown: Boolean
    var showDetails: Boolean
    var onlyOnline: Boolean
    var onlyDirect: Boolean
    var showIgnored: Boolean
    var showQuickChat: Boolean

    fun shouldProvideNodeLocation(nodeNum: Int?): Boolean

    fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean)
}

class UiPrefsImpl(private val prefs: SharedPreferences) : UiPrefs {
    override var lang: String by PrefDelegate(prefs, "lang", LanguageUtils.SYSTEM_DEFAULT)
    override var theme: Int by PrefDelegate(prefs, "theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    override var appIntroCompleted: Boolean by PrefDelegate(prefs, "app_intro_completed", false)
    override var hasShownNotPairedWarning: Boolean by PrefDelegate(prefs, "has_shown_not_paired_warning", false)
    override var nodeSortOption: Int by PrefDelegate(prefs, "node-sort-option", NodeSortOption.VIA_FAVORITE.ordinal)
    override var includeUnknown: Boolean by PrefDelegate(prefs, "include-unknown", false)
    override var showDetails: Boolean by PrefDelegate(prefs, "show-details", false)
    override var onlyOnline: Boolean by PrefDelegate(prefs, "only-online", false)
    override var onlyDirect: Boolean by PrefDelegate(prefs, "only-direct", false)
    override var showIgnored: Boolean by PrefDelegate(prefs, "show-ignored", false)
    override var showQuickChat: Boolean by PrefDelegate(prefs, "show-quick-chat", false)

    override fun shouldProvideNodeLocation(nodeNum: Int?): Boolean =
        prefs.getBoolean(provideLocationKey(nodeNum), false)

    override fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean) {
        prefs.edit { putBoolean(provideLocationKey(nodeNum), value) }
    }

    private fun provideLocationKey(nodeNum: Int?) = "provide-location-$nodeNum"
}
