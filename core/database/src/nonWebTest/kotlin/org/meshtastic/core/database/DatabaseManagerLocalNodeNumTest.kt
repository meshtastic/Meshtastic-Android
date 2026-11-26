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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [DatabaseManager.localNodeNums] is what the connection list labels each paired radio by. */
class DatabaseManagerLocalNodeNumTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun associationRecordsTheReportedNodeNumberForItsAddressAndFollowsARenumber() = runTest(testDispatcher) {
        val address = "xC8:2B:96:01:96:4E"
        manager.switchActiveDatabase(address)

        // Before firmware 2.8 the node number was MAC bytes 2 to 5; the upgrade renumbers it to crc32(public_key).
        manager.associateDevice(address, nodeNum = 0x9601964E.toInt(), deviceId = null, isSessionActive = { true })
        assertEquals(mapOf("XC82B9601964E" to 0x9601964E.toInt()), manager.localNodeNums.first())

        manager.associateDevice(address, nodeNum = 0x1A2B3C4D, deviceId = null, isSessionActive = { true })
        assertEquals(mapOf("XC82B9601964E" to 0x1A2B3C4D), manager.localNodeNums.first())
    }

    @Test
    fun identityFromAnotherAddressRecordsNothing() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")

        manager.associateDevice("addrB", nodeNum = 123, deviceId = null, isSessionActive = { true })

        assertEquals(emptyMap(), manager.localNodeNums.first())
    }
}
