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
package org.meshtastic.core.prefs.notification

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs

@Single
class LiveUpdatePrefsImpl(private val dataStore: UiDataStore, private val scope: ApplicationCoroutineScope) :
    LiveUpdatePrefs {

    override val mode: StateFlow<LiveUpdateMode> =
        dataStore.data
            .map { LiveUpdateMode.decode(it[KEY_LIVE_UPDATE_MODE]) }
            .stateIn(scope, SharingStarted.Eagerly, LiveUpdateMode.Off)

    override fun setMode(mode: LiveUpdateMode) {
        scope.launch { dataStore.edit { it[KEY_LIVE_UPDATE_MODE] = LiveUpdateMode.encode(mode) } }
    }

    private companion object {
        val KEY_LIVE_UPDATE_MODE = stringPreferencesKey("live_update_mode")
    }
}
