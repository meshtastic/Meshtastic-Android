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

import co.touchlab.kermit.Logger
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Backed by the browser's `localStorage`. [decode]/[encode] run against an in-memory [Buffer] — it implements both
 * `BufferedSource` and `BufferedSink` purely in memory (no file, works on every target) — to get/put the value's raw
 * proto bytes, which are then [Base64]-encoded since `localStorage` only stores strings.
 *
 * Reads are live (no caching), matching `LocalStoragePrefsStore`: [revision] exists purely to give [data] a fresh
 * emission after every [updateData] — [Revision] has no `equals` override, so each freshly-constructed instance is
 * unequal by reference to the last, exactly what [MutableStateFlow] needs to avoid conflating the update away.
 *
 * Corruption policy mirrors this module's Android/JVM `ReplaceFileCorruptionHandler` (see
 * `CoreDatastoreAndroidModule`'s `protoStore`): no value yet (first launch) or a value that fails to decode both fall
 * back to [defaultValue]. A decode failure is also logged and the recovered default written back, so a corrupted read
 * is not repeated on every subsequent access — "first launch" is not logged, since it isn't corruption.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class LocalStorageStore<T>(
    private val storageKey: String,
    private val defaultValue: T,
    private val decode: (BufferedSource) -> T,
    private val encode: (T, BufferedSink) -> Unit,
) : Store<T> {
    private class Revision

    private val revision = MutableStateFlow(Revision())

    override val data: Flow<T> = revision.map { readCurrent() }

    override suspend fun updateData(transform: (T) -> T): T {
        val updated = transform(readCurrent())
        writeCurrent(updated)
        revision.value = Revision()
        return updated
    }

    // Heterogeneous failure modes (Base64's IllegalArgumentException, Wire/okio's various decode-time exceptions) are
    // deliberately all treated the same way: recover to defaultValue.
    @Suppress("TooGenericExceptionCaught")
    private fun readCurrent(): T {
        val raw = localStorage.getItem(storageKey) ?: return defaultValue
        return try {
            decode(Buffer().apply { write(Base64.Default.decode(raw)) })
        } catch (e: Exception) {
            Logger.w(e) { "Corrupt localStorage value for '$storageKey', resetting to default" }
            writeCurrent(defaultValue)
            defaultValue
        }
    }

    private fun writeCurrent(value: T) {
        val buffer = Buffer()
        encode(value, buffer)
        localStorage.setItem(storageKey, Base64.Default.encode(buffer.readByteArray()))
    }
}
