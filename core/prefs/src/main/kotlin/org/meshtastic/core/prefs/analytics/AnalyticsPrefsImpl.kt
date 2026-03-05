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
package org.meshtastic.core.prefs.analytics

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.AnalyticsSharedPreferences
import org.meshtastic.core.prefs.di.AppSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.AnalyticsPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
class AnalyticsPrefsImpl
@Inject
constructor(
    @AnalyticsSharedPreferences private val analyticsSharedPreferences: SharedPreferences,
    @AppSharedPreferences private val appPrefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : AnalyticsPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val analyticsAllowed: StateFlow<Boolean> =
        analyticsSharedPreferences
            .preferenceFlow(KEY_ANALYTICS_ALLOWED) { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, analyticsSharedPreferences.getBoolean(KEY_ANALYTICS_ALLOWED, false))

    override fun setAnalyticsAllowed(allowed: Boolean) {
        analyticsSharedPreferences.edit { putBoolean(KEY_ANALYTICS_ALLOWED, allowed) }
    }

    private var _installId: String?
        get() = appPrefs.getString(KEY_INSTALL_ID, null)
        set(value) = appPrefs.edit { putString(KEY_INSTALL_ID, value) }

    override val installId: String
        get() = _installId ?: Uuid.random().toString().also { _installId = it }

    companion object {
        const val KEY_ANALYTICS_ALLOWED = "allowed"
        const val KEY_INSTALL_ID = "appPrefs_install_id"
    }
}
