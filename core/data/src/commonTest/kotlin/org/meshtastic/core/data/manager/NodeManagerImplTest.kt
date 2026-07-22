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
import dev.mokkery.matcher.capture.capture
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
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
        // Override the compose-resources formatter so notification dispatch is deterministic in the
        // plain-JVM test env (getStringSuspend does not resolve here). Tests that assert "no dispatch"
        // still hold: the override only changes the title, not whether dispatch fires.
        nodeManager.notificationTitleFormatter = { shortName -> "New node seen: $shortName" }
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

    // ---------- Atomic identity reconciliation ----------
    //
    // Covers the CAS-loop reducer that replaced the legacy read-then-mutate sequence. Each test asserts typed
    // in-memory state and the permitted side effects (ordinary same-number upsert and notification dispatch).

    private val validPk = ByteArray(32) { (it + 1).toByte() }.toByteString()

    private fun ByteString.canonicalNum(): Int = crc32().toInt()

    private fun ByteString.noncanonicalNum(preferred: Int): Int =
        if (preferred != canonicalNum()) preferred else preferred + 1

    private fun makeKnownNode(num: Int, pk: ByteString, name: String = "Known"): Node = Node(
        num = num,
        user =
        User(
            id = "!${num.toString(16)}",
            long_name = name,
            short_name = name.take(3),
            hw_model = HardwareModel.TLORA_V2,
            public_key = pk,
        ),
        publicKey = pk,
    )

    private fun enableDbWrites() {
        nodeManager.setNodeDbReady(true)
        nodeManager.setAllowNodeDbWrites(true)
    }

    private fun verifyNoRepositoryDeletion() {
        verifySuspend(mode = VerifyMode.not) { nodeRepository.deleteNode(any()) }
        verifySuspend(mode = VerifyMode.not) { nodeRepository.deleteNodes(any()) }
    }

    // ---------- Trusted migration retirement ----------

    @Test
    fun `retired number rejects delayed field sequence without persistence`() {
        val retiredNum = validPk.noncanonicalNum(4100)
        nodeManager.updateNode(retiredNum) { makeKnownNode(retiredNum, validPk, "Pre-migration") }
        nodeManager.applyTrustedIdentityMigrations(listOf(retiredNum))
        enableDbWrites()

        nodeManager.handleReceivedTelemetry(retiredNum, Telemetry(device_metrics = DeviceMetrics(battery_level = 50)))
        nodeManager.handleReceivedPosition(
            retiredNum,
            myNodeNum = 9999,
            p = ProtoPosition(latitude_i = 123, longitude_i = 456),
            defaultTime = 1000L,
        )
        nodeManager.handleReceivedPaxcounter(retiredNum, Paxcount(wifi = 10, ble = 5, uptime = 1000))
        nodeManager.handleReceivedNodeStatus(retiredNum, StatusMessage(status = "stale"))
        nodeManager.installNodeInfo(
            ProtoNodeInfo(
                num = retiredNum,
                user =
                User(
                    id = "!retired",
                    long_name = "Retired",
                    short_name = "RET",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = validPk,
                ),
            ),
        )
        nodeManager.insertMetadata(retiredNum, DeviceMetadata(firmware_version = "2.7.0"))
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[retiredNum])
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { nodeRepository.insertMetadata(any(), any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `retired number suppresses keyless and represented-key user replays`() {
        val retiredNum = validPk.noncanonicalNum(4200)
        val canonicalNum = validPk.canonicalNum()
        nodeManager.updateNode(canonicalNum) { makeKnownNode(canonicalNum, validPk, "Canonical") }
        nodeManager.updateNode(retiredNum) { makeKnownNode(retiredNum, validPk, "Retired") }
        nodeManager.applyTrustedIdentityMigrations(listOf(retiredNum))
        enableDbWrites()

        nodeManager.handleReceivedUser(
            retiredNum,
            User(id = "!keyless", long_name = "Keyless replay", short_name = "KEY", hw_model = HardwareModel.TLORA_V2),
        )
        nodeManager.handleReceivedUser(
            retiredNum,
            User(
                id = "!invalid",
                long_name = "Invalid-key replay",
                short_name = "INV",
                hw_model = HardwareModel.TLORA_V2,
                public_key = byteArrayOf(1, 2, 3).toByteString(),
            ),
        )
        nodeManager.handleReceivedUser(
            retiredNum,
            User(
                id = "!represented",
                long_name = "Represented replay",
                short_name = "REP",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            ),
        )
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[retiredNum])
        assertEquals("Canonical", nodeManager.nodeDBbyNodeNum[canonicalNum]?.user?.long_name)
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `retired number accepts validated new-key reuse and un-retires the slot`() {
        // Seed a trusted old identity at the number FIRST, so the migration retires it with a trusted key hint. Without
        // the hint, a later key packet would be suppressed as RETIRED_WITHOUT_HINT_SUPPRESSED and the slot would stay
        // retired for the session (see reduceReceivedUser).
        val retiredNum = validPk.noncanonicalNum(4300)
        val replacementKey = ByteArray(32) { (it + 80).toByte() }.toByteString()
        nodeManager.updateNode(retiredNum) { makeKnownNode(retiredNum, validPk, "Old") }
        testScope.advanceUntilIdle()
        nodeManager.applyTrustedIdentityMigrations(listOf(retiredNum))
        enableDbWrites()

        nodeManager.handleReceivedUser(
            retiredNum,
            User(
                id = "!replacement",
                long_name = "Replacement",
                short_name = "NEW",
                hw_model = HardwareModel.TLORA_V2,
                public_key = replacementKey,
            ),
        )
        testScope.advanceUntilIdle()

        val replacement = nodeManager.nodeDBbyNodeNum[retiredNum]
        assertNotNull(replacement)
        assertEquals(replacementKey, replacement.publicKey)
        assertEquals("Replacement", replacement.user.long_name)
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.exactly(1)) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `loadCachedNodeDB preserves retirement and suppresses reloaded stale replay`() {
        // The repo still holds the retired node's row; a cache reload must keep the trusted retirement and must NOT
        // resurrect the retired number into the live index, and a later stale replay at that number stays suppressed.
        val retiredNum = validPk.noncanonicalNum(4500)
        val retiredNode = makeKnownNode(retiredNum, validPk, "Retired")
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(retiredNum to retiredNode))
        everySuspend { nodeRepository.getNodeDbSnapshot() } returns mapOf(retiredNum to retiredNode)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        nodeManager.applyTrustedIdentityMigrations(listOf(retiredNum))
        enableDbWrites()

        nodeManager.loadCachedNodeDB()
        testScope.advanceUntilIdle()

        // Retirement survived the reload AND the reloaded row was filtered out of the live index.
        assertNull(nodeManager.nodeDBbyNodeNum[retiredNum])

        // A stale keyless replay at the retired number is still suppressed after the reload.
        nodeManager.handleReceivedUser(
            retiredNum,
            User(id = "!stale", long_name = "Stale replay", short_name = "STL", hw_model = HardwareModel.TLORA_V2),
        )
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[retiredNum])
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `clear starts a new session without retired-number suppression`() {
        val retiredNum = validPk.noncanonicalNum(4400)
        nodeManager.applyTrustedIdentityMigrations(listOf(retiredNum))
        nodeManager.handleReceivedTelemetry(retiredNum, Telemetry(device_metrics = DeviceMetrics(battery_level = 10)))
        assertNull(nodeManager.nodeDBbyNodeNum[retiredNum])

        nodeManager.clear()
        nodeManager.handleReceivedTelemetry(retiredNum, Telemetry(device_metrics = DeviceMetrics(battery_level = 90)))

        assertEquals(90, nodeManager.nodeDBbyNodeNum[retiredNum]?.deviceMetrics?.battery_level)
    }

    // 1. Established same-key duplicate

    @Test
    fun `established same-key stale replay is removed in memory without repository mutation`() {
        val oldNum = validPk.noncanonicalNum(1000)
        val newNum = validPk.canonicalNum()
        nodeManager.updateNode(newNum) { makeKnownNode(newNum, validPk, "Migrated") }
        nodeManager.updateNode(oldNum) { makeKnownNode(oldNum, validPk, "Stale Established") }
        enableDbWrites()

        val staleUser =
            User(
                id = "!old",
                long_name = "Stale",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(oldNum, staleUser)
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[oldNum])
        val canonical = nodeManager.nodeDBbyNodeNum[newNum]
        assertNotNull(canonical)
        assertEquals("Migrated", canonical!!.user.long_name)
        assertEquals(validPk, canonical.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 2. Placeholder duplicate

    @Test
    fun `stale replay preserves no-key telemetry placeholder and canonical identity`() {
        val oldNum = validPk.noncanonicalNum(1000)
        val newNum = validPk.canonicalNum()
        nodeManager.updateNode(newNum) { makeKnownNode(newNum, validPk, "Migrated") }
        val placeholderKey = ByteString.EMPTY
        val defaultId = NodeAddress.numToDefaultId(oldNum)
        nodeManager.updateNode(oldNum) {
            Node(
                num = oldNum,
                user =
                User(
                    id = defaultId,
                    long_name = "Meshtastic ${defaultId.takeLast(4)}",
                    short_name = defaultId.takeLast(4),
                    hw_model = HardwareModel.UNSET,
                    public_key = placeholderKey,
                ),
                publicKey = placeholderKey,
                position = ProtoPosition(latitude_i = 123, longitude_i = 456),
                channel = 7,
            )
        }
        enableDbWrites()

        val staleUser =
            User(
                id = "!old",
                long_name = "Stale",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(oldNum, staleUser)
        testScope.advanceUntilIdle()

        val placeholder = nodeManager.nodeDBbyNodeNum[oldNum]
        assertNotNull(placeholder)
        assertEquals(defaultId, placeholder.user.id)
        assertEquals(123, placeholder.position.latitude_i)
        assertEquals(456, placeholder.position.longitude_i)
        assertEquals(7, placeholder.channel)
        val canonical = nodeManager.nodeDBbyNodeNum[newNum]
        assertNotNull(canonical)
        assertEquals("Migrated", canonical!!.user.long_name)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 3. Different-key conflict

    @Test
    fun `different-key conflict preserves both nodes and emits no side effects`() {
        val fromNum = 1000
        val otherNum = 2000
        val differentPk = ByteArray(32) { (it + 50).toByte() }.toByteString()
        nodeManager.updateNode(otherNum) { makeKnownNode(otherNum, validPk, "Other") }
        nodeManager.updateNode(fromNum) { makeKnownNode(fromNum, differentPk, "Established") }
        enableDbWrites()

        val incomingUser =
            User(
                id = "!stale",
                long_name = "Stale",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(fromNum, incomingUser)
        testScope.advanceUntilIdle()

        val fromNode = nodeManager.nodeDBbyNodeNum[fromNum]
        assertNotNull(fromNode)
        assertEquals("Established", fromNode!!.user.long_name)
        assertEquals(differentPk, fromNode.publicKey)
        val otherNode = nodeManager.nodeDBbyNodeNum[otherNum]
        assertNotNull(otherNode)
        assertEquals("Other", otherNode!!.user.long_name)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 4. Multiple same-key matches

    @Test
    fun `ambiguous noncanonical duplicates update from entry and preserve every candidate`() {
        val nodeA = validPk.noncanonicalNum(1000)
        val nodeB = validPk.noncanonicalNum(2000)
        val fromNum = validPk.noncanonicalNum(3000)
        nodeManager.updateNode(nodeA) { makeKnownNode(nodeA, validPk, "Alpha") }
        nodeManager.updateNode(nodeB) { makeKnownNode(nodeB, validPk, "Bravo") }
        nodeManager.updateNode(fromNum) { makeKnownNode(fromNum, validPk, "Before") }
        enableDbWrites()

        val incomingUser =
            User(
                id = "!incoming",
                long_name = "Incoming",
                short_name = "INC",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(fromNum, incomingUser)
        testScope.advanceUntilIdle()

        // No candidate is removed, but future packets keep updating their own established fromNum entry.
        assertNotNull(nodeManager.nodeDBbyNodeNum[nodeA])
        assertNotNull(nodeManager.nodeDBbyNodeNum[nodeB])
        assertEquals("Incoming", nodeManager.nodeDBbyNodeNum[fromNum]?.user?.long_name)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `ambiguous noncanonical renumber installs identity into empty slot in memory`() {
        val previousNum = validPk.noncanonicalNum(3100)
        val fromNum = validPk.noncanonicalNum(3200)
        nodeManager.updateNode(previousNum) { makeKnownNode(previousNum, validPk, "Previous") }
        enableDbWrites()

        nodeManager.handleReceivedUser(
            fromNum,
            User(
                id = "!incoming",
                long_name = "Incoming",
                short_name = "INC",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            ),
        )
        testScope.advanceUntilIdle()

        assertNotNull(nodeManager.nodeDBbyNodeNum[previousNum])
        val incoming = assertNotNull(nodeManager.nodeDBbyNodeNum[fromNum])
        assertEquals("Incoming", incoming.user.long_name)
        assertEquals(validPk, incoming.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `ambiguous noncanonical renumber replaces telemetry placeholder without session lockout`() {
        val previousNum = validPk.noncanonicalNum(3300)
        val fromNum = validPk.noncanonicalNum(3400)
        nodeManager.updateNode(previousNum) { makeKnownNode(previousNum, validPk, "Previous") }
        nodeManager.handleReceivedPosition(
            fromNum = fromNum,
            myNodeNum = 9999,
            p = ProtoPosition(latitude_i = 123, longitude_i = 456),
            defaultTime = 1000L,
        )
        enableDbWrites()

        nodeManager.handleReceivedUser(
            fromNum,
            User(
                id = "!incoming",
                long_name = "First Identity",
                short_name = "ONE",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            ),
        )
        nodeManager.handleReceivedUser(
            fromNum,
            User(
                id = "!incoming",
                long_name = "Updated Identity",
                short_name = "TWO",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            ),
        )
        testScope.advanceUntilIdle()

        assertNotNull(nodeManager.nodeDBbyNodeNum[previousNum])
        val incoming = assertNotNull(nodeManager.nodeDBbyNodeNum[fromNum])
        assertEquals("Updated Identity", incoming.user.long_name)
        assertEquals(validPk, incoming.publicKey)
        assertEquals(123, incoming.position.latitude_i)
        assertEquals(456, incoming.position.longitude_i)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 5. Local authoritative renumber

    @Test
    fun `local authoritative renumber removes old same-key entries in memory upserts local no notification`() {
        val localNum = 5000
        val otherNum = 6000
        nodeManager.setMyNodeNum(localNum)
        nodeManager.updateNode(otherNum) { makeKnownNode(otherNum, validPk, "Old Same-Key") }
        enableDbWrites()

        val localUser =
            User(
                id = "!local",
                long_name = "Local Node",
                short_name = "LCL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(localNum, localUser)
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[otherNum])
        val localNode = nodeManager.nodeDBbyNodeNum[localNum]
        assertNotNull(localNode)
        assertEquals("Local Node", localNode!!.user.long_name)
        assertEquals(validPk, localNode.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 6. Genuine new node

    @Test
    fun `genuine new node fires exactly one upsert and exactly one notification`() {
        val newNodeNum = 3000
        val newPk = ByteArray(32) { (it + 100).toByte() }.toByteString()
        val newUser =
            User(
                id = "!newnode",
                long_name = "New Node",
                short_name = "NEW",
                hw_model = HardwareModel.TLORA_V2,
                public_key = newPk,
            )
        enableDbWrites()

        val captured = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(captured)) } returns true

        nodeManager.handleReceivedUser(newNodeNum, newUser)
        testScope.advanceUntilIdle()

        val result = nodeManager.nodeDBbyNodeNum[newNodeNum]
        assertNotNull(result)
        assertEquals("New Node", result!!.user.long_name)
        assertEquals(newPk, result.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeRepository.upsert(any()) }
        // Strengthen: capture the dispatched Notification and verify payload + routing fields, not just the call count.
        assertEquals(1, captured.size)
        val n = captured.first()
        assertEquals(newNodeNum, n.id)
        assertEquals("New Node", n.message)
        assertEquals(Notification.Category.NodeEvent, n.category)
        assertEquals("meshtastic://meshtastic/nodes/$newNodeNum", n.deepLinkUri)
    }

    // 7. Invalid / malformed keys (null, empty, ERROR) are treated as NoMatch

    @Test
    fun `invalid public keys skip identity matching and update normally`() {
        val existingNum = 7000
        val existingPk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        nodeManager.updateNode(existingNum) { makeKnownNode(existingNum, existingPk, "Canonical") }
        enableDbWrites()

        // Three packets at a different number without a validated public-key correlation hint.
        // None of them should match the canonical node at 7000 or trigger stale cleanup.
        val targetNum = 8000

        // (a) null key
        nodeManager.handleReceivedUser(
            targetNum,
            User(id = "!a", long_name = "A", short_name = "A", hw_model = HardwareModel.TLORA_V2),
        )
        testScope.advanceUntilIdle()

        // (b) empty key
        nodeManager.handleReceivedUser(
            targetNum,
            User(
                id = "!b",
                long_name = "B",
                short_name = "B",
                hw_model = HardwareModel.TLORA_V2,
                public_key = ByteString.EMPTY,
            ),
        )
        testScope.advanceUntilIdle()

        // (c) ERROR key
        nodeManager.handleReceivedUser(
            targetNum,
            User(
                id = "!c",
                long_name = "C",
                short_name = "C",
                hw_model = HardwareModel.TLORA_V2,
                public_key = Node.ERROR_BYTE_STRING,
            ),
        )
        testScope.advanceUntilIdle()

        // Canonical node untouched (no stale cleanup).
        assertNotNull(nodeManager.nodeDBbyNodeNum[existingNum])
    }

    // 8. Same number same key is a normal update, not a cross-number stale

    @Test
    fun `same number same key updates normally without stale classification`() {
        val nodeNum = 9000
        nodeManager.updateNode(nodeNum) { makeKnownNode(nodeNum, validPk, "Original") }
        enableDbWrites()

        val updatedUser =
            User(
                id = "!${nodeNum.toString(16)}",
                long_name = "Updated",
                short_name = "UPD",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(nodeNum, updatedUser)
        testScope.advanceUntilIdle()

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result)
        assertEquals("Updated", result!!.user.long_name)
        assertEquals(validPk, result.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeRepository.upsert(any()) }
    }

    // 9. Packet-driven duplicate cleanup stays entirely in memory.

    @Test
    fun `stale duplicate never deletes or upserts a repository row`() {
        val oldNum = validPk.noncanonicalNum(1000)
        val newNum = validPk.canonicalNum()
        nodeManager.updateNode(newNum) { makeKnownNode(newNum, validPk, "Canonical") }
        nodeManager.updateNode(oldNum) { makeKnownNode(oldNum, validPk, "Stale Dup") }
        enableDbWrites()

        val staleUser =
            User(
                id = "!old",
                long_name = "Stale Packet",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(oldNum, staleUser)
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[oldNum])
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
    }

    // 10. No notification for stale / conflict / local outcomes

    @Test
    fun `notification never fires for stale conflict or local outcomes`() {
        // Stale: stale packet at old number with established canonical elsewhere.
        val staleOld = validPk.noncanonicalNum(1000)
        val staleCanonical = validPk.canonicalNum()
        nodeManager.updateNode(staleCanonical) { makeKnownNode(staleCanonical, validPk, "Canonical") }
        nodeManager.updateNode(staleOld) { makeKnownNode(staleOld, validPk, "Old") }
        nodeManager.handleReceivedUser(
            staleOld,
            User(id = "!s", long_name = "S", short_name = "S", hw_model = HardwareModel.TLORA_V2, public_key = validPk),
        )

        // Conflict: different established identity at fromNum.
        val conflictFrom = 3000
        val conflictOther = 4000
        val conflictPk = ByteArray(32) { (it + 50).toByte() }.toByteString()
        nodeManager.updateNode(conflictOther) { makeKnownNode(conflictOther, validPk, "Other") }
        nodeManager.updateNode(conflictFrom) { makeKnownNode(conflictFrom, conflictPk, "Established") }
        nodeManager.handleReceivedUser(
            conflictFrom,
            User(id = "!c", long_name = "C", short_name = "C", hw_model = HardwareModel.TLORA_V2, public_key = validPk),
        )

        // Local: fromNum == myNodeNum, local-link authoritative update.
        val localNum = 5000
        val localGhost = 6000
        nodeManager.setMyNodeNum(localNum)
        nodeManager.updateNode(localGhost) { makeKnownNode(localGhost, validPk, "Ghost") }
        nodeManager.handleReceivedUser(
            localNum,
            User(id = "!l", long_name = "L", short_name = "L", hw_model = HardwareModel.TLORA_V2, public_key = validPk),
        )

        testScope.advanceUntilIdle()

        // No notification fired for any of the three outcomes.
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 11. byId consistency after stale removal — when stale and canonical share a user ID,
    // removing the stale node must not orphan the canonical node in the byId index.

    @Test
    fun `byId index points to canonical survivor after stale removal shares user id`() {
        val oldNum = validPk.noncanonicalNum(1000)
        val newNum = validPk.canonicalNum()
        val sharedUserId = "!shared"
        nodeManager.updateNode(newNum) {
            Node(
                num = newNum,
                user =
                User(
                    id = sharedUserId,
                    long_name = "Canonical",
                    short_name = "CAN",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = validPk,
                ),
                publicKey = validPk,
            )
        }
        nodeManager.updateNode(oldNum) {
            Node(
                num = oldNum,
                user =
                User(
                    id = sharedUserId,
                    long_name = "Stale",
                    short_name = "STL",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = validPk,
                ),
                publicKey = validPk,
            )
        }
        enableDbWrites()

        val staleUser =
            User(
                id = sharedUserId,
                long_name = "Stale",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validPk,
            )
        nodeManager.handleReceivedUser(oldNum, staleUser)
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[oldNum])
        val survivor = nodeManager.getNodeById(sharedUserId)
        assertNotNull(survivor)
        assertEquals(newNum, survivor!!.num)
    }

    // ── Additional identity reconciliation tests ────────────────────────────────

    // 12. Malformed key lengths (1, 31, 33) are rejected

    @Test
    fun `malformed key lengths 1 31 33 are rejected and skip identity matching`() {
        val nodeNum = 1234
        val shortKey = ByteArray(1) { 1 }.toByteString()
        val almostKey = ByteArray(31) { 2 }.toByteString()
        val longKey = ByteArray(33) { 3 }.toByteString()

        // All malformed keys should be treated as no-key (normal update path).
        nodeManager.handleReceivedUser(
            nodeNum,
            User(
                id = "!short",
                long_name = "S",
                short_name = "S",
                hw_model = HardwareModel.TLORA_V2,
                public_key = shortKey,
            ),
        )
        var result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(ByteString.EMPTY, result!!.publicKey)

        nodeManager.handleReceivedUser(
            nodeNum,
            User(
                id = "!almost",
                long_name = "A",
                short_name = "A",
                hw_model = HardwareModel.TLORA_V2,
                public_key = almostKey,
            ),
        )
        result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(ByteString.EMPTY, result!!.publicKey)

        nodeManager.handleReceivedUser(
            nodeNum,
            User(
                id = "!long",
                long_name = "L",
                short_name = "L",
                hw_model = HardwareModel.TLORA_V2,
                public_key = longKey,
            ),
        )
        result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(ByteString.EMPTY, result!!.publicKey)
    }

    // 13. Invalid Node.publicKey falls back to valid User.public_key

    @Test
    fun `invalid Node publicKey falls back to valid User public_key`() {
        val nodeNum = 1234
        val validUserKey = ByteArray(32) { 42 }.toByteString()
        val invalidNodeKey = ByteArray(16) { 1 }.toByteString() // Wrong size

        nodeManager.handleReceivedUser(
            nodeNum,
            User(
                id = "!test",
                long_name = "Test",
                short_name = "T",
                hw_model = HardwareModel.TLORA_V2,
                public_key = validUserKey,
            ),
        )
        // Manually set an invalid Node.publicKey
        nodeManager.updateNode(nodeNum) { it.copy(publicKey = invalidNodeKey) }

        // Public-key correlation should fall back to User.public_key.
        val node = nodeManager.nodeDBbyNodeNum[nodeNum]
        val resolvedKey = nodeManager.resolveNodePublicKeyHint(node!!)
        assertEquals(validUserKey, resolvedKey)
    }

    // 14. UNSET hardware with a different valid key is preserved (conflict)

    @Test
    fun `UNSET hardware with different valid key is preserved as conflict`() {
        val canonicalNum = 1000
        val conflictNum = 2000
        val canonicalKey = ByteArray(32) { 10 }.toByteString()
        val conflictKey = ByteArray(32) { 20 }.toByteString()

        // Canonical node with established identity
        nodeManager.updateNode(canonicalNum) { makeKnownNode(canonicalNum, canonicalKey, "Canonical") }

        // Conflict node with UNSET hardware but different valid key
        nodeManager.updateNode(conflictNum) {
            Node(
                num = conflictNum,
                user =
                User(
                    id = "!conflict",
                    long_name = "Conflict",
                    short_name = "CON",
                    hw_model = HardwareModel.UNSET,
                    public_key = conflictKey,
                ),
                publicKey = conflictKey,
            )
        }

        // Packet at conflictNum with canonicalKey should be treated as conflict (preserve both)
        nodeManager.handleReceivedUser(
            conflictNum,
            User(
                id = "!c",
                long_name = "C",
                short_name = "C",
                hw_model = HardwareModel.TLORA_V2,
                public_key = canonicalKey,
            ),
        )

        // Both nodes should still exist
        assertNotNull(nodeManager.nodeDBbyNodeNum[canonicalNum])
        assertNotNull(nodeManager.nodeDBbyNodeNum[conflictNum])
        assertEquals(canonicalKey, nodeManager.nodeDBbyNodeNum[canonicalNum]!!.publicKey)
        assertEquals(conflictKey, nodeManager.nodeDBbyNodeNum[conflictNum]!!.publicKey)
    }

    // 15. Custom incomplete identity is preserved

    @Test
    fun `custom incomplete identity is preserved over default incoming`() {
        val nodeNum = 1234
        val customUser =
            User(
                id = "!custom",
                long_name = "My Custom Name",
                short_name = "MCN",
                hw_model = HardwareModel.UNSET, // Incomplete hardware
            )
        nodeManager.updateNode(nodeNum) { it.copy(user = customUser) }

        // Incoming default user should not overwrite custom identity
        val defaultUser =
            User(
                id = NodeAddress.numToDefaultId(nodeNum),
                long_name = "Meshtastic ${nodeNum.toHex().takeLast(4)}",
                short_name = nodeNum.toHex().takeLast(4),
                hw_model = HardwareModel.UNSET,
            )
        nodeManager.handleReceivedUser(nodeNum, defaultUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("My Custom Name", result!!.user.long_name)
        assertEquals("MCN", result.user.short_name)
    }

    // 16. Three nodes sharing one user ID

    @Test
    fun `three nodes sharing one user ID selects deterministic representative`() {
        val userId = "!shared"
        val node1 = 1000
        val node2 = 2000
        val node3 = 3000

        // All three have the same user ID but different node numbers
        nodeManager.updateNode(node1) {
            Node(
                num = node1,
                user = User(id = userId, long_name = "N1", short_name = "N1", hw_model = HardwareModel.UNSET),
            )
        }
        nodeManager.updateNode(node2) {
            Node(
                num = node2,
                user = User(id = userId, long_name = "N2", short_name = "N2", hw_model = HardwareModel.TLORA_V2),
            )
        }
        nodeManager.updateNode(node3) {
            Node(
                num = node3,
                user = User(id = userId, long_name = "N3", short_name = "N3", hw_model = HardwareModel.UNSET),
            )
        }

        // byId should point to node2 (non-placeholder, lowest among non-placeholders)
        val representative = nodeManager.getNodeById(userId)
        assertNotNull(representative)
        assertEquals(node2, representative!!.num)
    }

    // 17. Old-ID survivor restoration after put

    @Test
    fun `old ID survivor restored after put changes user ID`() {
        val nodeNum = 1234
        val oldUserId = "!old"
        val newUserId = "!new"
        val survivorNum = 5678

        // Two nodes with old user ID
        nodeManager.updateNode(nodeNum) {
            Node(
                num = nodeNum,
                user = User(id = oldUserId, long_name = "Old", short_name = "OLD", hw_model = HardwareModel.TLORA_V2),
            )
        }
        nodeManager.updateNode(survivorNum) {
            Node(
                num = survivorNum,
                user =
                User(id = oldUserId, long_name = "Survivor", short_name = "SUR", hw_model = HardwareModel.TLORA_V2),
            )
        }

        // Change nodeNum's user ID
        nodeManager.updateNode(nodeNum) {
            it.copy(user = it.user.copy(id = newUserId, long_name = "New", short_name = "NEW"))
        }

        // byId for oldUserId should now point to survivorNum
        val survivor = nodeManager.getNodeById(oldUserId)
        assertNotNull(survivor)
        assertEquals(survivorNum, survivor!!.num)

        // byId for newUserId should point to nodeNum
        val newNode = nodeManager.getNodeById(newUserId)
        assertNotNull(newNode)
        assertEquals(nodeNum, newNode!!.num)
    }

    // 18. fromByNum insertion-order independence

    @Test
    fun `fromByNum is independent of insertion order`() {
        val userId = "!shared"
        val node1 = 1000
        val node2 = 2000

        val nodes12 =
            mapOf(
                node1 to
                    Node(
                        num = node1,
                        user = User(id = userId, long_name = "N1", short_name = "N1", hw_model = HardwareModel.UNSET),
                    ),
                node2 to
                    Node(
                        num = node2,
                        user = User(
                            id = userId,
                            long_name = "N2",
                            short_name = "N2",
                            hw_model = HardwareModel.TLORA_V2,
                        ),
                    ),
            )
        val nodes21 =
            mapOf(
                node2 to
                    Node(
                        num = node2,
                        user = User(
                            id = userId,
                            long_name = "N2",
                            short_name = "N2",
                            hw_model = HardwareModel.TLORA_V2,
                        ),
                    ),
                node1 to
                    Node(
                        num = node1,
                        user = User(id = userId, long_name = "N1", short_name = "N1", hw_model = HardwareModel.UNSET),
                    ),
            )

        val index12 = NodeManagerImpl.NodeIndex.fromByNum(nodes12)
        val index21 = NodeManagerImpl.NodeIndex.fromByNum(nodes21)

        // Both should produce the same byId representative (node2, non-placeholder)
        assertEquals(index12.byId[userId]!!.num, index21.byId[userId]!!.num)
        assertEquals(node2, index12.byId[userId]!!.num)
    }

    // 19. Preferred canonical mapping after stale removal

    @Test
    fun `preferred canonical remains mapped after stale removal`() {
        val userId = "!shared"
        val canonicalKey = ByteArray(32) { 41 }.toByteString()
        val competingKey = ByteArray(32) { 42 }.toByteString()
        val preferredNum = canonicalKey.canonicalNum()
        val competingNum = 500
        val staleNum = canonicalKey.noncanonicalNum(1000)

        nodeManager.updateNode(competingNum) {
            Node(
                num = competingNum,
                user =
                User(
                    id = userId,
                    long_name = "Fallback Winner",
                    short_name = "FBK",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = competingKey,
                ),
                publicKey = competingKey,
            )
        }
        nodeManager.updateNode(preferredNum) {
            Node(
                num = preferredNum,
                user =
                User(
                    id = userId,
                    long_name = "Preferred",
                    short_name = "PRE",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = canonicalKey,
                ),
                publicKey = canonicalKey,
            )
        }
        nodeManager.updateNode(staleNum) {
            Node(
                num = staleNum,
                user =
                User(
                    id = userId,
                    long_name = "Stale",
                    short_name = "STL",
                    hw_model = HardwareModel.TLORA_V2,
                    public_key = canonicalKey,
                ),
                publicKey = canonicalKey,
            )
        }

        nodeManager.handleReceivedUser(
            staleNum,
            User(
                id = userId,
                long_name = "Stale Replay",
                short_name = "STL",
                hw_model = HardwareModel.TLORA_V2,
                public_key = canonicalKey,
            ),
        )

        assertNull(nodeManager.nodeDBbyNodeNum[staleNum])
        assertNotNull(nodeManager.nodeDBbyNodeNum[competingNum])
        assertNotNull(nodeManager.nodeDBbyNodeNum[preferredNum])
        val representative = nodeManager.getNodeById(userId)
        assertNotNull(representative)
        assertEquals(preferredNum, representative!!.num)
    }

    // 20. Accepted local node remains the byId representative

    @Test
    fun `accepted local node remains byId representative`() {
        val userId = "!shared"
        val remoteNum = 1000
        val localNum = 2000

        nodeManager.setMyNodeNum(localNum)
        nodeManager.updateNode(localNum) {
            Node(
                num = localNum,
                user = User(id = userId, long_name = "Local", short_name = "LOC", hw_model = HardwareModel.TLORA_V2),
            )
        }
        nodeManager.updateNode(remoteNum) {
            Node(
                num = remoteNum,
                user = User(id = userId, long_name = "Remote", short_name = "REM", hw_model = HardwareModel.TLORA_V2),
            )
        }

        assertEquals(remoteNum, nodeManager.getNodeById(userId)!!.num)
        nodeManager.handleReceivedUser(
            localNum,
            User(id = userId, long_name = "Local Updated", short_name = "LOC", hw_model = HardwareModel.TLORA_V2),
        )

        assertNotNull(nodeManager.nodeDBbyNodeNum[remoteNum])
        val representative = nodeManager.getNodeById(userId)
        assertNotNull(representative)
        assertEquals(localNum, representative!!.num)
    }

    // 21. Exact notification title, message, ID, category, and deep link

    @Test
    fun `notification has exact title message ID category and deep link`() {
        enableDbWrites()
        val nodeNum = 1234
        val user = User(id = "!test", long_name = "Test User", short_name = "TST", hw_model = HardwareModel.TLORA_V2)

        nodeManager.handleReceivedUser(nodeNum, user)
        testScope.advanceUntilIdle()

        verifySuspend {
            notificationManager.dispatch(
                Notification(
                    title = "New node seen: TST",
                    message = "Test User",
                    category = Notification.Category.NodeEvent,
                    id = nodeNum,
                    deepLinkUri = "meshtastic://meshtastic/nodes/$nodeNum",
                ),
            )
        }
    }

    // 22. Incoming at the canonical num reconciles in memory only and preserves its placeholder history.

    @Test
    fun `incoming canonical replaces own placeholder and removes noncanonical in memory only`() {
        val canonicalKey = ByteArray(32) { (it + 7).toByte() }.toByteString()
        val canonicalNum = canonicalKey.canonicalNum()
        val staleNum = canonicalKey.noncanonicalNum(7777)
        // Pre-existing entry at the noncanonical num carries the same key.
        nodeManager.updateNode(staleNum) { makeKnownNode(staleNum, canonicalKey, "Ghost") }
        // Placeholder at the canonical num so the incoming slot is "open" for reconciliation.
        val placeholderId = NodeAddress.numToDefaultId(canonicalNum)
        nodeManager.updateNode(canonicalNum) {
            Node(
                num = canonicalNum,
                user =
                User(
                    id = placeholderId,
                    long_name = "Meshtastic ${placeholderId.takeLast(4)}",
                    short_name = placeholderId.takeLast(4),
                    hw_model = HardwareModel.UNSET,
                ),
                publicKey = ByteString.EMPTY,
                position = ProtoPosition(latitude_i = 111, longitude_i = 222),
                channel = 4,
            )
        }
        enableDbWrites()

        nodeManager.handleReceivedUser(
            canonicalNum,
            User(
                id = "!canonical",
                long_name = "Canonical",
                short_name = "CAN",
                hw_model = HardwareModel.TLORA_V2,
                public_key = canonicalKey,
            ),
        )
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[staleNum])
        val canonical = nodeManager.nodeDBbyNodeNum[canonicalNum]
        assertNotNull(canonical)
        assertEquals("Canonical", canonical!!.user.long_name)
        assertEquals(canonicalKey, canonical.publicKey)
        assertEquals(111, canonical.position.latitude_i)
        assertEquals(222, canonical.position.longitude_i)
        assertEquals(4, canonical.channel)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 23. Incoming canonical absent from index entirely creates from packet, removes noncanonical, no notify.

    @Test
    fun `incoming canonical absent from index creates from packet removes noncanonical no notify`() {
        val canonicalKey = ByteArray(32) { (it + 17).toByte() }.toByteString()
        val canonicalNum = canonicalKey.canonicalNum()
        val staleNum = canonicalKey.noncanonicalNum(8888)
        nodeManager.updateNode(staleNum) { makeKnownNode(staleNum, canonicalKey, "Stale Noncanonical") }
        enableDbWrites()

        nodeManager.handleReceivedUser(
            canonicalNum,
            User(
                id = "!fresh",
                long_name = "Fresh Canonical",
                short_name = "FRS",
                hw_model = HardwareModel.TLORA_V2,
                public_key = canonicalKey,
            ),
        )
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[staleNum])
        val canonical = nodeManager.nodeDBbyNodeNum[canonicalNum]
        assertNotNull(canonical)
        assertEquals("Fresh Canonical", canonical!!.user.long_name)
        assertEquals(canonicalKey, canonical.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 24. Neither num canonical preserves both with no side effects.

    @Test
    fun `neither num canonical preserves both no persistence no notification`() {
        val key = ByteArray(32) { (it + 23).toByte() }.toByteString()
        // Pick two nums guaranteed distinct from the canonical num and from each other.
        val canonicalNum = key.canonicalNum()
        val a = if (canonicalNum != 1111) 1111 else 1112
        val b = if (canonicalNum != 2222) 2222 else 2223

        nodeManager.updateNode(a) { makeKnownNode(a, key, "Alpha") }
        nodeManager.updateNode(b) { makeKnownNode(b, key, "Bravo") }
        enableDbWrites()

        nodeManager.handleReceivedUser(
            a,
            User(
                id = "!a",
                long_name = "Alpha Packet",
                short_name = "ALA",
                hw_model = HardwareModel.TLORA_V2,
                public_key = key,
            ),
        )
        testScope.advanceUntilIdle()

        // Both are preserved; the incoming packet still updates its own established same-key slot in memory.
        val alpha = nodeManager.nodeDBbyNodeNum[a]
        val bravo = nodeManager.nodeDBbyNodeNum[b]
        assertNotNull(alpha)
        assertNotNull(bravo)
        assertEquals("Alpha Packet", alpha!!.user.long_name)
        assertEquals("Bravo", bravo!!.user.long_name)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 25. Different established valid key at the canonical num is preserved as a conflict.

    @Test
    fun `different established key at canonical num preserved as conflict`() {
        val canonicalKey = ByteArray(32) { (it + 31).toByte() }.toByteString()
        val establishedKey = ByteArray(32) { (it + 32).toByte() }.toByteString()
        val canonicalNum = canonicalKey.canonicalNum()
        val noncanonicalNum = canonicalKey.noncanonicalNum(3333)
        nodeManager.updateNode(noncanonicalNum) { makeKnownNode(noncanonicalNum, canonicalKey, "Noncanonical Holder") }
        // Canonical num is occupied by a node with a DIFFERENT valid key.
        nodeManager.updateNode(canonicalNum) { makeKnownNode(canonicalNum, establishedKey, "Established") }
        enableDbWrites()

        nodeManager.handleReceivedUser(
            canonicalNum,
            User(
                id = "!c",
                long_name = "Claimant",
                short_name = "CLM",
                hw_model = HardwareModel.TLORA_V2,
                public_key = canonicalKey,
            ),
        )
        testScope.advanceUntilIdle()

        // Conflict: both preserved with their original keys; no persistence, no notification.
        val atCanonical = nodeManager.nodeDBbyNodeNum[canonicalNum]
        val atNoncanonical = nodeManager.nodeDBbyNodeNum[noncanonicalNum]
        assertNotNull(atCanonical)
        assertNotNull(atNoncanonical)
        assertEquals(establishedKey, atCanonical!!.publicKey)
        assertEquals("Established", atCanonical.user.long_name)
        assertEquals(canonicalKey, atNoncanonical!!.publicKey)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    // 26. Local classification and the node index share one atomic CAS state.

    @Test
    fun `local number change during User reduction retries classification atomically`() {
        val key = ByteArray(32) { (it + 37).toByte() }.toByteString()
        val previousNum = key.canonicalNum()
        val newLocalNum = key.noncanonicalNum(9999)
        nodeManager.updateNode(previousNum) { makeKnownNode(previousNum, key, "Previous") }
        enableDbWrites()
        nodeManager.receivedUserReductionHook = {
            nodeManager.receivedUserReductionHook = null
            nodeManager.setMyNodeNum(newLocalNum)
        }

        nodeManager.handleReceivedUser(
            newLocalNum,
            User(
                id = "!local9999",
                long_name = "Current Local",
                short_name = "LOC",
                hw_model = HardwareModel.TLORA_V2,
                public_key = key,
            ),
        )
        testScope.advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[previousNum])
        assertEquals("Current Local", nodeManager.nodeDBbyNodeNum[newLocalNum]?.user?.long_name)
        assertEquals(newLocalNum, nodeManager.myNodeNum.value)
        verifyNoRepositoryDeletion()
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeRepository.upsert(any()) }
        verifySuspend(mode = VerifyMode.not) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `reverse ID and public-key indexes stay consistent across put replacement and remove`() {
        val keyA = ByteArray(32) { 11 }.toByteString()
        val keyB = ByteArray(32) { 12 }.toByteString()
        val sharedId = "!shared"
        val replacementId = "!replacement"
        var index = NodeManagerImpl.NodeIndex()
        index = index.put(1, makeKnownNode(1, keyA).copy(user = makeKnownNode(1, keyA).user.copy(id = sharedId)))
        index = index.put(2, makeKnownNode(2, keyA).copy(user = makeKnownNode(2, keyA).user.copy(id = sharedId)))

        assertEquals<Set<Int>?>(setOf(1, 2), index.candidateNumsById[sharedId])
        assertEquals<Set<Int>?>(setOf(1, 2), index.candidateNumsByPublicKey[keyA])
        assertEquals(1, index.byId[sharedId]?.num)

        val replacement = makeKnownNode(1, keyB).copy(user = makeKnownNode(1, keyB).user.copy(id = replacementId))
        index = index.put(1, replacement)
        assertEquals<Set<Int>?>(setOf(2), index.candidateNumsById[sharedId])
        assertEquals<Set<Int>?>(setOf(1), index.candidateNumsById[replacementId])
        assertEquals<Set<Int>?>(setOf(2), index.candidateNumsByPublicKey[keyA])
        assertEquals<Set<Int>?>(setOf(1), index.candidateNumsByPublicKey[keyB])
        assertEquals(2, index.byId[sharedId]?.num)
        assertEquals(1, index.byId[replacementId]?.num)

        index = index.remove(2)
        assertNull(index.candidateNumsById[sharedId])
        assertNull(index.candidateNumsByPublicKey[keyA])
        assertNull(index.byId[sharedId])
        assertEquals(1, index.byId[replacementId]?.num)
    }

    private fun Int.toHex(): String = this.toString(16).padStart(8, '0')

    private fun userWithKey(key: ByteString, longName: String, shortName: String): User = User(
        id = key.hex(),
        long_name = longName,
        short_name = shortName,
        hw_model = HardwareModel.TLORA_V2,
        public_key = key,
    )

    // ---------- Retired-node notification behavior ----------

    @Test
    fun `applyTrustedIdentityMigrations cancels notifications for each retired number`() = testScope.runTest {
        // Setup: create nodes with distinct identities
        val num1 = 1230588578
        val num2 = 1111111111
        val key1 = ByteArray(32) { 0x01 }.toByteString()
        val key2 = ByteArray(32) { 0x02 }.toByteString()
        nodeManager.handleReceivedUser(num1, userWithKey(key1, "Node1", "N1"), manuallyVerified = false)
        nodeManager.handleReceivedUser(num2, userWithKey(key2, "Node2", "N2"), manuallyVerified = false)
        advanceUntilIdle()

        // Act
        nodeManager.applyTrustedIdentityMigrations(listOf(num1, num2))
        advanceUntilIdle()

        // Assert
        verify(VerifyMode.atLeast(1)) { notificationManager.cancel(num1) }
        verify(VerifyMode.atLeast(1)) { notificationManager.cancel(num2) }
    }

    @Test
    fun `trusted migration commits retirement before notification cancellation`() = testScope.runTest {
        val num = 310000000
        val key = ByteArray(32) { 0x12 }.toByteString()
        nodeManager.handleReceivedUser(num, userWithKey(key, "Retiring", "RT"), manuallyVerified = false)
        advanceUntilIdle()
        var nodePresentAtCancellation: Boolean? = null
        every { notificationManager.cancel(num) } calls
            {
                nodePresentAtCancellation = num in nodeManager.nodeDBbyNodeNum
            }

        nodeManager.applyTrustedIdentityMigrations(listOf(num))

        assertEquals(false, nodePresentAtCancellation, "retirement must commit before cancellation side effects")
        assertNull(nodeManager.nodeDBbyNodeNum[num])
        verify(VerifyMode.exactly(1)) { notificationManager.cancel(num) }
    }

    @Test
    fun `notification skipped when number retired between select and dispatch`() = testScope.runTest {
        val num = 4000000000.toInt()
        val key = ByteArray(32) { 0x03 }.toByteString()
        val titleStarted = CompletableDeferred<Unit>()
        val releaseTitle = CompletableDeferred<Unit>()
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        nodeManager.notificationTitleFormatter = { shortName ->
            titleStarted.complete(Unit)
            releaseTitle.await()
            "New node seen: $shortName"
        }

        nodeManager.handleReceivedUser(num, userWithKey(key, "NewNode", "NN"), manuallyVerified = false)
        titleStarted.await()
        nodeManager.applyTrustedIdentityMigrations(listOf(num))
        releaseTitle.complete(Unit)
        advanceUntilIdle()

        assertTrue(dispatched.none { it.id == num && it.message == "NewNode" })
    }

    @Test
    fun `notification skipped when number becomes local node before dispatch`() = testScope.runTest {
        val num = 5000000000.toInt()
        val key = ByteArray(32) { 0x04 }.toByteString()
        val titleStarted = CompletableDeferred<Unit>()
        val releaseTitle = CompletableDeferred<Unit>()
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        nodeManager.notificationTitleFormatter = { shortName ->
            titleStarted.complete(Unit)
            releaseTitle.await()
            "New node seen: $shortName"
        }

        nodeManager.handleReceivedUser(num, userWithKey(key, "NewNode", "NN"), manuallyVerified = false)
        titleStarted.await()
        nodeManager.setMyNodeNum(num)
        releaseTitle.complete(Unit)
        advanceUntilIdle()

        assertTrue(dispatched.none { it.id == num && it.message == "NewNode" })
    }

    @Test
    fun `notification skipped when identity changed before dispatch`() = testScope.runTest {
        val num = 6000000000.toInt()
        val key1 = ByteArray(32) { 0x05 }.toByteString()
        val key2 = ByteArray(32) { 0x06 }.toByteString()
        val titleStarted = CompletableDeferred<Unit>()
        val releaseTitle = CompletableDeferred<Unit>()
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        nodeManager.notificationTitleFormatter = { shortName ->
            titleStarted.complete(Unit)
            releaseTitle.await()
            "New node seen: $shortName"
        }

        nodeManager.handleReceivedUser(num, userWithKey(key1, "First", "F1"), manuallyVerified = false)
        titleStarted.await()
        nodeManager.updateNode(num) { makeKnownNode(num, key2, "Other") }
        releaseTitle.complete(Unit)
        advanceUntilIdle()

        val node = nodeManager.nodeDBbyNodeNum[num]
        assertEquals("Other", node?.user?.long_name)
        assertTrue(dispatched.none { it.message == "First" }, "notification for 'First' must be suppressed")
    }

    @Test
    fun `retired number with same key suppressed even without canonical key candidate`() = testScope.runTest {
        val oldNum = 7000000000.toInt()
        val key = ByteArray(32) { 0x07 }.toByteString()
        // Create node at oldNum with key, then retire it via migration
        nodeManager.handleReceivedUser(oldNum, userWithKey(key, "OldNode", "ON"), manuallyVerified = false)
        advanceUntilIdle()
        nodeManager.applyTrustedIdentityMigrations(listOf(oldNum))
        advanceUntilIdle()
        val replayDispatches = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(replayDispatches)) } returns true
        // Now replay: the canonical number (crc32(key)) has NOT appeared yet
        nodeManager.handleReceivedUser(oldNum, userWithKey(key, "Replay", "RP"), manuallyVerified = false)
        advanceUntilIdle()
        // The replayed node should NOT appear in nodeDBbyNodeNum at oldNum
        assertNull(nodeManager.nodeDBbyNodeNum[oldNum])
        assertTrue(replayDispatches.none { it.id == oldNum })
    }

    @Test
    fun `keyless retired number rejects an unrepresented valid key for the session`() = testScope.runTest {
        val num = 7100000000.toInt()
        val incomingKey = ByteArray(32) { 0x17 }.toByteString()
        nodeManager.applyTrustedIdentityMigrations(listOf(num))

        nodeManager.handleReceivedUser(
            num,
            userWithKey(incomingKey, "Untrusted Reuse", "UR"),
            manuallyVerified = false,
        )
        advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[num])
        verifySuspend(mode = VerifyMode.exactly(0)) { notificationManager.dispatch(any()) }
    }

    @Test
    fun `retired number with different valid key can be legitimately reused`() = testScope.runTest {
        val num = 8000000000.toInt()
        val oldKey = ByteArray(32) { 0x08 }.toByteString()
        val newKey = ByteArray(32) { 0x09 }.toByteString()
        // Create and retire with old key
        nodeManager.handleReceivedUser(num, userWithKey(oldKey, "Old", "OD"), manuallyVerified = false)
        advanceUntilIdle()
        nodeManager.applyTrustedIdentityMigrations(listOf(num))
        advanceUntilIdle()
        // Capture dispatches to verify the reuse notification specifically,
        // not the initial sighting from the setup above.
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        // Replay with different key — should be allowed as legitimate reuse
        nodeManager.handleReceivedUser(num, userWithKey(newKey, "New", "NW"), manuallyVerified = false)
        advanceUntilIdle()
        val newNode = nodeManager.nodeDBbyNodeNum[num]
        assertNotNull(newNode)
        assertEquals("New", newNode.user.long_name)
        assertEquals(
            1,
            dispatched.count { it.id == num && it.message == "New" },
            "exactly one notification for the reuse at $num",
        )
    }

    @Test
    fun `legitimate reuse clears prior retirement hint before a later keyless retirement`() = testScope.runTest {
        val num = 8100000000.toInt()
        val oldKey = ByteArray(32) { 0x18 }.toByteString()
        val reuseKey = ByteArray(32) { 0x19 }.toByteString()
        val untrustedKey = ByteArray(32) { 0x1a }.toByteString()
        nodeManager.handleReceivedUser(num, userWithKey(oldKey, "Old", "OD"), manuallyVerified = false)
        advanceUntilIdle()
        nodeManager.applyTrustedIdentityMigrations(listOf(num))
        nodeManager.handleReceivedUser(num, userWithKey(reuseKey, "Reuse", "RE"), manuallyVerified = false)
        advanceUntilIdle()
        assertEquals("Reuse", nodeManager.nodeDBbyNodeNum[num]?.user?.long_name)

        nodeManager.removeByNodenum(num)
        nodeManager.applyTrustedIdentityMigrations(listOf(num))
        nodeManager.handleReceivedUser(num, userWithKey(untrustedKey, "Untrusted", "UN"), manuallyVerified = false)
        advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[num], "an absent trusted migration must not retain an obsolete hint")
    }

    @Test
    fun `public key diagnostics use a one-way fingerprint`() {
        val key = ByteArray(32) { it.toByte() }.toByteString()
        val fingerprint = publicKeyLogFingerprint(key)

        assertEquals(key.sha256().hex().take(8), fingerprint)
        assertFalse(fingerprint == key.hex().take(8), "diagnostics must not expose a raw public-key prefix")
    }

    @Test
    fun `complete 2_7 to 2_8 renumbering produces no old number notification`() = testScope.runTest {
        val oldNum = -297114191
        val newNum = 1230588578
        val key = ByteArray(32) { 0x0a }.toByteString()
        // Simulate: old 2.7 local node identity
        nodeManager.setMyNodeNum(oldNum)
        nodeManager.handleReceivedUser(oldNum, userWithKey(key, "OldLocal", "OL"), manuallyVerified = false)
        advanceUntilIdle()
        // Trusted migration retires old number, canonical identity moves to newNum
        nodeManager.setMyNodeNum(newNum)
        nodeManager.handleReceivedUser(newNum, userWithKey(key, "NewLocal", "NL"), manuallyVerified = false)
        nodeManager.applyTrustedIdentityMigrations(listOf(oldNum))
        advanceUntilIdle()
        // Early replay of old number User packet — should be suppressed
        val dispatchedBefore = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatchedBefore)) } returns true
        nodeManager.handleReceivedUser(oldNum, userWithKey(key, "Replay", "RP"), manuallyVerified = false)
        advanceUntilIdle()
        // The old number should NOT be in nodeDB
        assertNull(nodeManager.nodeDBbyNodeNum[oldNum])
        // No notification should have been dispatched for the old number replay
        // (the notification dispatch was captured; check none is for oldNum)
        val oldNumNotifications = dispatchedBefore.filter { it.id == oldNum }
        assertTrue(oldNumNotifications.isEmpty(), "No notification should be dispatched for retired oldNum")
    }

    @Test
    fun `genuine remote first sighting emits exactly one notification`() = testScope.runTest {
        val num = 9000000000.toInt()
        val key = ByteArray(32) { 0x0b }.toByteString()
        nodeManager.setMyNodeNum(1230588578)
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        nodeManager.handleReceivedUser(num, userWithKey(key, "Genuine Remote", "GR"), manuallyVerified = false)
        advanceUntilIdle()
        val numNotifications = dispatched.filter { it.id == num }
        assertEquals(1, numNotifications.size, "Exactly one notification for genuine new node")
    }

    // ── Phase 8 — session-safe node cache loading & trusted-hint survival ─────────

    @Test
    fun `direct snapshot returns only the newly selected DB after a currentDb switch`() = testScope.runTest {
        // Simulate the race that motivated Phase 6: the process-wide nodeDBbyNum StateFlow (a stateIn cache) still
        // holds the PREVIOUS database's node map right after a currentDb switch, but the direct snapshot read
        // returns
        // only the newly selected DB's nodes.
        val staleNum = 1001
        val freshNum = 2002
        val staleNode = makeKnownNode(staleNum, validPk, "Old DB")
        val freshNode = makeKnownNode(freshNum, validPk, "New DB")

        // Stale StateFlow cache (old DB still reflected) vs. the direct snapshot (new DB only).
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(staleNum to staleNode))
        everySuspend { nodeRepository.getNodeDbSnapshot() } returns mapOf(freshNum to freshNode)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        nodeManager.loadCachedNodeDB()
        advanceUntilIdle()

        // The cache loader consumed the direct snapshot, so staleNum never entered the live index and freshNum did.
        assertNull(nodeManager.nodeDBbyNodeNum[staleNum], "stale-DB row must not leak into the live index")
        val loaded = nodeManager.nodeDBbyNodeNum[freshNum]
        assertNotNull(loaded)
        assertEquals("New DB", loaded!!.user.long_name)
    }

    @Test
    fun `loadCachedNodeDB result is discarded when clear starts a new session mid-load`() = testScope.runTest {
        // Pause the load after the snapshot read so we can race a clear() against it before commit. We use the
        // repository snapshot call as the suspension point.
        val oldNum = 3003
        val oldNode = makeKnownNode(oldNum, validPk, "Old")
        val snapshotRead = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.getNodeDbSnapshot() } calls
            {
                snapshotRead.complete(Unit)
                snapshotRelease.await()
                mapOf(oldNum to oldNode)
            }
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        nodeManager.loadCachedNodeDB()
        snapshotRead.await()
        // While the load is paused after its read, a new session starts. This must discard the old snapshot.
        nodeManager.clear()
        // Add a node AFTER clear so we can verify the discarded load did not wipe it on commit.
        val liveNum = 4004
        nodeManager.updateNode(liveNum) { makeKnownNode(liveNum, validPk, "Live After Clear") }
        snapshotRelease.complete(Unit)
        advanceUntilIdle()

        // The old-DB snapshot was discarded completely: oldNum is absent, liveNum survives.
        assertNull(
            nodeManager.nodeDBbyNodeNum[oldNum],
            "stale snapshot from the previous session must be discarded",
        )
        val live = nodeManager.nodeDBbyNodeNum[liveNum]
        assertNotNull(live)
        assertEquals("Live After Clear", live!!.user.long_name)
    }

    @Test
    fun `live node update during the load window is not overwritten by the snapshot`() = testScope.runTest {
        val num = 5005
        val staleSnapshotNode = makeKnownNode(num, validPk, "Stale Snapshot").copy(lastHeard = 100)
        val liveUpdatedNode = makeKnownNode(num, validPk, "Live Updated").copy(lastHeard = 200)

        val snapshotRead = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.getNodeDbSnapshot() } calls
            {
                snapshotRead.complete(Unit)
                snapshotRelease.await()
                mapOf(num to staleSnapshotNode)
            }
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        nodeManager.loadCachedNodeDB()
        snapshotRead.await()
        // A live update arrives between the snapshot capture and its commit. The live value (with fresher fields)
        // must win over the snapshot row at the same num.
        nodeManager.updateNode(num) { liveUpdatedNode }
        snapshotRelease.complete(Unit)
        advanceUntilIdle()

        val result = nodeManager.nodeDBbyNodeNum[num]
        assertNotNull(result)
        assertEquals("Live Updated", result!!.user.long_name)
        assertEquals(200, result.lastHeard, "live mutation field must not be overwritten by the snapshot")
    }

    @Test
    fun `trusted migration during the load window retires absent number and suppresses replay`() = testScope.runTest {
        val num = 6006
        val snapshotNode = makeKnownNode(num, validPk, "Pre-migration")

        val snapshotRead = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.getNodeDbSnapshot() } calls
            {
                snapshotRead.complete(Unit)
                snapshotRelease.await()
                mapOf(num to snapshotNode)
            }
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        nodeManager.loadCachedNodeDB()
        snapshotRead.await()
        // Trusted migration runs while the load is suspended. The migrated number is absent at this point, so its
        // hint is captured as "absent" — but the retirement must still survive and suppress a stale replay even
        // after
        // the load commits.
        nodeManager.applyTrustedIdentityMigrations(listOf(num))
        snapshotRelease.complete(Unit)
        advanceUntilIdle()

        assertNull(
            nodeManager.nodeDBbyNodeNum[num],
            "retired number absent at migration time stays retired post-load",
        )

        // Stale same-key replay still suppressed, no notification dispatched for the retired old number.
        val dispatched = mutableListOf<Notification>()
        everySuspend { notificationManager.dispatch(capture(dispatched)) } returns true
        nodeManager.handleReceivedUser(num, userWithKey(validPk, "Replay", "RP"), manuallyVerified = false)
        advanceUntilIdle()

        assertNull(nodeManager.nodeDBbyNodeNum[num])
        assertTrue(dispatched.none { it.id == num }, "no notification for retired-number replay")
    }

    @Test
    fun `persisted local node fills null identity when live nodes change during load`() = testScope.runTest {
        val persistedNum = 7007
        val liveNum = 8008
        val liveKey = ByteArray(32) { (it + 90).toByte() }.toByteString()
        val persistedInfo =
            MyNodeInfo(
                myNodeNum = persistedNum,
                hasGPS = false,
                model = null,
                firmwareVersion = "2.5.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 0L,
                messageTimeoutMsec = 0,
                minAppVersion = 0,
                maxChannels = 0,
                hasWifi = false,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = null,
            )
        val snapshotRead = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.getNodeDbSnapshot() } calls
            {
                snapshotRead.complete(Unit)
                snapshotRelease.await()
                mapOf(persistedNum to makeKnownNode(persistedNum, validPk, "Persisted"))
            }
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(persistedInfo)

        nodeManager.loadCachedNodeDB()
        snapshotRead.await()
        nodeManager.updateNode(liveNum) { makeKnownNode(liveNum, liveKey, "Live") }
        snapshotRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            persistedNum,
            nodeManager.myNodeNum.value,
            "the merge path must restore persisted local identity",
        )
        assertNotNull(nodeManager.nodeDBbyNodeNum[liveNum], "the concurrent live update must also survive")
    }

    @Test
    fun `local-node identity learned during the load window survives snapshot commit`() = testScope.runTest {
        val persistedNum = 7007
        val persistedInfo =
            MyNodeInfo(
                myNodeNum = persistedNum,
                hasGPS = false,
                model = null,
                firmwareVersion = "2.5.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 0L,
                messageTimeoutMsec = 0,
                minAppVersion = 0,
                maxChannels = 0,
                hasWifi = false,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = null,
            )
        val liveLearnedNum = 8008

        val snapshotRead = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        everySuspend { nodeRepository.getNodeDbSnapshot() } calls
            {
                snapshotRead.complete(Unit)
                snapshotRelease.await()
                // Persisted DB still references the old persistedNum row.
                mapOf(persistedNum to makeKnownNode(persistedNum, validPk, "Persisted"))
            }
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(persistedInfo)

        nodeManager.loadCachedNodeDB()
        snapshotRead.await()
        // The live session has learned a DIFFERENT local node number during the load window. The persisted value
        // must NOT clobber it on commit.
        nodeManager.setMyNodeNum(liveLearnedNum)
        snapshotRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            liveLearnedNum,
            nodeManager.myNodeNum.value,
            "live local-node info wins over persisted snapshot",
        )
    }

    @Test
    fun `repeated trusted migration preserves original hint and still permits distinct-key reuse`() =
        testScope.runTest {
            val num = 9009
            val oldKey = ByteArray(32) { 0x21 }.toByteString()
            val reuseKey = ByteArray(32) { 0x22 }.toByteString()
            nodeManager.handleReceivedUser(num, userWithKey(oldKey, "Old", "OD"), manuallyVerified = false)
            advanceUntilIdle()

            // Apply the same migration twice. The original hint must survive the second pass because the node is
            // already
            // retired and absent; the prior hint is preserved rather than dropped.
            nodeManager.applyTrustedIdentityMigrations(listOf(num))
            nodeManager.applyTrustedIdentityMigrations(listOf(num))
            advanceUntilIdle()

            // Same-key replay still suppressed after the repeated migration.
            val suppressedDispatches = mutableListOf<Notification>()
            everySuspend { notificationManager.dispatch(capture(suppressedDispatches)) } returns true
            nodeManager.handleReceivedUser(num, userWithKey(oldKey, "Replay", "RP"), manuallyVerified = false)
            advanceUntilIdle()
            assertNull(
                nodeManager.nodeDBbyNodeNum[num],
                "same-key replay after repeated migration must stay suppressed",
            )
            assertTrue(suppressedDispatches.none { it.id == num })

            // Distinct valid unrepresented key is still accepted as a legitimate reuse, clearing retirement + hint and
            // emitting exactly one replacement notification.
            val reuseDispatches = mutableListOf<Notification>()
            everySuspend { notificationManager.dispatch(capture(reuseDispatches)) } returns true
            nodeManager.handleReceivedUser(num, userWithKey(reuseKey, "Replacement", "NP"), manuallyVerified = false)
            advanceUntilIdle()

            val reused = nodeManager.nodeDBbyNodeNum[num]
            assertNotNull(reused)
            assertEquals("Replacement", reused!!.user.long_name)
            assertEquals(reuseKey, reused.publicKey)
            assertEquals(1, reuseDispatches.count { it.id == num && it.message == "Replacement" })
        }
}
