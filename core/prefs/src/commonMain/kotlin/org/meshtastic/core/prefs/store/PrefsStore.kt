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
package org.meshtastic.core.prefs.store

import kotlinx.coroutines.flow.Flow

/**
 * The value shapes a [PrefsKey] can address — every case a real preference in this module actually uses. `internal`:
 * only the two platform-specific [PrefsStore] implementations (nonWebMain's `DataStorePrefsStore`, wasmJsMain's
 * `LocalStoragePrefsStore`) ever need to switch on it; every `*PrefsImpl` consumer only ever calls
 * [PrefsSnapshot.get]/[PrefsSnapshot.Editor.set] generically and never inspects a key's shape.
 */
internal enum class PrefsKeyType {
    BOOLEAN,
    INT,
    LONG,
    DOUBLE,
    STRING,
    STRING_SET,
}

/**
 * A single, platform-neutral key into a [PrefsStore]. Mirrors `androidx.datastore.preferences.core.Preferences.Key`
 * closely enough that porting a `*PrefsImpl` off DataStore is a mechanical rename, not a rewrite: swap the
 * `androidx.datastore.preferences.core.*PreferencesKey` import/call for the matching `*PrefsKey` factory below, and
 * `Preferences`/`MutablePreferences` for [PrefsSnapshot]/[PrefsSnapshot.Editor].
 *
 * Equality/hashing is by [name] alone (matching `Preferences.Key`), so two keys constructed for the same name — even
 * from different call sites, e.g. a dynamic per-node key built fresh on every read and write — address the same
 * underlying value.
 */
class PrefsKey<T> internal constructor(internal val name: String, internal val type: PrefsKeyType) {
    override fun equals(other: Any?): Boolean = other is PrefsKey<*> && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name
}

fun booleanPrefsKey(name: String): PrefsKey<Boolean> = PrefsKey(name, PrefsKeyType.BOOLEAN)

fun intPrefsKey(name: String): PrefsKey<Int> = PrefsKey(name, PrefsKeyType.INT)

fun longPrefsKey(name: String): PrefsKey<Long> = PrefsKey(name, PrefsKeyType.LONG)

fun doublePrefsKey(name: String): PrefsKey<Double> = PrefsKey(name, PrefsKeyType.DOUBLE)

fun stringPrefsKey(name: String): PrefsKey<String> = PrefsKey(name, PrefsKeyType.STRING)

fun stringSetPrefsKey(name: String): PrefsKey<Set<String>> = PrefsKey(name, PrefsKeyType.STRING_SET)

/** Read-only view of a [PrefsStore]'s current values. Mirrors `androidx.datastore.preferences.core.Preferences`. */
interface PrefsSnapshot {
    operator fun <T> get(key: PrefsKey<T>): T?

    operator fun contains(key: PrefsKey<*>): Boolean

    /**
     * Mutable view passed to [PrefsStore.edit]'s transform block. Mirrors `MutablePreferences` — including reading back
     * values already written earlier in the same transform, which several `*PrefsImpl` read-modify-write call sites
     * rely on (e.g. bumping an insertion-ordered CSV, or defaulting a value only if absent).
     */
    interface Editor : PrefsSnapshot {
        operator fun <T> set(key: PrefsKey<T>, value: T)

        fun <T> remove(key: PrefsKey<T>)
    }
}

/**
 * Platform-neutral replacement for `DataStore<Preferences>`. `androidx.datastore.preferences` itself publishes no
 * wasmJs (or even plain JS) variant at any version — the `Preferences` type doesn't exist for that target — so this
 * module can't reference it from commonMain at all. Two implementations exist:
 * - nonWebMain's `DataStorePrefsStore` — a thin wrapper over a real `DataStore<Preferences>`; android/jvm/iOS keep
 *   using the real DataStore machinery underneath, unchanged.
 * - wasmJsMain's `LocalStoragePrefsStore` — backed by the browser's `localStorage`, a synchronous, built-in key-value
 *   string store. Unlike core:database's OPFS story, no Worker or npm dependency is needed here at all.
 *
 * `edit`'s [transform] is deliberately non-suspend (androidx's `DataStore.edit`'s is `suspend`) and returns `Unit`
 * (androidx's returns the resulting `Preferences`) — no call site in this module uses either capability, and keeping it
 * synchronous means no suspension point can interleave between a transform's reads and its writes on any platform.
 */
interface PrefsStore {
    val data: Flow<PrefsSnapshot>

    suspend fun edit(transform: (PrefsSnapshot.Editor) -> Unit)
}
