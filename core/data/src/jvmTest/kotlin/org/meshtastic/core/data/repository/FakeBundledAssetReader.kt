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
package org.meshtastic.core.data.repository

import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader

/** [BundledAssetReader] over in-memory asset bodies keyed by file name; a name with no body is an absent asset. */
internal class FakeBundledAssetReader : BundledAssetReader {
    private val assets = mutableMapOf<String, String>()

    /** How many reads throw before one succeeds, as an unreadable asset would. */
    var failuresBeforeSuccess = 0

    fun put(name: String, body: String) {
        assets[name] = body
    }

    inline fun <reified T> put(name: String, value: T, json: Json) = put(name, json.encodeToString(value))

    fun remove(name: String) {
        assets.remove(name)
    }

    override fun open(name: String): Source? {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess -= 1
            error("Bundled asset read failed")
        }
        return assets[name]?.let { Buffer().writeUtf8(it) }
    }
}
