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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.squareup.wire.Message

/**
 * A state holder for managing config data within a Composable.
 *
 * This class encapsulates the common logic for handling editable state that is derived from an initial value. It tracks
 * whether the current value has been modified ("dirty"), and provides simple methods to save the changes or reset to
 * the initial state.
 *
 * @param T The type of the data being managed, typically a Wire message.
 * @property initialValue The original, unmodified value of the config data.
 */
class ConfigState<T : Message<T, *>>(private val initialValue: T) {
    var value by mutableStateOf(initialValue)

    val isDirty: Boolean
        get() = value != initialValue

    fun reset() {
        value = initialValue
    }

    companion object {
        fun <T : Message<T, *>> saver(initialValue: T): Saver<ConfigState<T>, ByteArray> = Saver(
            save = { it.value.adapter.encode(it.value) },
            restore = { ConfigState(initialValue).apply { value = initialValue.adapter.decode(it) } },
        )
    }
}

/**
 * Creates and remembers a [ConfigState] instance, correctly handling process death and recomposition. When the
 * `initialValue` changes, the config state will be reset.
 *
 * @param initialValue The initial value to populate the config with. The config will be reset if this value changes
 *   across recompositions.
 */
@Composable
fun <T : Message<T, *>> rememberConfigState(initialValue: T): ConfigState<T> =
    rememberSaveable(initialValue, saver = ConfigState.saver(initialValue)) { ConfigState(initialValue) }
