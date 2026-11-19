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

/**
 * The two members of `androidx.datastore.core.DataStore`, which publishes no wasmJs variant. nonWebMain's `asStore()`
 * wraps a real `DataStore`; wasmJsMain's `LocalStorageStore` keeps base64 proto bytes in `localStorage`. The transform
 * is not suspending, so no suspension point can fall between a read and its write.
 */
interface Store<T> {
    val data: Flow<T>

    suspend fun updateData(transform: (T) -> T): T
}
