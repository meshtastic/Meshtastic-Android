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
package org.meshtastic.core.prefs.ui

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.meshtastic.core.model.NodeListDensity

/**
 * DataStore preference keys for node list layout configuration. Key strings are used directly by DataStore — do not
 * change without migration.
 */
enum class NodeListLayoutPreferences(val key: String, val defaultBoolean: Boolean = true) {
    SHOULD_SHOW_POWER("node-layout-show-power", defaultBoolean = true),
    SHOULD_SHOW_LAST_HEARD("node-layout-show-last-heard", defaultBoolean = true),
    LAST_HEARD_IS_RELATIVE("node-layout-last-heard-relative", defaultBoolean = false),
    SHOULD_SHOW_LOCATION("node-layout-show-location", defaultBoolean = true),
    SHOULD_SHOW_HOPS("node-layout-show-hops", defaultBoolean = true),
    SHOULD_SHOW_SIGNAL("node-layout-show-signal", defaultBoolean = true),
    SHOULD_SHOW_CHANNEL("node-layout-show-channel", defaultBoolean = true),
    SHOULD_SHOW_ROLE("node-layout-show-role", defaultBoolean = true),
    SHOULD_SHOW_TELEMETRY("node-layout-show-telemetry", defaultBoolean = true),
    ;

    companion object {
        private const val DENSITY_KEY = "node-list-density"
        val DEFAULT_DENSITY = NodeListDensity.COMPLETE.name

        val KEY_DENSITY = stringPreferencesKey(DENSITY_KEY)
        val KEY_SHOW_POWER = booleanPreferencesKey(SHOULD_SHOW_POWER.key)
        val KEY_SHOW_LAST_HEARD = booleanPreferencesKey(SHOULD_SHOW_LAST_HEARD.key)
        val KEY_LAST_HEARD_RELATIVE = booleanPreferencesKey(LAST_HEARD_IS_RELATIVE.key)
        val KEY_SHOW_LOCATION = booleanPreferencesKey(SHOULD_SHOW_LOCATION.key)
        val KEY_SHOW_HOPS = booleanPreferencesKey(SHOULD_SHOW_HOPS.key)
        val KEY_SHOW_SIGNAL = booleanPreferencesKey(SHOULD_SHOW_SIGNAL.key)
        val KEY_SHOW_CHANNEL = booleanPreferencesKey(SHOULD_SHOW_CHANNEL.key)
        val KEY_SHOW_ROLE = booleanPreferencesKey(SHOULD_SHOW_ROLE.key)
        val KEY_SHOW_TELEMETRY = booleanPreferencesKey(SHOULD_SHOW_TELEMETRY.key)
    }
}
