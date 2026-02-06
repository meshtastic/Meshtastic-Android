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
package org.meshtastic.core.prefs.homoglyph

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.di.HomoglyphEncodingSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

interface HomoglyphPrefs {

    /** Preference for whether homoglyph encoding is enabled by the user. */
    var homoglyphEncodingEnabled: Boolean

    /**
     * Provides a [Flow] that emits the current state of [homoglyphEncodingEnabled] and subsequent changes.
     *
     * @return A [Flow] of [Boolean] indicating if homoglyph encoding is enabled.
     */
    fun getHomoglyphEncodingEnabledChangesFlow(): Flow<Boolean>

    companion object {
        /** Key for the homoglyphEncodingEnabled preference. */
        const val KEY_HOMOGLYPH_ENCODING_ENABLED = "enabled"
    }
}

@Singleton
class HomoglyphPrefsImpl
@Inject
constructor(
    @HomoglyphEncodingSharedPreferences private val homoglyphEncodingSharedPreferences: SharedPreferences,
) : HomoglyphPrefs {
    override var homoglyphEncodingEnabled: Boolean by
        PrefDelegate(homoglyphEncodingSharedPreferences, HomoglyphPrefs.KEY_HOMOGLYPH_ENCODING_ENABLED, false)

    override fun getHomoglyphEncodingEnabledChangesFlow(): Flow<Boolean> = callbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == HomoglyphPrefs.KEY_HOMOGLYPH_ENCODING_ENABLED) {
                    trySend(homoglyphEncodingEnabled)
                }
            }
        // Emit the initial value
        trySend(homoglyphEncodingEnabled)
        homoglyphEncodingSharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { homoglyphEncodingSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
