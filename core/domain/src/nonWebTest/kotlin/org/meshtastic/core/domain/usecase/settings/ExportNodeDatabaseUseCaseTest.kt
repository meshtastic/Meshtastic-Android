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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportNodeDatabaseUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var useCase: ExportNodeDatabaseUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        useCase = ExportNodeDatabaseUseCase(nodeRepository)
    }

    private suspend fun exportJson(): JsonObject {
        val buffer = Buffer()
        useCase(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    @Test
    fun `empty database exports a valid document`() = runTest {
        val root = exportJson()

        assertEquals(1, root["schemaVersion"]?.jsonPrimitive?.int)
        assertTrue(root["exportedAt"]?.jsonPrimitive?.content.orEmpty().isNotEmpty())
        assertNull(root["myNodeNum"])
        assertEquals(0, root["nodes"]?.jsonArray?.size)
    }

    @Test
    fun `absent readings are omitted rather than exported as sentinels`() = runTest {
        // A bare node: snr/rssi hold their unset sentinels, lastHeard is 0, and no metrics were ever received.
        nodeRepository.setNodes(listOf(Node(num = 42)))

        val node = exportJson()["nodes"]!!.jsonArray.single().jsonObject

        assertEquals(42, node["num"]?.jsonPrimitive?.int)
        assertFalse("snr" in node)
        assertFalse("rssi" in node)
        assertFalse("lastHeard" in node)
        assertFalse("position" in node)
        assertFalse("deviceMetrics" in node)
        assertFalse("environmentMetrics" in node)
        assertFalse("publicKey" in node)
    }

    @Test
    fun `zero readings are real and survive the export`() = runTest {
        // 0 dB SNR and 0 dBm RSSI are genuine measurements, not absence.
        nodeRepository.setNodes(listOf(Node(num = 7, snr = 0f, rssi = 0, lastHeard = 1700000000)))

        val node = exportJson()["nodes"]!!.jsonArray.single().jsonObject

        assertEquals(0f, node["snr"]?.jsonPrimitive?.float)
        assertEquals(0, node["rssi"]?.jsonPrimitive?.int)
        assertEquals(1700000000L, node["lastHeard"]?.jsonPrimitive?.long)
    }

    @Test
    fun `node fields map through user position metrics and key`() = runTest {
        val key = "0123456789abcdef0123456789abcdef".encodeUtf8()
        val node =
            Node(
                num = 7,
                user =
                User(
                    id = "!00000007",
                    long_name = "Base Camp",
                    short_name = "BASE",
                    hw_model = HardwareModel.TBEAM,
                ),
                position = Position(latitude_i = 450000000, longitude_i = -930000000),
                deviceMetrics = DeviceMetrics(battery_level = 85, voltage = 3.9f),
                publicKey = key,
                isFavorite = true,
                viaMqtt = true,
                hopsAway = 2,
                notes = "solar repeater",
            )
        nodeRepository.setNodes(listOf(node))

        val exported = exportJson()["nodes"]!!.jsonArray.single().jsonObject

        assertEquals("!00000007", exported["id"]?.jsonPrimitive?.content)
        assertEquals("Base Camp", exported["longName"]?.jsonPrimitive?.content)
        assertEquals("BASE", exported["shortName"]?.jsonPrimitive?.content)
        assertEquals("TBEAM", exported["hwModel"]?.jsonPrimitive?.content)
        assertEquals(key.base64(), exported["publicKey"]?.jsonPrimitive?.content)
        assertTrue(exported["isFavorite"]?.jsonPrimitive?.boolean == true)
        assertTrue(exported["viaMqtt"]?.jsonPrimitive?.boolean == true)
        assertEquals(2, exported["hopsAway"]?.jsonPrimitive?.int)
        assertEquals("solar repeater", exported["notes"]?.jsonPrimitive?.content)

        val position = exported["position"]!!.jsonObject
        assertEquals(45.0, position["latitude"]!!.jsonPrimitive.double, absoluteTolerance = 1e-6)
        assertEquals(-93.0, position["longitude"]!!.jsonPrimitive.double, absoluteTolerance = 1e-6)

        val metrics = exported["deviceMetrics"]!!.jsonObject
        assertEquals(85, metrics["batteryLevel"]?.jsonPrimitive?.int)
        assertEquals(3.9f, metrics["voltage"]?.jsonPrimitive?.float)
    }

    @Test
    fun `node numbers export as unsigned`() = runTest {
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = -2))
        nodeRepository.setNodes(listOf(Node(num = -1)))

        val root = exportJson()

        assertEquals(4294967294L, root["myNodeNum"]?.jsonPrimitive?.long)
        val node = root["nodes"]!!.jsonArray.single().jsonObject
        assertEquals(4294967295L, node["num"]?.jsonPrimitive?.long)
    }

    @Test
    fun `nodes are sorted most recently heard first`() = runTest {
        nodeRepository.setNodes(
            listOf(Node(num = 1, lastHeard = 100), Node(num = 2, lastHeard = 300), Node(num = 3, lastHeard = 200)),
        )

        val nums = exportJson()["nodes"]!!.jsonArray.map { it.jsonObject["num"]?.jsonPrimitive?.int }

        assertEquals(listOf(2, 3, 1), nums)
    }
}
