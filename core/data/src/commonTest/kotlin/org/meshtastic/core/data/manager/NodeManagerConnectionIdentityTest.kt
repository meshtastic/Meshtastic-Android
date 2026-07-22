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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [NodeManagerImpl.connectionIdentity] — the fresh-handshake identity source that stays separate from the
 * general [NodeManagerImpl.myNodeNum]/[NodeManagerImpl.myDeviceId] StateFlows so a stale cached identity cannot
 * associate a new transport address with a previous device. Covers publish/clear semantics, loadCachedNodeDB
 * non-population, full-reset behavior, StateFlow distinctness, null-deviceId handling, and address binding.
 */
class NodeManagerConnectionIdentityTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val testScope = TestScope()

    private lateinit var nodeManager: NodeManagerImpl

    @BeforeTest
    fun setUp() {
        everySuspend { nodeRepository.getNodeDbSnapshot() } returns emptyMap()
        nodeManager = NodeManagerImpl(nodeRepository, notificationManager, radioInterfaceService, testScope)
    }

    @Test
    fun `publish then clear sets and nulls connectionIdentity`() = testScope.runTest {
        assertNull(nodeManager.connectionIdentity.value, "identity should start null")

        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "tcp:one",
            nodeNum = 42,
            deviceId = "deadbeef",
        )
        assertEquals(
            ConnectionIdentity(sessionGeneration = 1L, address = "tcp:one", nodeNum = 42, deviceId = "deadbeef"),
            nodeManager.connectionIdentity.value,
        )

        nodeManager.clearConnectionIdentity()
        assertNull(nodeManager.connectionIdentity.value, "identity should be null after clear")
    }

    @Test
    fun `connectionIdentity diagnostic text redacts persistent identifiers`() = testScope.runTest {
        val identity = ConnectionIdentity(7L, "ble:AA:BB:CC:DD:EE:FF", 42, "factory-burned-id")

        val diagnostic = identity.toString()

        assertFalse("AA:BB:CC:DD:EE:FF" in diagnostic)
        assertFalse("factory-burned-id" in diagnostic)
        assertTrue("sessionGeneration=7" in diagnostic)
        assertTrue("nodeNum=42" in diagnostic)
        assertTrue("deviceIdPresent=true" in diagnostic)
    }

    @Test
    fun `generation-aware clear preserves fresh identity and removes stale identity`() = testScope.runTest {
        val freshIdentity = ConnectionIdentity(2L, "tcp:one", 42, "deadbeef")
        nodeManager.publishConnectionIdentity(
            sessionGeneration = freshIdentity.sessionGeneration,
            address = freshIdentity.address,
            nodeNum = freshIdentity.nodeNum,
            deviceId = freshIdentity.deviceId,
        )

        nodeManager.clearStaleConnectionIdentity(activeSessionGeneration = 2L)
        assertEquals(freshIdentity, nodeManager.connectionIdentity.value, "the active generation must be preserved")

        nodeManager.clearStaleConnectionIdentity(activeSessionGeneration = 3L)
        assertNull(nodeManager.connectionIdentity.value, "an identity from the prior generation must be cleared")
    }

    @Test
    fun `loadCachedNodeDB does not populate connectionIdentity`() = testScope.runTest {
        // Even with a non-null myNodeInfo in the repo cache, connectionIdentity must remain null — only a fresh
        // MyNodeInfo handshake publishes the identity (see publishConnectionIdentity).
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(cachedMyNodeInfo(myNodeNum = 777))

        nodeManager.loadCachedNodeDB()
        runCurrent()

        // myNodeNum may have been restored from cache, but connectionIdentity must NOT.
        assertEquals(777, nodeManager.myNodeNum.value, "myNodeNum should be restored from cache")
        assertNull(nodeManager.connectionIdentity.value, "connectionIdentity must not be populated by cache reload")
    }

    @Test
    fun `clear resets connectionIdentity alongside other state`() = testScope.runTest {
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "tcp:one",
            nodeNum = 42,
            deviceId = "deadbeef",
        )
        assertEquals(ConnectionIdentity(1L, "tcp:one", 42, "deadbeef"), nodeManager.connectionIdentity.value)

        nodeManager.clear()

        assertNull(nodeManager.connectionIdentity.value, "clear() must reset connectionIdentity")
        assertNull(nodeManager.myNodeNum.value, "clear() must reset myNodeNum")
        assertNull(nodeManager.myDeviceId.value, "clear() must reset myDeviceId")
    }

    @Test
    fun `connectionIdentity StateFlow emits only distinct values`() = testScope.runTest {
        val emissions = mutableListOf<ConnectionIdentity?>()
        val collector = testScope.launch { nodeManager.connectionIdentity.collect { emissions += it } }
        runCurrent()
        // Initial replay: the starting null value.
        assertEquals(
            listOf<ConnectionIdentity?>(null),
            emissions,
            "only the initial null should be present before any publish",
        )

        // First publish — produces one new emission.
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "tcp:one",
            nodeNum = 42,
            deviceId = "deadbeef",
        )
        runCurrent()
        assertEquals(
            listOf(null, ConnectionIdentity(1L, "tcp:one", 42, "deadbeef")),
            emissions,
            "first publish should emit once",
        )

        // Re-publishing an equal value must NOT emit again — StateFlow conflates equal values, so any downstream
        // distinctUntilChanged (e.g. RadioControllerImpl's combine collector) skips it.
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "tcp:one",
            nodeNum = 42,
            deviceId = "deadbeef",
        )
        runCurrent()
        assertEquals(
            listOf(null, ConnectionIdentity(1L, "tcp:one", 42, "deadbeef")),
            emissions,
            "equal re-publication must not re-emit",
        )

        // A genuinely different value does emit.
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "tcp:two",
            nodeNum = 43,
            deviceId = "deadbeef",
        )
        runCurrent()
        assertEquals(
            listOf(
                null,
                ConnectionIdentity(1L, "tcp:one", 42, "deadbeef"),
                ConnectionIdentity(1L, "tcp:two", 43, "deadbeef"),
            ),
            emissions,
            "different value must emit",
        )
        collector.cancel()
    }

    @Test
    fun `publish with null deviceId keeps address and nodeNum set`() = testScope.runTest {
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "ble:one",
            nodeNum = 99,
            deviceId = null,
        )

        val identity = nodeManager.connectionIdentity.value
        assertNotEquals(null, identity, "identity itself must not be null")
        assertEquals("ble:one", identity?.address, "address must identify the handshake transport")
        assertEquals(99, identity?.nodeNum, "nodeNum must be set even when deviceId is null")
        assertNull(identity?.deviceId, "deviceId must remain null")
    }

    @Test
    fun `clearing a session removes its address-bound identity`() = testScope.runTest {
        // Simulate the previous transport publishing its handshake identity.
        nodeManager.publishConnectionIdentity(
            sessionGeneration = 1L,
            address = "ble:old",
            nodeNum = 42,
            deviceId = "deadbeef",
        )
        assertEquals(ConnectionIdentity(1L, "ble:old", 42, "deadbeef"), nodeManager.connectionIdentity.value)

        nodeManager.clearConnectionIdentity()

        assertNull(nodeManager.connectionIdentity.value, "identity must be null after the session is cleared")

        // Even after subsequent loadCachedNodeDB-style state restoration, identity stays null.
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        nodeManager.loadCachedNodeDB()
        runCurrent()
        assertNull(nodeManager.connectionIdentity.value, "identity must stay null across cache reload")
    }

    private fun cachedMyNodeInfo(myNodeNum: Int) = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = null,
        firmwareVersion = null,
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
}
