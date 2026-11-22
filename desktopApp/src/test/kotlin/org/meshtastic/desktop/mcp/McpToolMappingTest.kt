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
package org.meshtastic.desktop.mcp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpToolMappingTest {

    @Test
    fun `a node id parses with its high bit set and anything else is refused`() {
        assertEquals(0xF00DCAFE.toInt(), parseMcpNodeNum("!f00dcafe"))
        assertNull(parseMcpNodeNum("^all"))
        assertNull(parseMcpNodeNum("!abc"))
        assertNull(parseMcpNodeNum("f00dcafe"))
        assertNull(parseMcpNodeNum("!f00dcafe0"))
    }

    @Test
    fun `our own request row is never taken for the reply`() {
        val stats = telemetry { it.local_stats = LocalStats.Builder().build() }
        val requestRow = log(from = MeshLog.NODE_NUM_LOCAL, to = MY_NUM, payload = stats, wantResponse = true)
        val reply = log(from = MeshLog.NODE_NUM_LOCAL, to = MY_NUM, payload = stats)

        assertFalse(isMcpReply(requestRow, McpRequestKind.LOCAL_STATS, null, SINCE, MY_NUM))
        assertTrue(isMcpReply(reply, McpRequestKind.LOCAL_STATS, null, SINCE, MY_NUM))
    }

    @Test
    fun `telemetry without a request id takes only a reply to us since the request`() {
        val device = telemetry {
            it.device_metrics = DeviceMetrics.Builder().also { m -> m.battery_level = 80 }.build()
        }
        val broadcast = log(from = PEER, to = NodeAddress.NODENUM_BROADCAST, payload = device)
        val earlier = log(from = PEER, to = MY_NUM, payload = device, receivedDate = SINCE - 1)
        val reply = log(from = PEER, to = MY_NUM, payload = device)

        assertFalse(isMcpReply(broadcast, McpRequestKind.TELEMETRY, null, SINCE, MY_NUM))
        assertFalse(isMcpReply(earlier, McpRequestKind.TELEMETRY, null, SINCE, MY_NUM))
        assertTrue(isMcpReply(reply, McpRequestKind.TELEMETRY, null, SINCE, MY_NUM))
    }

    @Test
    fun `a reply with a request id must answer that id rather than carry it as its own packet id`() {
        val other = log(from = PEER, to = MY_NUM, port = PortNum.POSITION_APP, requestId = 42, packetId = 41)
        val reply = log(from = PEER, to = MY_NUM, port = PortNum.POSITION_APP, requestId = 41, packetId = 7)

        assertFalse(isMcpReply(other, McpRequestKind.POSITION, 41, SINCE, MY_NUM))
        assertTrue(isMcpReply(reply, McpRequestKind.POSITION, 41, SINCE, MY_NUM))
    }

    @Test
    fun `a config write keeps the section's other fields from the config the app holds`() {
        val values = buildJsonObject {
            put("lora.region", "US")
            put("owner.long_name", "Bench")
        }

        val writes = planMcpConfigWrites(values, HELD)

        assertEquals(listOf("lora", "owner"), writes.map { it.section })
        val lora = writes[0].toConfig().lora!!
        assertEquals(Config.LoRaConfig.RegionCode.US, lora.region)
        assertEquals(HELD_LORA.hop_limit, lora.hop_limit)
        assertEquals(HELD_LORA.tx_power, lora.tx_power)
        assertEquals(HELD_LORA.use_preset, lora.use_preset)
        val owner = writes[1].updated as User
        assertEquals("Bench", owner.long_name)
        assertEquals(HELD_OWNER.short_name, owner.short_name)
        assertEquals(HELD_OWNER.hw_model, owner.hw_model)
    }

    @Test
    fun `a misspelled key or an unknown value or a byte field is refused before anything is sent`() {
        listOf(
            "lora.regoin" to JsonPrimitive("US"),
            "lroa.region" to JsonPrimitive("US"),
            "lora.region" to JsonPrimitive("MARS"),
            "lora" to JsonPrimitive("US"),
            "security.private_key" to JsonPrimitive("AAAA"),
        )
            .forEach { (key, value) ->
                assertFailsWith<McpFieldException>("$key=$value") {
                    planMcpConfigWrites(buildJsonObject { put(key, value) }, HELD)
                }
            }
    }

    @Test
    fun `the app holds a write only when every requested field took`() {
        val values = buildJsonObject {
            put("lora.region", "US")
            put("lora.hop_limit", 3)
        }
        val write = planMcpConfigWrites(values, HELD).single()
        val regionOnly = HELD_LORA.newBuilder().also { it.region = Config.LoRaConfig.RegionCode.US }.build()
        val both = regionOnly.newBuilder().also { it.hop_limit = 3 }.build()

        assertFalse(write.heldBy(HELD.copy(config = LocalConfig.Builder().also { it.lora = regionOnly }.build())))
        assertTrue(write.heldBy(HELD.copy(config = LocalConfig.Builder().also { it.lora = both }.build())))
    }

    @Test
    fun `byte fields are reported by length and never by content`() {
        val key = ByteArray(32) { 0xAB.toByte() }.toByteString()
        val security = Config.SecurityConfig.Builder().also { it.private_key = key }.build()

        val json = mcpJson(security).toString()

        assertTrue("<32 bytes>" in json, json)
        assertFalse(key.hex() in json, json)
        assertFalse(key.base64() in json, json)
    }

    @Test
    fun `a node id finds its conversation on every channel including PKC`() {
        val keys = listOf("0!aabbccdd", "8!aabbccdd", "0!aabbccd1", "0^all", "1^all", "~gone^all")

        assertEquals(listOf("0!aabbccdd", "8!aabbccdd"), matchMcpContactKeys(keys, "!aabbccdd"))
        assertEquals(listOf("0^all", "1^all"), matchMcpContactKeys(keys, "^all"))
        assertEquals(listOf("1^all"), matchMcpContactKeys(keys, "1^all"))
    }

    @Test
    fun `a received broadcast went to everyone and a received DM went to us`() {
        assertEquals("^all", mcpMessageTo("1^all", fromLocal = false, myId = MY_ID))
        assertEquals("!aabbccdd", mcpMessageTo("8!aabbccdd", fromLocal = true, myId = MY_ID))
        assertEquals(MY_ID, mcpMessageTo("8!aabbccdd", fromLocal = false, myId = MY_ID))
    }

    private fun telemetry(block: (Telemetry.Builder) -> Unit): ByteString =
        Telemetry.Builder().also(block).build().encode().toByteString()

    @Suppress("LongParameterList")
    private fun log(
        from: Int,
        to: Int,
        port: PortNum = PortNum.TELEMETRY_APP,
        payload: ByteString = ByteString.EMPTY,
        wantResponse: Boolean = false,
        requestId: Int = 0,
        packetId: Int = 1,
        receivedDate: Long = SINCE + 1,
    ): MeshLog {
        val data =
            Data.Builder()
                .also {
                    it.portnum = port
                    it.payload = payload
                    it.want_response = wantResponse
                    it.request_id = requestId
                }
                .build()
        val packet =
            MeshPacket.Builder()
                .also {
                    it.id = packetId
                    it.from = if (from == MeshLog.NODE_NUM_LOCAL) MY_NUM else from
                    it.to = to
                    it.decoded = data
                }
                .build()
        return MeshLog(
            uuid = "u$packetId",
            message_type = "Packet",
            received_date = receivedDate,
            raw_message = "",
            fromNum = from,
            portNum = port.value,
            fromRadio = FromRadio.Builder().also { it.packet = packet }.build(),
        )
    }

    private companion object {
        const val MY_NUM = 0x1A2B3C4D
        const val MY_ID = "!1a2b3c4d"
        const val PEER = 0x0BADF00D
        const val SINCE = 1_700_000_000_000L

        val HELD_LORA: Config.LoRaConfig =
            Config.LoRaConfig.Builder()
                .also {
                    it.use_preset = true
                    it.hop_limit = 5
                    it.tx_power = 17
                }
                .build()
        val HELD_OWNER: User =
            User.Builder()
                .also {
                    it.long_name = "Old"
                    it.short_name = "OLD"
                    it.hw_model = HardwareModel.HELTEC_V3
                }
                .build()
        val HELD =
            McpHeldConfig(
                config = LocalConfig.Builder().also { it.lora = HELD_LORA }.build(),
                modules = LocalModuleConfig.Builder().build(),
                owner = HELD_OWNER,
            )
    }
}
