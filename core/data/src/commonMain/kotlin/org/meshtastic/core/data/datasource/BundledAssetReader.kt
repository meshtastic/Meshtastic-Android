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
package org.meshtastic.core.data.datasource

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okio.Source
import okio.buffer

/**
 * Reads a bundled JSON asset by file name. Android serves these from `assets/`; non-Android targets have no bundled
 * assets and return `null`, which callers treat as "nothing to seed".
 */
fun interface BundledAssetReader {
    /** Opens [name] (e.g. `"device_links.json"`), or returns `null` if the asset is absent. */
    fun open(name: String): Source?
}

/** Decodes bundled asset [name] to [T] using the shared tolerant [json], or `null` if the asset is absent. */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> BundledAssetReader.decode(name: String, json: Json): T? {
    // okio.Source doesn't extend AutoCloseable in commonMain, so close explicitly instead of `use {}`.
    val source = (open(name) ?: return null).buffer()
    return try {
        json.decodeFromBufferedSource(source)
    } finally {
        source.close()
    }
}
