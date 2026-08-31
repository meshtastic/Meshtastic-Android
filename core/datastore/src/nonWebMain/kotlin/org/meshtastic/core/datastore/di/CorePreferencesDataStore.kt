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
package org.meshtastic.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * `androidx.datastore.preferences`'s `Preferences` type has no wasmJs variant at all (the same absence `core:prefs` hit
 * — see `core/prefs/store/PrefsStore.kt`), so unlike the four proto-payload stores in `CoreDataStores.kt`, this one
 * cannot be ported behind the generic `Store<T>` abstraction: there is no way to construct a `Store<Preferences>` on
 * wasmJs, because the `Preferences` type itself doesn't resolve there — this is not a missing-adapter gap, it's the
 * payload type being unavailable. Stays directly typed against the real `DataStore<Preferences>`, android/jvm/iOS only.
 * Its three consumers — `RecentAddressesDataSource`, `BootloaderWarningDataSource`, `FirmwareRecoveryDataSource` — move
 * here with it, since they use `androidx.datastore.preferences`'s typed-key API directly. [DEFERRED]: a future pass
 * could give these three web support by rewriting them against core:prefs's own `PrefsStore`/`PrefsKey` abstraction
 * instead (which already solves "key-value settings on wasmJs" for the rest of the app) — not attempted here, out of
 * this module's scope.
 */
interface CorePreferencesDataStore : DataStore<Preferences>

/** Presents an existing store as [CorePreferencesDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asCorePreferencesDataStore(): CorePreferencesDataStore =
    object : CorePreferencesDataStore, DataStore<Preferences> by this {}
