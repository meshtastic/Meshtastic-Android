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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

class NodeManagerImplTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val testScope = TestScope()

    private lateinit var nodeManager: NodeManagerImpl

    @BeforeTest
    fun setUp() {
        nodeManager = NodeManagerImpl(nodeRepository, notificationManager, radioInterfaceService, testScope)
    }

    @Test
    fun `getOrCreateNode creates default user for unknown node`() {
        val nodeNum = 1234
        val result = nodeManager.getOrCreateNode(nodeNum)

        assertNotNull(result)
        assertEquals(nodeNum, result.num)
        assertTrue(result.user.long_name.startsWith("Meshtastic"))
        assertEquals(NodeAddress.numToDefaultId(nodeNum), result.user.id)
    }

    @Test
    fun `updateNodeAndPersist awaits the repository write`() = testScope.runTest {
        val nodeNum = 1234
        nodeManager.setNodeDbReady(true)
        nodeManager.setAllowNodeDbWrites(true)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.upsert(any()) } calls
            {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }

        val update = async { nodeManager.updateNodeAndPersist(nodeNum) { node -> node.copy(lastHeard = 42) } }
        writeStarted.await()
        assertFalse(update.isCompleted)
        releaseWrite.complete(Unit)
        update.await()

        verifySuspend { nodeRepository.upsert(any()) }
        assertEquals(42, nodeManager.nodeDBbyNodeNum[nodeNum]?.lastHeard)
    }

    @Test
    fun `same-node persistence cannot finish with an older snapshot`() = testScope.runTest {
        val nodeNum = 1234
        nodeManager.setNodeDbReady(true)
        nodeManager.setAllowNodeDbWrites(true)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val secondWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val persisted = mutableListOf<Node>()
        var invocation = 0
        everySuspend { nodeRepository.upsert(any()) } calls
            {
                val node = it.arg<Node>(0)
                invocation++
                when (invocation) {
                    1 -> {
                        firstWriteStarted.complete(Unit)
                        releaseFirstWrite.await()
                    }

                    2 -> secondWriteStarted.complete(Unit)
                }
                persisted += node
            }

        val first = async { nodeManager.updateNodeAndPersist(nodeNum) { node -> node.copy(lastHeard = 1) } }
        firstWriteStarted.await()
        val second =
            async(start = CoroutineStart.UNDISPATCHED) {
                nodeManager.updateNodeAndPersist(nodeNum) { node -> node.copy(lastHeard = 2) }
            }
        runCurrent()
        assertFalse(
            secondWriteStarted.isCompleted,
            "the second upsert must not enter while the first owns its lane",
        )

        releaseFirstWrite.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf(1, 2), persisted.map(Node::lastHeard))
        assertEquals(2, nodeManager.nodeDBbyNodeNum[nodeNum]?.lastHeard)
    }

    @Test
    fun `session-bound node persistence from a retired generation is rejected`() = testScope.runTest {
        val nodeNum = 1234
        val oldSession = RadioSessionContext(generation = 7L, address = "ble:same")
        nodeManager.setNodeDbReady(true)
        nodeManager.setAllowNodeDbWrites(true)
        everySuspend { radioInterfaceService.runWithSessionLease(oldSession, any()) } returns false

        nodeManager.updateNodeForSession(nodeNum, oldSession) { node -> node.copy(lastHeard = 42) }
        runCurrent()

        assertEquals(42, nodeManager.nodeDBbyNodeNum[nodeNum]?.lastHeard)
        verifySuspend(exactly(0)) { nodeRepository.upsert(any()) }
    }

    @Test
    fun `node persistence is suppressed while database writes are disabled`() = testScope.runTest {
        val nodeNum = 1234
        nodeManager.setNodeDbReady(true)
        nodeManager.setAllowNodeDbWrites(false)

        nodeManager.updateNodeAndPersist(nodeNum) { node -> node.copy(lastHeard = 42) }

        verifySuspend(exactly(0)) { nodeRepository.upsert(any()) }
        assertEquals(42, nodeManager.nodeDBbyNodeNum[nodeNum]?.lastHeard)
    }

    @Test
    fun `handleReceivedUser preserves existing user if incoming is default`() {
        val nodeNum = 1234
        val existingUser =
            User(id = "!12345678", long_name = "My Custom Name", short_name = "MCN", hw_model = HardwareModel.TLORA_V2)

        // Setup existing node
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDefaultUser =
            User(id = "!12345678", long_name = "Meshtastic 5678", short_name = "5678", hw_model = HardwareModel.UNSET)

        nodeManager.handleReceivedUser(nodeNum, incomingDefaultUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("My Custom Name", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V2, result.user.hw_model)
    }

    @Test
    fun `handleReceivedUser updates user if incoming is higher detail`() {
        val nodeNum = 1234
        // Use a non-UNSET hw_model so isUnknownUser=false (avoids new-node notification + getString)
        val existingUser =
            User(id = "!12345678", long_name = "Old Name", short_name = "ON", hw_model = HardwareModel.TLORA_V2)

        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDetailedUser =
            User(id = "!12345678", long_name = "Real User", short_name = "RU", hw_model = HardwareModel.TLORA_V1)

        nodeManager.handleReceivedUser(nodeNum, incomingDetailedUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("Real User", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V1, result.user.hw_model)
    }

    @Test
    fun `handleReceivedPosition updates node position`() {
        val nodeNum = 1234
        val position = ProtoPosition(latitude_i = 450000000, longitude_i = 900000000)

        nodeManager.handleReceivedPosition(nodeNum, 9999, position, 0)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result)
        assertNotNull(result.position)
        assertEquals(450000000, result.position.latitude_i)
        assertEquals(900000000, result.position.longitude_i)
    }

    @Test
    fun `handleReceivedPosition with zero coordinates preserves last known location but updates satellites`() {
        val nodeNum = 1234
        val initialPosition = ProtoPosition(latitude_i = 450000000, longitude_i = 900000000, sats_in_view = 10)
        nodeManager.handleReceivedPosition(nodeNum, 9999, initialPosition, 1000000L)

        // Receive "zero" position with new satellite count
        val zeroPosition = ProtoPosition(latitude_i = 0, longitude_i = 0, sats_in_view = 5, time = 1001)
        nodeManager.handleReceivedPosition(nodeNum, 9999, zeroPosition, 1001000L)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(450000000, result!!.position.latitude_i)
        assertEquals(900000000, result.position.longitude_i)
        assertEquals(5, result.position.sats_in_view)
        assertEquals(1001, result.lastHeard)
    }

    @Test
    fun `handleReceivedPosition for local node ignores purely empty packets`() {
        val myNum = 1111
        val emptyPos = ProtoPosition(latitude_i = 0, longitude_i = 0, sats_in_view = 0, time = 0)

        nodeManager.handleReceivedPosition(myNum, myNum, emptyPos, 0)

        val result = nodeManager.nodeDBbyNodeNum[myNum]
        // Should still be null since the empty position for local node is ignored
        assertNull(result)
    }

    @Test
    fun `handleReceivedTelemetry updates lastHeard`() {
        val nodeNum = 1234
        nodeManager.updateNode(nodeNum) { it.copy(lastHeard = 1000) }

        val telemetry = Telemetry(time = 2000, device_metrics = DeviceMetrics(battery_level = 50))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(2000, result!!.lastHeard)
    }

    @Test
    fun `handleReceivedTelemetry updates device metrics`() {
        val nodeNum = 1234
        val telemetry = Telemetry(device_metrics = DeviceMetrics(battery_level = 75, voltage = 3.8f))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.deviceMetrics)
        assertEquals(75, result.deviceMetrics.battery_level)
        assertEquals(3.8f, result.deviceMetrics.voltage)
    }

    @Test
    fun `handleReceivedTelemetry updates environment metrics`() {
        val nodeNum = 1234
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(temperature = 22.5f, relative_humidity = 45.0f))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.environmentMetrics)
        assertEquals(22.5f, result.environmentMetrics.temperature)
        assertEquals(45.0f, result.environmentMetrics.relative_humidity)
    }

    @Test
    fun `clear resets internal state`() {
        nodeManager.updateNode(1234) { it.copy(user = it.user.copy(long_name = "Test")) }
        nodeManager.clear()

        assertTrue(nodeManager.nodeDBbyNodeNum.isEmpty())
        assertNull(nodeManager.getNodeById("!000004d2"))
        assertNull(nodeManager.myNodeNum.value)
    }

    @Test
    fun `toNodeID returns broadcast ID for broadcast nodeNum`() {
        val result = nodeManager.toNodeID(NodeAddress.NODENUM_BROADCAST)
        assertEquals(NodeAddress.ID_BROADCAST, result)
    }

    @Test
    fun `toNodeID returns default hex ID for unknown node`() {
        val result = nodeManager.toNodeID(0x1234)
        assertEquals(NodeAddress.numToDefaultId(0x1234), result)
    }

    @Test
    fun `toNodeID returns user ID for known node`() {
        val nodeNum = 5678
        val userId = "!customid"
        nodeManager.updateNode(nodeNum) { it.copy(user = it.user.copy(id = userId)) }
        val result = nodeManager.toNodeID(nodeNum)
        assertEquals(userId, result)
    }

    @Test
    fun `removeByNodenum removes node from map`() {
        val nodeNum = 1234
        nodeManager.updateNode(nodeNum) {
            Node(num = nodeNum, user = User(id = "!testnode", long_name = "Test", short_name = "T"))
        }
        assertTrue(nodeManager.nodeDBbyNodeNum.containsKey(nodeNum))
        assertNotNull(nodeManager.getNodeById("!testnode"))

        nodeManager.removeByNodenum(nodeNum)

        assertTrue(!nodeManager.nodeDBbyNodeNum.containsKey(nodeNum))
        assertNull(nodeManager.getNodeById("!testnode"))
    }

    @Test
    fun `handleReceivedUser sets publicKey from user public_key`() {
        val nodeNum = 1234
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val existingUser =
            User(id = "!12345678", long_name = "Existing", short_name = "EX", hw_model = HardwareModel.TLORA_V2)
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingUser =
            User(
                id = "!12345678",
                long_name = "Updated",
                short_name = "UP",
                hw_model = HardwareModel.TLORA_V2,
                public_key = pk,
            )
        nodeManager.handleReceivedUser(nodeNum, incomingUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(pk, result.publicKey)
        assertEquals(pk, result.user.public_key)
        assertTrue(result.hasPKC)
    }

    @Test
    fun `handleReceivedUser sets empty publicKey when key mismatch clears user key`() {
        val nodeNum = 1234
        val existingPk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val existingUser =
            User(
                id = "!12345678",
                long_name = "Existing",
                short_name = "EX",
                hw_model = HardwareModel.TLORA_V2,
                public_key = existingPk,
            )
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser, publicKey = existingPk) }

        val differentPk = ByteArray(32) { (it + 10).toByte() }.toByteString()
        val incomingUser =
            User(
                id = "!12345678",
                long_name = "Updated",
                short_name = "UP",
                hw_model = HardwareModel.TLORA_V2,
                public_key = differentPk,
            )
        nodeManager.handleReceivedUser(nodeNum, incomingUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        // Key mismatch: newUser gets public_key cleared to EMPTY, and publicKey should match
        assertEquals(ByteString.EMPTY, result.publicKey)
        assertEquals(ByteString.EMPTY, result.user.public_key)
    }

    @Test
    fun `installNodeInfo sets publicKey from user public_key`() {
        val nodeNum = 5678
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val user =
            User(
                id = "!abcd1234",
                long_name = "Remote Node",
                short_name = "RN",
                hw_model = HardwareModel.HELTEC_V3,
                public_key = pk,
            )
        val info = ProtoNodeInfo(num = nodeNum, user = user, last_heard = 1000, channel = 0)

        nodeManager.installNodeInfo(info)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(pk, result.publicKey)
        assertEquals(pk, result.user.public_key)
        assertTrue(result.hasPKC)
    }

    @Test
    fun `installNodeInfo clears publicKey for licensed users`() {
        val nodeNum = 5678
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val user =
            User(
                id = "!abcd1234",
                long_name = "Licensed Op",
                short_name = "LO",
                hw_model = HardwareModel.HELTEC_V3,
                public_key = pk,
                is_licensed = true,
            )
        val info = ProtoNodeInfo(num = nodeNum, user = user, last_heard = 1000, channel = 0)

        nodeManager.installNodeInfo(info)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(ByteString.EMPTY, result.publicKey)
        assertEquals(ByteString.EMPTY, result.user.public_key)
    }

    @Test
    fun `getMyNodeInfo returns null when repository has no info`() {
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        val result = nodeManager.getMyNodeInfo()

        assertNull(result)
    }

    @Test
    fun `getMyNodeInfo synthesizes from repository and nodeDB`() {
        val myNum = 1234
        val repoInfo =
            MyNodeInfo(
                myNodeNum = myNum,
                hasGPS = false,
                model = "tbeam",
                firmwareVersion = "2.5.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 100L,
                messageTimeoutMsec = 5000,
                minAppVersion = 30000,
                maxChannels = 8,
                hasWifi = false,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = null,
            )
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(repoInfo)

        // Add node with position (non-zero lat → hasGPS = true)
        nodeManager.handleReceivedPosition(myNum, myNum, ProtoPosition(latitude_i = 100), 0)
        nodeManager.updateNode(myNum) { it.copy(user = it.user.copy(id = "!mydevice", hw_model = HardwareModel.TBEAM)) }

        val result = nodeManager.getMyNodeInfo()

        assertNotNull(result)
        assertEquals(myNum, result.myNodeNum)
        assertTrue(result.hasGPS)
        assertEquals("tbeam", result.model)
        assertEquals("!mydevice", result.deviceId)
    }

    @Test
    fun `getMyNodeInfo falls back to nodeDB model when repository model is null`() {
        val myNum = 1234
        val repoInfo =
            MyNodeInfo(
                myNodeNum = myNum,
                hasGPS = false,
                model = null,
                firmwareVersion = "2.5.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 100L,
                messageTimeoutMsec = 5000,
                minAppVersion = 30000,
                maxChannels = 8,
                hasWifi = false,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = null,
            )
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(repoInfo)

        nodeManager.updateNode(myNum) { it.copy(user = it.user.copy(hw_model = HardwareModel.HELTEC_V3)) }

        val result = nodeManager.getMyNodeInfo()

        assertNotNull(result)
        assertEquals("HELTEC_V3", result.model)
    }

    @Test
    fun `handleReceivedTelemetry with null metrics does not crash`() {
        val nodeNum = 1234
        nodeManager.updateNode(nodeNum) { it.copy(lastHeard = 1000) }

        // Telemetry with no metrics at all
        val telemetry = Telemetry(time = 3000)

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result)
        assertEquals(3000, result.lastHeard)
    }

    @Test
    fun `getMyId returns empty when disconnected`() {
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        val result = nodeManager.getMyId()
        assertEquals("", result)
    }

    @Test
    fun `getMyId returns user ID when connected`() {
        val myNum = 1234
        nodeManager.setMyNodeNum(myNum)
        nodeManager.updateNode(myNum) { it.copy(user = it.user.copy(id = "!mynode42")) }

        val result = nodeManager.getMyId()
        assertEquals("!mynode42", result)
    }
}
