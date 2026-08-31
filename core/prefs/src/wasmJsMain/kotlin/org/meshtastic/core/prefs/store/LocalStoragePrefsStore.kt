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

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Backed by the browser's `localStorage` — a synchronous, built-in key-value string store, so (unlike core:database's
 * OPFS story) no Worker or npm dependency is needed here at all.
 *
 * Every domain's store gets its own [namespace] prefix on every localStorage key, matching the file-per-domain
 * isolation the real `DataStore<Preferences>` instances get on android/jvm/iOS (see CorePrefsAndroidModule /
 * DesktopPlatformModule's per-domain `fileName`).
 *
 * Reads are live: [LocalStorageSnapshot] holds no cached state, it re-reads `localStorage` on every
 * [PrefsSnapshot.get]/[PrefsSnapshot.contains] call. [revision] exists purely to give [data] a *new* emission after
 * every [edit] — `LocalStorageSnapshot` has no `equals` override, so each freshly-constructed instance is unequal by
 * reference to the last, which is exactly what [MutableStateFlow] needs to avoid conflating the update away.
 *
 * [DEFERRED]: same-tab writes are reflected immediately (every `edit()` publishes a new revision, and every read goes
 * straight to localStorage), but a write from another browser tab is not observed — that would need a
 * `window.onstorage` listener re-publishing a revision when this namespace's keys change, not attempted in this pass.
 */
internal class LocalStoragePrefsStore(private val namespace: String) : PrefsStore {
    private val revision = MutableStateFlow(LocalStorageSnapshot(namespace))

    override val data: Flow<PrefsSnapshot> = revision

    override suspend fun edit(transform: (PrefsSnapshot.Editor) -> Unit) {
        transform(LocalStorageEditor(namespace))
        revision.value = LocalStorageSnapshot(namespace)
    }
}

private class LocalStorageSnapshot(private val namespace: String) : PrefsSnapshot {
    override fun <T> get(key: PrefsKey<T>): T? = decode(localStorage.getItem(storageKey(namespace, key)), key.type)

    override fun contains(key: PrefsKey<*>): Boolean = localStorage.getItem(storageKey(namespace, key)) != null
}

private class LocalStorageEditor(private val namespace: String) : PrefsSnapshot.Editor {
    override fun <T> get(key: PrefsKey<T>): T? = decode(localStorage.getItem(storageKey(namespace, key)), key.type)

    override fun contains(key: PrefsKey<*>): Boolean = localStorage.getItem(storageKey(namespace, key)) != null

    override fun <T> set(key: PrefsKey<T>, value: T) {
        localStorage.setItem(storageKey(namespace, key), encode(value, key.type))
    }

    override fun <T> remove(key: PrefsKey<T>) {
        localStorage.removeItem(storageKey(namespace, key))
    }
}

private fun storageKey(namespace: String, key: PrefsKey<*>): String = "$namespace:${key.name}"

// Matches the '|'-joined convention UiPrefsImpl's own firmwareUpdateNotificationKeys already uses for a
// Set<String>-shaped preference — reusing it here rather than inventing a new serialization format. As there, a set
// element containing '|' itself would corrupt round-tripping; no current preference stores one.
private const val SET_DELIMITER = "|"

@Suppress("UNCHECKED_CAST")
private fun <T> decode(raw: String?, type: PrefsKeyType): T? {
    if (raw == null) return null
    val value: Any =
        when (type) {
            PrefsKeyType.BOOLEAN -> raw.toBooleanStrictOrNull() ?: return null
            PrefsKeyType.INT -> raw.toIntOrNull() ?: return null
            PrefsKeyType.LONG -> raw.toLongOrNull() ?: return null
            PrefsKeyType.DOUBLE -> raw.toDoubleOrNull() ?: return null
            PrefsKeyType.STRING -> raw
            PrefsKeyType.STRING_SET -> raw.split(SET_DELIMITER).filter(String::isNotEmpty).toSet()
        }
    return value as T
}

@Suppress("UNCHECKED_CAST")
private fun <T> encode(value: T, type: PrefsKeyType): String = when (type) {
    PrefsKeyType.STRING_SET -> (value as Set<String>).joinToString(SET_DELIMITER)
    else -> value.toString()
}
