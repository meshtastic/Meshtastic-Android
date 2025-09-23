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

package org.meshtastic.core.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class StringSetPrefDelegate(
    private val prefs: SharedPreferences,
    private val key: String,
    private val defaultValue: Set<String>,
) : ReadWriteProperty<Any?, Set<String>> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> =
        prefs.getStringSet(key, defaultValue) ?: emptySet()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) =
        prefs.edit { putStringSet(key, value) }
}
