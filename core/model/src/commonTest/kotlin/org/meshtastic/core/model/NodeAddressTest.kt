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
package org.meshtastic.core.model

import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeAddressTest {

    // --- fromString parsing ---

    @Test
    fun fromString_null_returnsBroadcast() {
        assertEquals(NodeAddress.Broadcast, NodeAddress.fromString(null))
    }

    @Test
    fun fromString_broadcastString_returnsBroadcast() {
        assertEquals(NodeAddress.Broadcast, NodeAddress.fromString("^all"))
    }

    @Test
    fun fromString_localString_returnsLocal() {
        assertEquals(NodeAddress.Local, NodeAddress.fromString("^local"))
    }

    @Test
    fun fromString_validHexId_returnsByNum() {
        val result = NodeAddress.fromString("!a1b2c3d4")
        assertIs<NodeAddress.ByNum>(result)
        assertEquals(0xa1b2c3d4.toInt(), result.num)
    }

    @Test
    fun fromString_shortHexId_returnsByNum() {
        val result = NodeAddress.fromString("!1234")
        assertIs<NodeAddress.ByNum>(result)
        assertEquals(0x1234, result.num)
    }

    @Test
    fun fromString_invalidHexAfterBang_returnsByIdFallback() {
        val result = NodeAddress.fromString("!notahex")
        assertIs<NodeAddress.ById>(result)
        assertEquals("!notahex", result.id)
    }

    @Test
    fun fromString_arbitraryString_returnsById() {
        val result = NodeAddress.fromString("some-node-name")
        assertIs<NodeAddress.ById>(result)
        assertEquals("some-node-name", result.id)
    }

    @Test
    fun fromString_emptyString_returnsById() {
        val result = NodeAddress.fromString("")
        assertIs<NodeAddress.ById>(result)
        assertEquals("", result.id)
    }

    // --- numToDefaultId ---

    @Test
    fun numToDefaultId_typicalValue_formatsCorrectly() {
        assertEquals("!a1b2c3d4", NodeAddress.numToDefaultId(0xa1b2c3d4.toInt()))
    }

    @Test
    fun numToDefaultId_zero_padsToEightChars() {
        assertEquals("!00000000", NodeAddress.numToDefaultId(0))
    }

    @Test
    fun numToDefaultId_maxInt_formatsCorrectly() {
        assertEquals("!7fffffff", NodeAddress.numToDefaultId(Int.MAX_VALUE))
    }

    @Test
    fun numToDefaultId_negativeOne_formatsAsFffffffff() {
        assertEquals("!ffffffff", NodeAddress.numToDefaultId(-1))
    }

    // --- idToNum ---

    @Test
    fun idToNum_validHex_returnsInt() {
        assertEquals(0xa1b2c3d4.toInt(), NodeAddress.idToNum("a1b2c3d4"))
    }

    @Test
    fun idToNum_withBangPrefix_stripsAndParses() {
        assertEquals(0x1234, NodeAddress.idToNum("!1234"))
    }

    @Test
    fun idToNum_null_returnsNull() {
        assertNull(NodeAddress.idToNum(null))
    }

    @Test
    fun idToNum_emptyString_returnsNull() {
        assertNull(NodeAddress.idToNum(""))
    }

    @Test
    fun idToNum_nonHex_returnsNull() {
        assertNull(NodeAddress.idToNum("zzzzzzzz"))
    }

    @Test
    fun idToNum_overflow_returnsNull() {
        assertNull(NodeAddress.idToNum("ffffffffffffffffff"))
    }

    // --- roundtrip ---

    @Test
    fun numToDefaultId_idToNum_roundtrip() {
        val original = 0xdeadbeef.toInt()
        val id = NodeAddress.numToDefaultId(original)
        val parsed = NodeAddress.idToNum(id)
        assertEquals(original, parsed)
    }

    // --- toIdString ---

    @Test
    fun toIdString_broadcast() {
        assertEquals("^all", NodeAddress.Broadcast.toIdString())
    }

    @Test
    fun toIdString_local() {
        assertEquals("^local", NodeAddress.Local.toIdString())
    }

    @Test
    fun toIdString_byNum() {
        assertEquals("!0000abcd", NodeAddress.ByNum(0xabcd).toIdString())
    }

    @Test
    fun toIdString_byId() {
        assertEquals("custom-id", NodeAddress.ById("custom-id").toIdString())
    }

    // --- toContactKey ---

    @Test
    fun toContactKey_formatsChannelPlusId() {
        val key = NodeAddress.Broadcast.toContactKey(0)
        assertEquals("0^all", key.value)
    }

    @Test
    fun toContactKey_nonZeroChannel() {
        val key = NodeAddress.ByNum(0x1234).toContactKey(3)
        assertEquals("3!00001234", key.value)
    }

    // --- ContactKey ---

    @Test
    fun contactKey_channel_extractsFirstDigit() {
        val key = ContactKey("2!abcdef01")
        assertEquals(2, key.channel)
    }

    @Test
    fun contactKey_addressString_extractsAfterFirstChar() {
        val key = ContactKey("0^all")
        assertEquals("^all", key.addressString)
    }

    @Test
    fun contactKey_address_parsesCorrectly() {
        val key = ContactKey("0^local")
        assertEquals(NodeAddress.Local, key.address)
    }

    @Test
    fun contactKey_broadcast_factory() {
        val key = ContactKey.broadcast(1)
        assertEquals("1^all", key.value)
        assertEquals(NodeAddress.Broadcast, key.address)
    }

    // --- DataPacket extensions ---

    private fun testPacket(to: String? = "^all", from: String? = "^local") =
        DataPacket(to = to, bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value, from = from)

    @Test
    fun dataPacket_destination_parsesBroadcast() {
        assertEquals(NodeAddress.Broadcast, testPacket(to = "^all").destination)
    }

    @Test
    fun dataPacket_source_parsesLocal() {
        assertEquals(NodeAddress.Local, testPacket(from = "^local").source)
    }

    @Test
    fun dataPacket_isFromLocal_trueForLocal() {
        assertTrue(testPacket(from = "^local").isFromLocal())
    }

    @Test
    fun dataPacket_isFromLocal_trueForMatchingNodeNum() {
        assertTrue(testPacket(from = "!000000ff").isFromLocal(myNodeNum = 0xff))
    }

    @Test
    fun dataPacket_isFromLocal_falseForDifferentNodeNum() {
        assertFalse(testPacket(from = "!000000ff").isFromLocal(myNodeNum = 0xaa))
    }

    @Test
    fun dataPacket_isFromLocal_falseWithoutNodeNum() {
        assertFalse(testPacket(from = "!000000ff").isFromLocal(myNodeNum = null))
    }

    @Test
    fun dataPacket_isBroadcast_trueForBroadcastDestination() {
        assertTrue(testPacket(to = "^all").isBroadcast)
    }

    @Test
    fun dataPacket_isBroadcast_falseForUnicastDestination() {
        assertFalse(testPacket(to = "!12345678").isBroadcast)
    }
}
