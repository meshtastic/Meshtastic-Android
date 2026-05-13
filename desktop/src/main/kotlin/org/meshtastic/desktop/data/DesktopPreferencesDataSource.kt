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
package org.meshtastic.desktop.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

/**
 * Persists and restores desktop window geometry (position and size) across application restarts.
 *
 * Backed by the `CorePreferencesDataStore` [DataStore] instance. Window bounds are written atomically via
 * [setWindowBounds] and exposed as [StateFlow] properties for composable consumption.
 */
@Single
class DesktopPreferencesDataSource(
    @Named("CorePreferencesDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    val windowWidth: StateFlow<Float> = dataStore.prefStateFlow(key = WINDOW_WIDTH, default = 1024f)
    val windowHeight: StateFlow<Float> = dataStore.prefStateFlow(key = WINDOW_HEIGHT, default = 768f)
    val windowX: StateFlow<Float> = dataStore.prefStateFlow(key = WINDOW_X, default = Float.NaN)
    val windowY: StateFlow<Float> = dataStore.prefStateFlow(key = WINDOW_Y, default = Float.NaN)

    fun setWindowBounds(width: Float, height: Float, x: Float, y: Float) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[WINDOW_WIDTH] = width
                prefs[WINDOW_HEIGHT] = height
                prefs[WINDOW_X] = x
                prefs[WINDOW_Y] = y
            }
        }
    }

    private fun <T : Any> DataStore<Preferences>.prefStateFlow(
        key: Preferences.Key<T>,
        default: T,
        started: SharingStarted = SharingStarted.Lazily,
    ): StateFlow<T> = data.map { it[key] ?: default }.stateIn(scope = scope, started = started, initialValue = default)

    companion object {
        val WINDOW_WIDTH = floatPreferencesKey("window_width")
        val WINDOW_HEIGHT = floatPreferencesKey("window_height")
        val WINDOW_X = floatPreferencesKey("window_x")
        val WINDOW_Y = floatPreferencesKey("window_y")
    }
}
