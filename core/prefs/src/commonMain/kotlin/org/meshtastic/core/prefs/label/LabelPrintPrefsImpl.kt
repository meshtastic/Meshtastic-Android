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
package org.meshtastic.core.prefs.label

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.prefs.store.intPrefsKey
import org.meshtastic.core.prefs.store.stringPrefsKey
import org.meshtastic.core.repository.LabelPrintPrefs

@Single
class LabelPrintPrefsImpl(private val dataStore: UiDataStore, private val scope: ApplicationCoroutineScope) :
    LabelPrintPrefs {

    override val labelLengthMm: StateFlow<Int> =
        dataStore.data
            .map { it[KEY_LENGTH_MM] ?: LabelPrintPrefs.DEFAULT_LABEL_LENGTH_MM }
            .stateIn(scope, SharingStarted.Eagerly, LabelPrintPrefs.DEFAULT_LABEL_LENGTH_MM)

    override fun setLabelLengthMm(millimeters: Int) {
        scope.launch { dataStore.edit { it[KEY_LENGTH_MM] = millimeters } }
    }

    override val printerAddress: StateFlow<String?> =
        dataStore.data.map { it[KEY_PRINTER_ADDRESS] }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun setPrinterAddress(address: String?) {
        scope.launch {
            dataStore.edit {
                if (address == null) it.remove(KEY_PRINTER_ADDRESS) else it[KEY_PRINTER_ADDRESS] = address
            }
        }
    }

    private companion object {
        val KEY_LENGTH_MM = intPrefsKey("label-print-length-mm")
        val KEY_PRINTER_ADDRESS = stringPrefsKey("label-print-printer-address")
    }
}
