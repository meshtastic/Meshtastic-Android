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

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

/**
 * Adapts a real `DataStore<T>` (android/jvm/iOS only — `androidx.datastore:datastore` has no wasmJs variant) to the
 * platform-neutral [Store]. Every `CoreDatastoreAndroidModule`/`DesktopPlatformModule` call site that used to hand a
 * `DataStore<T>` straight to an `asCoreXDataStore()` wrapper now inserts this adapter first:
 * `protoStore(...).asStore().asCoreXDataStore()`.
 */
fun <T> DataStore<T>.asStore(): Store<T> = object : Store<T> {
    override val data: Flow<T> = this@asStore.data

    override suspend fun updateData(transform: (T) -> T): T = this@asStore.updateData(transform)
}
