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
package org.meshtastic.core.ui.util

import android.content.Context

/**
 * Persists, per Android permission string, whether the app has ever completed a runtime request for it.
 *
 * This flag is the disambiguator required by [computePermissionStatus]: `shouldShowRequestPermissionRationale` returns
 * `false` both before the first prompt and after a permanent denial, so a persisted "has been requested" marker is the
 * only way to tell the two apart.
 *
 * Deliberately backed by [android.content.SharedPreferences] rather than DataStore: the flag is read synchronously
 * inside composition (in the same pass as the rationale check) and written synchronously from a permission-result
 * callback. DataStore's asynchronous `Flow` model would introduce a read-after-write race on exactly the transition the
 * permission state machine hinges on.
 */
internal class PermissionRequestTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasRequested(permission: String): Boolean = prefs.getBoolean(permission, false)

    /**
     * Marks [permission] as having completed a request. MUST be called from the launcher's result callback (after the
     * OS has adjudicated the request), never when `launch()` is merely invoked — see [computePermissionStatus].
     */
    fun markRequested(permission: String) {
        prefs.edit().putBoolean(permission, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "meshtastic_permissions"
    }
}
