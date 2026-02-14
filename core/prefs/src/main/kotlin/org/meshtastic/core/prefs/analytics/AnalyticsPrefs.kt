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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.di.AnalyticsSharedPreferences
import org.meshtastic.core.prefs.di.AppSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

/** Interface for managing analytics-related preferences. */
interface AnalyticsPrefs {
    /** Preference for whether analytics collection is allowed by the user. */
    var analyticsAllowed: Boolean

    /**
     * Provides a [Flow] that emits the current state of [analyticsAllowed] and subsequent changes.
     *
     * @return A [Flow] of [Boolean] indicating if analytics are allowed.
     */
    fun getAnalyticsAllowedChangesFlow(): Flow<Boolean>

    /** Unique installation ID for analytics purposes. */
    val installId: String

    companion object {
        /** Key for the analyticsAllowed preference. */
        const val KEY_ANALYTICS_ALLOWED = "allowed"

        /** Name of the SharedPreferences file where analytics preferences are stored. */
        const val ANALYTICS_PREFS_NAME = "analytics-prefs"
    }
}

@Singleton
class AnalyticsPrefsImpl
@Inject
constructor(
    @AnalyticsSharedPreferences private val analyticsSharedPreferences: SharedPreferences,
    @AppSharedPreferences appPrefs: SharedPreferences,
) : AnalyticsPrefs {
    override var analyticsAllowed: Boolean by
        PrefDelegate(analyticsSharedPreferences, AnalyticsPrefs.KEY_ANALYTICS_ALLOWED, true)

    private var _installId: String? by NullableStringPrefDelegate(appPrefs, "appPrefs_install_id", null)

    override val installId: String
        get() = _installId ?: Uuid.random().toString().also { _installId = it }

    override fun getAnalyticsAllowedChangesFlow(): Flow<Boolean> = callbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == AnalyticsPrefs.KEY_ANALYTICS_ALLOWED) {
                    trySend(analyticsAllowed)
                }
            }
        // Emit the initial value
        trySend(analyticsAllowed)
        analyticsSharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { analyticsSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
