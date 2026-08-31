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
 * Platform-neutral replacement for `androidx.datastore.core.DataStore<T>`, mirroring its two members exactly.
 * `androidx.datastore:datastore` (the core artifact providing `DataStore`/`OkioSerializer`/`CorruptionException`)
 * publishes no wasmJs variant at all — confirmed against its Gradle Module Metadata, the same absence
 * `androidx.datastore.preferences` hit for core:prefs (see `core/prefs/store/PrefsStore.kt`) — so this module's
 * proto-payload stores (ChannelSet/LocalConfig/LocalStats/LocalModuleConfig) can't reference the real type from
 * commonMain at all. Two implementations exist:
 * - nonWebMain's `asStore()` — a thin wrapper over a real `DataStore<T>`; android/jvm/iOS keep using the real DataStore
 *   machinery underneath, unchanged.
 * - wasmJsMain's `LocalStorageStore` — backed by the browser's `localStorage`, encoding each value's proto bytes as
 *   base64 (localStorage is string-only).
 *
 * Unlike core:prefs's `PrefsStore` (many keys under one store), each `Store<T>` here already holds one whole
 * serializable value, so no key/snapshot abstraction is needed — this interface is a direct, simpler analog.
 *
 * `updateData`'s [transform] is deliberately non-suspend (androidx's is `suspend`) — no DataSource in this module needs
 * suspension inside a transform, and a non-suspend lambda already satisfies a `suspend` function type, so nonWebMain's
 * real-DataStore adapter passes it straight through with no wrapping needed.
 */
interface Store<T> {
    val data: Flow<T>

    suspend fun updateData(transform: (T) -> T): T
}
