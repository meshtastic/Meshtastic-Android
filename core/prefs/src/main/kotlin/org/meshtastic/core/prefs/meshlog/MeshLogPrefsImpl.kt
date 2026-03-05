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
package org.meshtastic.core.prefs.meshlog

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MeshLogSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.MeshLogPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshLogPrefsImpl
@Inject
constructor(
    @MeshLogSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : MeshLogPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val retentionDays: StateFlow<Int> =
        prefs
            .preferenceFlow(RETENTION_DAYS_KEY) { p, k -> p.getInt(k, DEFAULT_RETENTION_DAYS) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getInt(RETENTION_DAYS_KEY, DEFAULT_RETENTION_DAYS))

    override fun setRetentionDays(days: Int) {
        prefs.edit { putInt(RETENTION_DAYS_KEY, days) }
    }

    override val loggingEnabled: StateFlow<Boolean> =
        prefs
            .preferenceFlow(LOGGING_ENABLED_KEY) { p, k -> p.getBoolean(k, DEFAULT_LOGGING_ENABLED) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(LOGGING_ENABLED_KEY, DEFAULT_LOGGING_ENABLED))

    override fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(LOGGING_ENABLED_KEY, enabled) }
    }

    companion object {
        const val RETENTION_DAYS_KEY = "meshlog_retention_days"
        const val LOGGING_ENABLED_KEY = "meshlog_logging_enabled"
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_LOGGING_ENABLED = true
    }
}
