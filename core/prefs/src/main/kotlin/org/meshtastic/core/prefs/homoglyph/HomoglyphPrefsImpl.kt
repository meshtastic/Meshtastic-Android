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
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.HomoglyphEncodingSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.HomoglyphPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomoglyphPrefsImpl
@Inject
constructor(
    @HomoglyphEncodingSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : HomoglyphPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val homoglyphEncodingEnabled: StateFlow<Boolean> =
        prefs
            .preferenceFlow(KEY_ENABLED) { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(KEY_ENABLED, false))

    override fun setHomoglyphEncodingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    companion object {
        const val KEY_ENABLED = "enabled"
    }
}
