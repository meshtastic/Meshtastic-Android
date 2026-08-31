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
 *
 * Lives in commonMain (unlike the rest of this file's original contents, see [nodeDbPrefKey]/[deviceDbPrefKey]/
 * [resolveDbClaim] in `nonWebMain`'s `DeviceIdentityPrefs.kt`) because [org.meshtastic.core.database.dao.NodeInfoDao] —
 * needed on every platform including wasmJs — calls it directly.
 */
internal fun validDeviceIdOrNull(id: String?): String? =
    id?.takeIf { it != MyNodeEntity.DEVICE_ID_UNKNOWN && it.matches(HEX_DEVICE_ID) }
