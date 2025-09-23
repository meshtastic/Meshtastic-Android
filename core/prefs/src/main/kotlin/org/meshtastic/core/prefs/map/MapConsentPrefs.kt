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

package org.meshtastic.core.prefs.map

import android.content.SharedPreferences
import androidx.core.content.edit
import org.meshtastic.core.prefs.di.MapConsentSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

interface MapConsentPrefs {
    fun shouldReportLocation(nodeNum: Int?): Boolean

    fun setShouldReportLocation(nodeNum: Int?, value: Boolean)
}

@Singleton
class MapConsentPrefsImpl @Inject constructor(@MapConsentSharedPreferences private val prefs: SharedPreferences) :
    MapConsentPrefs {
    override fun shouldReportLocation(nodeNum: Int?) = prefs.getBoolean(nodeNum.toString(), false)

    override fun setShouldReportLocation(nodeNum: Int?, value: Boolean) {
        prefs.edit { putBoolean(nodeNum.toString(), value) }
    }
}
