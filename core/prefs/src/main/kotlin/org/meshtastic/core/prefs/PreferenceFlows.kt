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
package org.meshtastic.core.prefs

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/** Creates a [Flow] that emits the value of a [SharedPreferences] key whenever it changes. */
internal fun <T> SharedPreferences.preferenceFlow(key: String, getValue: (SharedPreferences, String) -> T): Flow<T> =
    callbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
                if (key == changedKey) {
                    trySend(getValue(prefs, key))
                }
            }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .onStart { emit(getValue(this@preferenceFlow, key)) }
