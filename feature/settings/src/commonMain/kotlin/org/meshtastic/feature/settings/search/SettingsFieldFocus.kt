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
 * Each request carries the schema string key of the field (`schema_lora_hop_limit`) and an id unique to that selection.
 * The screen's list host scrolls to the item carrying the key and calls [consume]; a screen whose items carry no
 * matching key leaves the request pending until the host leaves composition, which [clear]s it.
 */
@Single
class SettingsFieldFocus {
    private val _pending = MutableStateFlow<FieldFocusRequest?>(null)
    val pending: StateFlow<FieldFocusRequest?> = _pending.asStateFlow()
    private var lastId = 0L

    fun request(fieldKey: String?) {
        _pending.value = fieldKey?.let { FieldFocusRequest(it, ++lastId) }
    }

    /**
     * Clears the pending request only if it is still [request], so a newer selection is not dropped by an older host.
     */
    fun consume(request: FieldFocusRequest) {
        _pending.compareAndSet(request, null)
    }

    /**
     * Clears the pending request only if it is still [request]; same guard as [consume], for a host that is leaving.
     */
    fun clear(request: FieldFocusRequest?) {
        if (request != null) consume(request)
    }
}

/** One selection of a search result: two selections of the same [key] differ by [id]. */
data class FieldFocusRequest(val key: String, val id: Long)

/** Provided around each config screen; null where no search can have named a control, such as previews. */
@Suppress("CompositionLocalAllowlist")
val LocalSettingsFieldFocus = compositionLocalOf<SettingsFieldFocus?> { null }
