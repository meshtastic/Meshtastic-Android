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
package org.meshtastic.core.prefs.nodedisplay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.NodeDisplayNameDataStore
import org.meshtastic.core.repository.NodeDisplayNamePrefs
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_NODE_DISPLAY_NAMES = "node_display_names"
private const val ENTRY_SEP = '\u0002'
private const val KV_SEP = '\u0001'

@Singleton
class NodeDisplayNamePrefsImpl
@Inject
constructor(
    @NodeDisplayNameDataStore private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : NodeDisplayNamePrefs {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val key = stringPreferencesKey(KEY_NODE_DISPLAY_NAMES)

    override val displayNames: StateFlow<Map<Int, String>> =
        dataStore.data
            .map { prefs ->
                val raw = prefs[key] ?: return@map emptyMap<Int, String>()
                parseMap(raw)
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun setDisplayName(nodeNum: Int, name: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = prefs[key]?.let { parseMap(it) } ?: emptyMap()
                val next =
                    if (name.isNullOrBlank()) {
                        current - nodeNum
                    } else {
                        current + (nodeNum to name.trim())
                    }
                prefs[key] = encodeMap(next)
            }
        }
    }

    private fun encodeValue(value: String): String =
        value.replace("$", "$E").replace(KV_SEP, "$K").replace(ENTRY_SEP, "$V")

    private fun decodeValue(value: String): String =
        value.replace("$V", ENTRY_SEP.toString()).replace("$K", KV_SEP.toString()).replace("$E", "$")

    private fun encodeMap(map: Map<Int, String>): String =
        map.entries
            .joinToString(ENTRY_SEP.toString()) { (num, value) ->
                "$num$KV_SEP${encodeValue(value)}"
            }

    private fun parseMap(raw: String): Map<Int, String> {
        if (raw.isEmpty()) return emptyMap()
        return buildMap {
            raw.split(ENTRY_SEP).forEach { entry ->
                val idx = entry.indexOf(KV_SEP)
                if (idx > 0) {
                    val num = entry.substring(0, idx).toIntOrNull() ?: return@forEach
                    val value = decodeValue(entry.substring(idx + 1))
                    put(num, value)
                }
            }
        }
    }
}
