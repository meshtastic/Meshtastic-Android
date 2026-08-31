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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adapts a real `DataStore<Preferences>` (android/jvm/iOS only — `androidx.datastore.preferences` has no wasmJs
 * variant) to the platform-neutral [PrefsStore]. Every `CorePrefsAndroidModule`/`DesktopPlatformModule` call site that
 * used to hand a `DataStore<Preferences>` straight to an `asXDataStore()` wrapper now inserts this adapter first:
 * `store(...).asPrefsStore().asXDataStore()`.
 */
fun DataStore<Preferences>.asPrefsStore(): PrefsStore = DataStorePrefsStore(this)

private class DataStorePrefsStore(private val delegate: DataStore<Preferences>) : PrefsStore {
    override val data: Flow<PrefsSnapshot> = delegate.data.map { DataStorePrefsSnapshot(it) }

    override suspend fun edit(transform: (PrefsSnapshot.Editor) -> Unit) {
        delegate.edit { prefs -> transform(DataStorePrefsEditor(prefs)) }
    }
}

private class DataStorePrefsSnapshot(private val prefs: Preferences) : PrefsSnapshot {
    override fun <T> get(key: PrefsKey<T>): T? = prefs[key.toPreferencesKey()]

    override fun contains(key: PrefsKey<*>): Boolean = key.toRawPreferencesKey() in prefs
}

private class DataStorePrefsEditor(private val prefs: MutablePreferences) : PrefsSnapshot.Editor {
    override fun <T> get(key: PrefsKey<T>): T? = prefs[key.toPreferencesKey()]

    override fun contains(key: PrefsKey<*>): Boolean = key.toRawPreferencesKey() in prefs

    override fun <T> set(key: PrefsKey<T>, value: T) {
        prefs[key.toPreferencesKey()] = value
    }

    override fun <T> remove(key: PrefsKey<T>) {
        prefs.remove(key.toPreferencesKey())
    }
}

private fun PrefsKey<*>.toRawPreferencesKey(): Preferences.Key<*> = when (type) {
    PrefsKeyType.BOOLEAN -> booleanPreferencesKey(name)
    PrefsKeyType.INT -> intPreferencesKey(name)
    PrefsKeyType.LONG -> longPreferencesKey(name)
    PrefsKeyType.DOUBLE -> doublePreferencesKey(name)
    PrefsKeyType.STRING -> stringPreferencesKey(name)
    PrefsKeyType.STRING_SET -> stringSetPreferencesKey(name)
}

@Suppress("UNCHECKED_CAST")
private fun <T> PrefsKey<T>.toPreferencesKey(): Preferences.Key<T> = toRawPreferencesKey() as Preferences.Key<T>
