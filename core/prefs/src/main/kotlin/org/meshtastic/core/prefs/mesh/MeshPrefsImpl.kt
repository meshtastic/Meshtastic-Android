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
package org.meshtastic.core.prefs.mesh

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MeshSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.MeshPrefs
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshPrefsImpl
@Inject
constructor(
    @MeshSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : MeshPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val locationFlows = ConcurrentHashMap<Int?, StateFlow<Boolean>>()
    private val storeForwardFlows = ConcurrentHashMap<String?, StateFlow<Int>>()

    override val deviceAddress: StateFlow<String?> =
        prefs
            .preferenceFlow("device_address") { p, k -> p.getString(k, NO_DEVICE_SELECTED) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getString("device_address", NO_DEVICE_SELECTED))

    override fun setDeviceAddress(address: String?) {
        prefs.edit { putString("device_address", address) }
    }

    override fun shouldProvideNodeLocation(nodeNum: Int?): StateFlow<Boolean> = locationFlows.getOrPut(nodeNum) {
        val key = provideLocationKey(nodeNum)
        prefs
            .preferenceFlow(key) { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(key, false))
    }

    override fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean) {
        prefs.edit { putBoolean(provideLocationKey(nodeNum), value) }
    }

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> = storeForwardFlows.getOrPut(address) {
        val key = storeForwardKey(address)
        prefs
            .preferenceFlow(key) { p, k -> p.getInt(k, 0) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getInt(key, 0))
    }

    override fun setStoreForwardLastRequest(address: String?, value: Int) {
        prefs.edit {
            if (value <= 0) {
                remove(storeForwardKey(address))
            } else {
                putInt(storeForwardKey(address), value)
            }
        }
    }

    private fun provideLocationKey(nodeNum: Int?) = "provide-location-$nodeNum"

    private fun storeForwardKey(address: String?): String = "store-forward-last-request-${normalizeAddress(address)}"

    private fun normalizeAddress(address: String?): String {
        val raw = address?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            raw == null -> "DEFAULT"
            raw.equals(NO_DEVICE_SELECTED, ignoreCase = true) -> "DEFAULT"
            else -> raw.uppercase(Locale.US).replace(":", "")
        }
    }
}

private const val NO_DEVICE_SELECTED = "n"
