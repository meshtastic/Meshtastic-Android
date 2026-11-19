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

/** The value shapes a [PrefsKey] can address; only the two [PrefsStore] implementations switch on it. */
internal enum class PrefsKeyType {
    BOOLEAN,
    INT,
    LONG,
    DOUBLE,
    STRING,
    STRING_SET,
}

/**
 * A platform-neutral key into a [PrefsStore], mirroring `Preferences.Key`. Equal by [name], so a key built fresh for
 * the same name addresses the same value.
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

    /** Mutable view inside [PrefsStore.edit]; reads see values written earlier in the same transform. */
    interface Editor : PrefsSnapshot {
        operator fun <T> set(key: PrefsKey<T>, value: T)

        fun <T> remove(key: PrefsKey<T>)
    }
}

/**
 * The part of `DataStore<Preferences>` the prefs use; `androidx.datastore.preferences` publishes no wasmJs variant.
 * nonWebMain's `DataStorePrefsStore` wraps a real `DataStore`, wasmJsMain's `LocalStoragePrefsStore` uses
 * `localStorage`. The transform is not suspending, so no suspension point can fall between a read and its write.
 */
interface PrefsStore {
    val data: Flow<PrefsSnapshot>

    suspend fun edit(transform: (PrefsSnapshot.Editor) -> Unit)
}
