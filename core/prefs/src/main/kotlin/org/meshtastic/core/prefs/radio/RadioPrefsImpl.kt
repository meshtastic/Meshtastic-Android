/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.prefs.radio

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.RadioSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.RadioPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioPrefsImpl
@Inject
constructor(
    @RadioSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : RadioPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val devAddr: StateFlow<String?> =
        prefs
            .preferenceFlow("devAddr2") { p, k -> p.getString(k, null) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getString("devAddr2", null))

    override fun setDevAddr(address: String?) {
        prefs.edit { putString("devAddr2", address) }
    }
}
