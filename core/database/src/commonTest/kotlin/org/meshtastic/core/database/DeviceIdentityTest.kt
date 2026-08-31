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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers [validDeviceIdOrNull], the portable half of the cross-transport DB-claim helpers behind
 * `DatabaseManager.associateDevice` (`nonWebMain`-only). The Preferences-keyed half (`deviceDbPrefKey`/
 * `nodeDbPrefKey`/`resolveDbClaim`) lives in `nonWebTest`'s `DeviceIdentityPrefsTest.kt` alongside the
 * `nonWebMain`-only types it depends on.
 */
class DeviceIdentityTest {

    @Test
    fun validDeviceIdRejectsAbsentBlankPlaceholderAndNonHexForms() {
        assertNull(validDeviceIdOrNull(null))
        assertNull(validDeviceIdOrNull(""))
        assertNull(validDeviceIdOrNull("   "))
        assertNull(validDeviceIdOrNull(MyNodeEntity.DEVICE_ID_UNKNOWN))
        // Legacy app versions persisted a lossy utf8 decode of the raw bytes — such values can
        // collide across devices and must be treated as absent, not compared.
        assertNull(validDeviceIdOrNull("��legacy id"))
        assertNull(validDeviceIdOrNull("abcdef")) // too short to be a real 16-byte id
        val hexId = "a1b2c3d4e5f60718a9b0c1d2e3f40516"
        assertEquals(hexId, validDeviceIdOrNull(hexId))
    }
}
