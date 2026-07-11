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

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.database.entity.MyNodeEntity

/** Ingestion hex-encodes the 16-byte `device_id`; require that shape so lossy legacy values can't be compared. */
private val HEX_DEVICE_ID = Regex("[0-9a-fA-F]{16,64}")

/**
 * A device id usable for hardware-identity comparison; null when absent, blank, the legacy placeholder, or not in the
 * hex form current ingestion produces (older app versions persisted a lossy utf8 decode of the raw bytes — those values
 * can collide across devices, so they are treated as absent rather than compared). The id is the factory-burned silicon
 * identifier from `MyNodeInfo.device_id` — stable across firmware upgrades, erases, and key changes — but not reported
 * by all hardware (e.g. classic ESP32) and deliberately zeroed for unauthenticated clients in lockdown mode, so callers
 * must always tolerate null.
 */
internal fun validDeviceIdOrNull(id: String?): String? =
    id?.takeIf { it != MyNodeEntity.DEVICE_ID_UNKNOWN && it.matches(HEX_DEVICE_ID) }

/** Datastore key mapping a node number to its canonical DB. Legacy: node numbers renumber under firmware 2.8. */
internal fun nodeDbPrefKey(nodeNum: Int): Preferences.Key<String> =
    stringPreferencesKey("${DatabaseConstants.NODE_DB_FOR_PREFIX}$nodeNum")

/**
 * Datastore key mapping a factory-burned device id to its canonical DB. The id is hex-encoded: it originates as raw
 * hardware bytes decoded lossily into a string, so normalize to printable key material.
 */
internal fun deviceDbPrefKey(deviceId: String): Preferences.Key<String> =
    stringPreferencesKey("${DatabaseConstants.DEVICE_DB_FOR_PREFIX}${deviceId.encodeUtf8().hex()}")

/**
 * Resolves which canonical DB the connected device already claimed: the hardware-stable device-id claim wins, the
 * node-number claim is the fallback for hardware without a device id and for claims written by older app versions.
 */
internal fun resolveDbClaim(
    prefs: Preferences,
    deviceKey: Preferences.Key<String>?,
    nodeKey: Preferences.Key<String>,
): String? = deviceKey?.let { prefs[it] } ?: prefs[nodeKey]
