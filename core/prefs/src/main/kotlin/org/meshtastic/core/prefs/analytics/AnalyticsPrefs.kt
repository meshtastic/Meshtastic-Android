/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.di.AnalyticsSharedPreferences
import org.meshtastic.core.prefs.di.AppSharedPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface AnalyticsPrefs {
    var analyticsAllowed: Boolean
    val installId: String
}

// Having an additional app prefs store is maintaining the existing behavior.
@Singleton
class AnalyticsPrefsImpl
@Inject
constructor(
    @AnalyticsSharedPreferences analyticsPrefs: SharedPreferences,
    @AppSharedPreferences appPrefs: SharedPreferences,
) : AnalyticsPrefs {
    override var analyticsAllowed: Boolean by PrefDelegate(analyticsPrefs, "allowed", true)

    private var _installId: String? by NullableStringPrefDelegate(appPrefs, "appPrefs_install_id", null)

    override val installId: String
        get() = _installId ?: UUID.randomUUID().toString().also { _installId = it }
}
