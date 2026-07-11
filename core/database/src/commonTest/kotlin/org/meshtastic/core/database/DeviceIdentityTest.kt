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

import androidx.datastore.preferences.core.preferencesOf
import org.meshtastic.core.database.entity.MyNodeEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the cross-transport DB-claim helpers behind [DatabaseManager.associateDevice]. */
class DeviceIdentityTest {

    @Test
    fun validDeviceIdRejectsAbsentBlankAndPlaceholder() {
        assertNull(validDeviceIdOrNull(null))
        assertNull(validDeviceIdOrNull(""))
        assertNull(validDeviceIdOrNull("   "))
        assertNull(validDeviceIdOrNull(MyNodeEntity.DEVICE_ID_UNKNOWN))
        assertEquals("hw-A", validDeviceIdOrNull("hw-A"))
    }

    @Test
    fun deviceKeyIsDeterministicAndDistinctPerDevice() {
        assertEquals(deviceDbPrefKey("hw-A"), deviceDbPrefKey("hw-A"))
        assertTrue(deviceDbPrefKey("hw-A") != deviceDbPrefKey("hw-B"))
        // Raw device-id bytes decode lossily to strings; the key must stay printable regardless.
        val keyName = deviceDbPrefKey("\uFFFD id").name
        assertTrue(keyName.startsWith(DatabaseConstants.DEVICE_DB_FOR_PREFIX))
        assertTrue(keyName.drop(DatabaseConstants.DEVICE_DB_FOR_PREFIX.length).all { it.isLetterOrDigit() })
    }

    @Test
    fun deviceClaimIsPreferredOverNodeClaim() {
        val deviceKey = deviceDbPrefKey("hw-A")
        val nodeKey = nodeDbPrefKey(100)
        val prefs = preferencesOf(deviceKey to "db_device", nodeKey to "db_node")
        assertEquals("db_device", resolveDbClaim(prefs, deviceKey, nodeKey))
    }

    @Test
    fun nodeClaimIsTheFallback() {
        val deviceKey = deviceDbPrefKey("hw-A")
        val nodeKey = nodeDbPrefKey(100)
        val legacyOnly = preferencesOf(nodeKey to "db_node")
        // Device id available but only a legacy nodeNum claim exists (pre-device-id install).
        assertEquals("db_node", resolveDbClaim(legacyOnly, deviceKey, nodeKey))
        // No device id at all (classic ESP32, lockdown, old firmware).
        assertEquals("db_node", resolveDbClaim(legacyOnly, deviceKey = null, nodeKey = nodeKey))
    }

    @Test
    fun renumberedDeviceStillResolvesItsClaimViaDeviceKey() {
        val deviceKey = deviceDbPrefKey("hw-A")
        // Claims written before a firmware 2.8 renumber: device key + OLD node num key.
        val prefs = preferencesOf(deviceKey to "db_claimed", nodeDbPrefKey(100) to "db_claimed")
        // After the renumber the node key is new and unclaimed — the device key still resolves.
        assertEquals("db_claimed", resolveDbClaim(prefs, deviceKey, nodeDbPrefKey(200)))
    }

    @Test
    fun unclaimedDeviceResolvesNothing() {
        assertNull(resolveDbClaim(preferencesOf(), deviceDbPrefKey("hw-A"), nodeDbPrefKey(100)))
    }
}
