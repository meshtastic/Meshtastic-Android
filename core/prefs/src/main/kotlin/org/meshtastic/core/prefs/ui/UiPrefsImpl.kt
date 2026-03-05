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
package org.meshtastic.core.prefs.ui

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.UiSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.UiPrefs
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPrefsImpl
@Inject
constructor(
    @UiSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : UiPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    // Maps nodeNum to a flow for the for the "provide-location-nodeNum" pref
    private val provideNodeLocationFlows = ConcurrentHashMap<Int, StateFlow<Boolean>>()

    override val hasShownNotPairedWarning: StateFlow<Boolean> =
        prefs
            .preferenceFlow("has_shown_not_paired_warning") { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean("has_shown_not_paired_warning", false))

    override fun setHasShownNotPairedWarning(value: Boolean) {
        prefs.edit { putBoolean("has_shown_not_paired_warning", value) }
    }

    override val showQuickChat: StateFlow<Boolean> =
        prefs
            .preferenceFlow("show-quick-chat") { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean("show-quick-chat", false))

    override fun setShowQuickChat(value: Boolean) {
        prefs.edit { putBoolean("show-quick-chat", value) }
    }

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        provideNodeLocationFlows.getOrPut(nodeNum) {
            val key = provideLocationKey(nodeNum)
            prefs
                .preferenceFlow(key) { p, k -> p.getBoolean(k, false) }
                .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(key, false))
        }

    override fun setShouldProvideNodeLocation(nodeNum: Int, value: Boolean) {
        prefs.edit { putBoolean(provideLocationKey(nodeNum), value) }
    }

    private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"
}
