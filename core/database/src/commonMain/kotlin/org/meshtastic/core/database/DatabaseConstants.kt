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
package org.meshtastic.core.database

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.common.util.normalizeAddress

object DatabaseConstants {
    const val DB_PREFIX: String = "meshtastic_database"
    const val LEGACY_DB_NAME: String = DB_PREFIX
    const val DEFAULT_DB_NAME: String = "${DB_PREFIX}_default"

    const val CACHE_LIMIT_KEY: String = "node_db_cache_limit"
    const val DEFAULT_CACHE_LIMIT: Int = 3
    const val MIN_CACHE_LIMIT: Int = 1
    const val MAX_CACHE_LIMIT: Int = 10

    const val LEGACY_DB_CLEANED_KEY: String = "legacy_db_cleaned"

    // Display/truncation and hash sizing for DB names
    const val DB_NAME_HASH_LEN: Int = 10
    const val DB_NAME_SEPARATOR_LEN: Int = 1
    const val DB_NAME_SUFFIX_LEN: Int = 3

    // Address anonymization sizing
    const val ADDRESS_ANON_SHORT_LEN: Int = 4
    const val ADDRESS_ANON_EDGE_LEN: Int = 2
}

fun shortSha1(s: String): String = s.encodeUtf8().sha1().hex().take(DatabaseConstants.DB_NAME_HASH_LEN)

fun buildDbName(address: String?): String = if (address.isNullOrBlank()) {
    DatabaseConstants.DEFAULT_DB_NAME
} else {
    "${DatabaseConstants.DB_PREFIX}_${shortSha1(normalizeAddress(address))}"
}

fun anonymizeAddress(address: String?): String = when {
    address == null -> "null"

    address.length <= DatabaseConstants.ADDRESS_ANON_SHORT_LEN -> address

    else ->
        address.take(DatabaseConstants.ADDRESS_ANON_EDGE_LEN) +
            "…" +
            address.takeLast(DatabaseConstants.ADDRESS_ANON_EDGE_LEN)
}

fun anonymizeDbName(name: String): String =
    if (name == DatabaseConstants.LEGACY_DB_NAME || name == DatabaseConstants.DEFAULT_DB_NAME) {
        name
    } else {
        name.take(
            DatabaseConstants.DB_PREFIX.length +
                DatabaseConstants.DB_NAME_SEPARATOR_LEN +
                DatabaseConstants.DB_NAME_SUFFIX_LEN,
        ) + "…"
    }

/** Compute which DBs to evict using LRU policy. */
internal fun selectEvictionVictims(
    dbNames: List<String>,
    activeDbName: String,
    limit: Int,
    lastUsedMsByDb: Map<String, Long>,
): List<String> {
    val deviceDbNames =
        dbNames.filterNot { it == DatabaseConstants.LEGACY_DB_NAME || it == DatabaseConstants.DEFAULT_DB_NAME }
    val victims =
        if (limit < 1 || deviceDbNames.size <= limit) {
            emptyList()
        } else {
            val candidates = deviceDbNames.filter { it != activeDbName }
            if (candidates.isEmpty()) {
                emptyList()
            } else {
                val toEvict = deviceDbNames.size - limit
                candidates.sortedBy { lastUsedMsByDb[it] ?: 0L }.take(toEvict)
            }
        }
    return victims
}
