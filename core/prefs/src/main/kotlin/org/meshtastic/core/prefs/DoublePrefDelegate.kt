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
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DoublePrefDelegate(
    private val preferences: SharedPreferences,
    private val key: String,
    private val defaultValue: Double,
) : ReadWriteProperty<Any?, Double> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Double = preferences
        .getFloat(key, defaultValue.toFloat())
        .toDouble() // SharedPreferences doesn't have putDouble, so convert to float

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
        preferences
            .edit()
            .putFloat(key, value.toFloat())
            .apply() // SharedPreferences doesn't have putDouble, so convert to float
    }
}
