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
package org.meshtastic.feature.settings.search

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * The control a settings search result named, handed from the search field to the config screen it opened.
 *
 * The key is the schema string key of the field (`schema_lora_hop_limit`). The screen's list host scrolls to the item
 * carrying that key and calls [consume]; a screen whose items carry no matching key leaves the request pending until
 * the host leaves composition, which [clear]s it.
 */
@Single
class SettingsFieldFocus {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(fieldKey: String?) {
        _pending.value = fieldKey
    }

    /** Clears the request only if it is still [fieldKey], so a newer search result is not dropped by an older host. */
    fun consume(fieldKey: String) {
        if (_pending.value == fieldKey) _pending.value = null
    }

    fun clear() {
        _pending.value = null
    }
}

/** Provided around each config screen; null where no search can have named a control, such as previews. */
val LocalSettingsFieldFocus = compositionLocalOf<SettingsFieldFocus?> { null }
