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
package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ── Fake dependencies ─────────────────────────────────────────────────────────

private class FakeRadioConfigRepository(
    mqttAddress: String = "mqtt.meshtastic.org",
    tlsEnabled: Boolean = false,
    jsonEnabled: Boolean = false,
    channelSettings: List<ChannelSettings> = emptyList(),
) : RadioConfigRepository {

    override val channelSetFlow: Flow<ChannelSet> = flowOf(ChannelSet(settings = channelSettings))

    override val moduleConfigFlow: Flow<LocalModuleConfig> =
        flowOf(
            LocalModuleConfig(
                mqtt =
                ModuleConfig.MQTTConfig(
                    enabled = true,
                    address = mqttAddress,
                    tls_enabled = tlsEnabled,
                    json_enabled = jsonEnabled,
                ),
            ),
        )

    override val localConfigFlow: Flow<LocalConfig> = flowOf(LocalConfig())
    override val deviceProfileFlow: Flow<DeviceProfile> = flowOf(DeviceProfile())
    override val deviceUIConfigFlow: Flow<DeviceUIConfig?> = flowOf(null)
    override val fileManifestFlow: Flow<List<FileInfo>> = flowOf(emptyList())

    override suspend fun clearChannelSet() {}

    override suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {}

    override suspend fun updateChannelSettings(channel: Channel) {}

    override suspend fun clearLocalConfig() {}

    override suspend fun setLocalConfig(config: Config) {}

    override suspend fun clearLocalModuleConfig() {}

    override suspend fun setLocalModuleConfig(config: ModuleConfig) {}

    override suspend fun setDeviceUIConfig(config: DeviceUIConfig) {}

    override suspend fun clearDeviceUIConfig() {}

    override suspend fun addFileInfo(info: FileInfo) {}

    override suspend fun clearFileManifest() {}
}

private class FakeNodeRepository(nodeId: String? = "!aabbccdd") : NodeRepository {

    override val myId: StateFlow<String?> = MutableStateFlow(nodeId)
    override val myNodeInfo: StateFlow<MyNodeInfo?> = MutableStateFlow(null)
    override val ourNodeInfo: StateFlow<Node?> = MutableStateFlow(null)
    override val localStats: StateFlow<LocalStats> = MutableStateFlow(LocalStats())
    override val nodeDBbyNum: StateFlow<Map<Int, Node>> = MutableStateFlow(emptyMap())
    override val onlineNodeCount: Flow<Int> = flowOf(0)
    override val totalNodeCount: Flow<Int> = flowOf(0)

    override fun updateLocalStats(stats: LocalStats) {}

    override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> = flowOf(nodeNum)

    override fun getNode(userId: String): Node = Node(0)

    override fun getUser(nodeNum: Int): User = User()

    override fun getUser(userId: String): User = User()

    override fun getNodes(
        sort: NodeSortOption,
        filter: String,
        includeUnknown: Boolean,
        onlyOnline: Boolean,
        onlyDirect: Boolean,
    ): Flow<List<Node>> = flowOf(emptyList())

    override suspend fun getNodesOlderThan(lastHeard: Int): List<Node> = emptyList()

    override suspend fun getUnknownNodes(): List<Node> = emptyList()

    override suspend fun clearNodeDB(preserveFavorites: Boolean) {}

    override suspend fun clearMyNodeInfo() {}

    override suspend fun deleteNode(num: Int) {}

    override suspend fun deleteNodes(nodeNums: List<Int>) {}

    override suspend fun setNodeNotes(num: Int, notes: String) {}

    override suspend fun upsert(node: Node) {}

    override suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>) {}

    override suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {}
}

// ── Helper functions (mirror of production logic) ─────────────────────────────

private fun parseAddress(address: String, tlsEnabled: Boolean = false): Pair<String, Int> =
    address.split(":", limit = 2).let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: if (tlsEnabled) 8883 else 1883) }

private fun backoffDelayMs(attempt: Int): Long {
    val baseDelay = 2_000L
    val maxDelay = 64_000L
    return when {
        attempt == 1 -> 0L
        attempt >= 7 -> maxDelay
        else -> baseDelay * (1L shl (attempt - 2))
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class MQTTRepositoryImplTest {

    // ── Instance creation (covers constructor and field initialization) ────────────

    @Test
    fun `repository can be instantiated with default config`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        assertNotNull(repo)
    }

    @Test
    fun `proxyMessageFlow is not null after instantiation`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        assertNotNull(repo.proxyMessageFlow)
    }

    @Test
    fun `disconnect does not throw when called before connect`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        repo.disconnect() // should not throw an exception
    }

    @Test
    fun `disconnect can be called multiple times without error`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        repo.disconnect()
        repo.disconnect()
        repo.disconnect()
    }

    @Test
    fun `publish does not throw when client is null`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        // client == null immediately after creation without connecting
        repo.publish("msh/test", byteArrayOf(1, 2, 3), retained = false)
        // test passes if no crash
    }

    @Test
    fun `publish does not throw with retained flag`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        repo.publish("msh/test", byteArrayOf(0x08, 0x01), retained = true)
    }

    @Test
    fun `publish does not throw with empty payload`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(),
            )
        repo.publish("msh/test", ByteArray(0), retained = false)
    }

    @Test
    fun `repository works with null nodeId`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository(nodeId = null),
            )
        assertNotNull(repo)
        repo.disconnect()
    }

    @Test
    fun `repository works with TLS config`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository =
                FakeRadioConfigRepository(mqttAddress = "broker.example.com", tlsEnabled = true),
                nodeRepository = FakeNodeRepository(),
            )
        assertNotNull(repo)
    }

    @Test
    fun `repository works with json enabled config`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(jsonEnabled = true),
                nodeRepository = FakeNodeRepository(),
            )
        assertNotNull(repo)
    }

    @Test
    fun `repository works with channel settings`() {
        val repo =
            MQTTRepositoryImpl(
                radioConfigRepository =
                FakeRadioConfigRepository(
                    channelSettings = listOf(ChannelSettings(name = "LongFast", downlink_enabled = true)),
                ),
                nodeRepository = FakeNodeRepository(),
            )
        assertNotNull(repo)
    }

    // ── Address parsing ───────────────────────────────────────────────────────────

    @Test
    fun `address with explicit port is parsed correctly`() {
        val (host, port) = parseAddress("mqtt.example.com:1883")
        assertEquals("mqtt.example.com", host)
        assertEquals(1883, port)
    }

    @Test
    fun `address without port uses default plain port 1883`() {
        val (host, port) = parseAddress("mqtt.example.com", tlsEnabled = false)
        assertEquals("mqtt.example.com", host)
        assertEquals(1883, port)
    }

    @Test
    fun `address without port uses default TLS port 8883`() {
        val (_, port) = parseAddress("broker.local", tlsEnabled = true)
        assertEquals(8883, port)
    }

    @Test
    fun `explicit port overrides TLS default`() {
        val (host, port) = parseAddress("broker.local:9999", tlsEnabled = true)
        assertEquals("broker.local", host)
        assertEquals(9999, port)
    }

    // ── Exponential backoff ───────────────────────────────────────────────────────

    @Test
    fun `backoff attempt 1 is immediate`() {
        assertEquals(0L, backoffDelayMs(1))
    }

    @Test
    fun `backoff attempt 2 is 2 seconds`() {
        assertEquals(2_000L, backoffDelayMs(2))
    }

    @Test
    fun `backoff attempt 3 is 4 seconds`() {
        assertEquals(4_000L, backoffDelayMs(3))
    }

    @Test
    fun `backoff attempt 4 is 8 seconds`() {
        assertEquals(8_000L, backoffDelayMs(4))
    }

    @Test
    fun `backoff attempt 5 is 16 seconds`() {
        assertEquals(16_000L, backoffDelayMs(5))
    }

    @Test
    fun `backoff attempt 6 is 32 seconds`() {
        assertEquals(32_000L, backoffDelayMs(6))
    }

    @Test
    fun `backoff is capped at 64 seconds from attempt 7`() {
        val cap = 64_000L
        assertEquals(cap, backoffDelayMs(7))
        assertEquals(cap, backoffDelayMs(8))
        assertEquals(cap, backoffDelayMs(100))
    }

    @Test
    fun `backoff sequence is non-decreasing`() {
        val delays = (1..10).map { backoffDelayMs(it) }
        for (i in 0 until delays.size - 1) {
            assertTrue(delays[i] <= delays[i + 1], "backoff[${i + 1}]=${delays[i + 1]} < backoff[$i]=${delays[i]}")
        }
    }

    // ── JSON serialization / deserialization ──────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `json payload is parsed correctly`() {
        val jsonStr =
            """{"type":"text","from":12345678,"to":4294967295,"payload":"Hello World","hop_limit":3,"id":123,"time":1600000000}"""
        val payload = json.decodeFromString<MqttJsonPayload>(jsonStr)
        assertEquals("text", payload.type)
        assertEquals(12345678L, payload.from)
        assertEquals(4294967295L, payload.to)
        assertEquals("Hello World", payload.payload)
        assertEquals(3, payload.hopLimit)
        assertEquals(123L, payload.id)
        assertEquals(1600000000L, payload.time)
    }

    @Test
    fun `json payload is serialized correctly`() {
        val payload =
            MqttJsonPayload(
                type = "text",
                from = 12345678,
                to = 4294967295,
                payload = "Hello World",
                hopLimit = 3,
                id = 123,
                time = 1600000000,
            )
        val jsonStr = json.encodeToString(payload)
        assertTrue(jsonStr.contains("\"type\":\"text\""))
        assertTrue(jsonStr.contains("\"from\":12345678"))
        assertTrue(jsonStr.contains("\"payload\":\"Hello World\""))
    }

    @Test
    fun `json payload with optional fields null is valid`() {
        val jsonStr = """{"type":"position","from":999}"""
        val payload = json.decodeFromString<MqttJsonPayload>(jsonStr)
        assertEquals("position", payload.type)
        assertEquals(999L, payload.from)
        assertEquals(null, payload.to)
        assertEquals(null, payload.payload)
    }
}
