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

package org.meshtastic.core.prefs.mesh

import android.content.SharedPreferences
import androidx.core.content.edit
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.di.MeshSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

interface MeshPrefs {
    var deviceAddress: String?

    fun shouldProvideNodeLocation(nodeNum: Int?): Boolean

    fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean)
}

@Singleton
class MeshPrefsImpl @Inject constructor(@MeshSharedPreferences private val prefs: SharedPreferences) : MeshPrefs {
    override var deviceAddress: String? by NullableStringPrefDelegate(prefs, "device_address", NO_DEVICE_SELECTED)

    override fun shouldProvideNodeLocation(nodeNum: Int?): Boolean =
        prefs.getBoolean(provideLocationKey(nodeNum), false)

    override fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean) {
        prefs.edit { putBoolean(provideLocationKey(nodeNum), value) }
    }

    private fun provideLocationKey(nodeNum: Int?) = "provide-location-$nodeNum"
}

private const val NO_DEVICE_SELECTED = "n"
