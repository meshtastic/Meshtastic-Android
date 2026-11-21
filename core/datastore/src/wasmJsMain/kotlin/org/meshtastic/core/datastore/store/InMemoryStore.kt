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
package org.meshtastic.core.datastore.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Keeps the value for the lifetime of the page and writes it nowhere, so a reload starts from [defaultValue]. The radio
 * sends the same data again on every connect.
 */
internal class InMemoryStore<T>(defaultValue: T) : Store<T> {
    private val state = MutableStateFlow(defaultValue)

    override val data: Flow<T> = state

    override suspend fun updateData(transform: (T) -> T): T = state.updateAndGet(transform)
}
