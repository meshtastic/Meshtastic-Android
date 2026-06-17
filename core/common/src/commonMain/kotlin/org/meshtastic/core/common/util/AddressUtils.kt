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
package org.meshtastic.core.common.util

/**
 * Normalizes a BLE/device address to a canonical uppercase form with colons removed. Returns `"DEFAULT"` for null,
 * blank, or sentinel values (`"N"`, `"NULL"`).
 */
fun normalizeAddress(addr: String?): String {
    val u = addr?.trim()?.uppercase()
    return when {
        u.isNullOrBlank() -> "DEFAULT"
        u == "N" || u == "NULL" -> "DEFAULT"
        else -> u.replace(":", "")
    }
}

/**
 * True iff [normalized] (the output of [normalizeAddress]) is a no-device sentinel that DB naming collapses to the
 * default database. Single source of truth for "no real device selected".
 */
fun isNoDeviceSentinel(normalized: String): Boolean = normalized == "DEFAULT" || normalized == ".N"

/**
 * True iff [address] refers to a real selected device. Rejects null/blank and all legacy no-device sentinels (`"n"`,
 * `"null"`, `".n"`, `"default"`, case-insensitive) by delegating to [normalizeAddress] and [isNoDeviceSentinel]. Any
 * input that [buildDbName] would collapse to `DEFAULT_DB_NAME` is rejected here too, so the foreground-service
 * stay-alive decision and the DB name resolution can never diverge.
 */
fun isValidDeviceAddress(address: String?): Boolean = !isNoDeviceSentinel(normalizeAddress(address))
