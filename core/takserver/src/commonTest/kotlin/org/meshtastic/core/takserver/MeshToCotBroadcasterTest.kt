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
package org.meshtastic.core.takserver

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeTakPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MeshToCotBroadcasterTest {
    // FakeTAKServerManager lives in its own file in this source set, shared with TAKMeshIntegrationTest.
    // It never touches isRunning; MeshToCotBroadcaster only reads connectionCount.

    private class Harness(val scope: TestScope) {
        val serverManager = FakeTAKServerManager()
        val nodeRepository = FakeNodeRepository()
        val takPrefs = FakeTakPrefs()
        private val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)

        val broadcaster =
            MeshToCotBroadcaster(
                takServerManager = serverManager,
                nodeRepository = nodeRepository,
                takPrefs = takPrefs,
                dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
            )

        /** Simulates an attached TAK client; without one the broadcaster deliberately stays quiet. */
        fun withConnectedClient() = apply { serverManager.connections.value = 1 }

        fun enableMeshToCot() = apply { takPrefs.isMeshToCotEnabled.value = true }

        fun setOurNodeNum(num: Int) = apply { nodeRepository.setMyNodeInfo(myNodeInfo(num)) }

        /** Lets the broadcaster's inter-message spacing elapse so every queued send lands. */
        fun settle() {
            scope.advanceTimeBy(SETTLE_MS)
            scope.runCurrent()
        }
    }

    @Test
    fun `stays silent while the preference is off`() = runTest {
        val h = Harness(this).withConnectedClient()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()

        assertTrue(h.serverManager.broadcasts.isEmpty(), "opt-in pref off must publish nothing")
    }

    @Test
    fun `publishes eligible nodes once enabled`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()

        assertEquals(1, h.serverManager.broadcasts.size)
        assertEquals("MESHTASTIC-A1B2C3D4", h.serverManager.broadcasts.single().uid)
    }

    @Test
    fun `does not publish without a connected TAK client`() = runTest {
        // Broadcasting with no client would fill TAKServerManager's 50-entry offline queue with
        // node PLIs and evict genuine mesh CoT. The replay on connect covers the gap.
        val h = Harness(this).enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()

        assertTrue(h.serverManager.broadcasts.isEmpty())
    }

    @Test
    fun `skips ineligible nodes`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot().setOurNodeNum(OUR_NODE_NUM)
        h.nodeRepository.setNodes(
            listOf(
                meshNode(num = OUR_NODE_NUM),
                meshNode(num = 0x22222222, latitudeI = 0, longitudeI = 0),
                meshNode(num = 0x33333333, hwModel = org.meshtastic.proto.HardwareModel.UNSET),
                meshNode(num = 0x44444444, lastHeard = 1),
                meshNode(num = ELIGIBLE_NODE_NUM),
            ),
        )

        h.broadcaster.start(backgroundScope)
        h.settle()

        assertEquals(1, h.serverManager.broadcasts.size, "only the one eligible node should publish")
        assertEquals(meshNode(num = ELIGIBLE_NODE_NUM).cotUid(), h.serverManager.broadcasts.single().uid)
    }

    @Test
    fun `an unchanged node is not rebroadcast`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        val node = meshNode()
        h.nodeRepository.setNodes(listOf(node))

        h.broadcaster.start(backgroundScope)
        h.settle()
        h.nodeRepository.setNodes(listOf(node))
        h.settle()

        assertEquals(1, h.serverManager.broadcasts.size)
    }

    @Test
    fun `a moved node is rebroadcast`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()
        h.nodeRepository.setNodes(listOf(meshNode(latitudeI = 387_749_000)))
        h.settle()

        assertEquals(2, h.serverManager.broadcasts.size)
    }

    @Test
    fun `a connecting client gets a full replay`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()
        assertEquals(1, h.serverManager.broadcasts.size)

        // The new client has seen none of the earlier broadcasts, so dedup must not suppress them.
        h.serverManager.emitClientConnected()
        h.settle()

        assertEquals(2, h.serverManager.broadcasts.size)
    }

    @Test
    fun `stationary nodes are refreshed before their markers go stale`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()
        assertEquals(1, h.serverManager.broadcasts.size)

        // Nothing about the node changes, but ATAK expires the marker at MESH_NODE_STALE_MINUTES.
        advanceTimeBy(MESH_TO_COT_REFRESH_INTERVAL_MS + SETTLE_MS)
        runCurrent()

        assertTrue(h.serverManager.broadcasts.size >= 2, "stationary node must be re-sent to stay alive in ATAK")
        assertTrue(MESH_TO_COT_REFRESH_INTERVAL_MS < MESH_NODE_STALE_MINUTES * 60L * 1000L)
    }

    @Test
    fun `disabling the preference stops publishing`() = runTest {
        val h = Harness(this).withConnectedClient().enableMeshToCot()
        h.nodeRepository.setNodes(listOf(meshNode()))

        h.broadcaster.start(backgroundScope)
        h.settle()
        val afterEnable = h.serverManager.broadcasts.size

        h.takPrefs.isMeshToCotEnabled.value = false
        h.nodeRepository.setNodes(listOf(meshNode(latitudeI = 387_749_000)))
        h.settle()

        assertEquals(afterEnable, h.serverManager.broadcasts.size)
    }

    private companion object {
        const val OUR_NODE_NUM = 0x0badf00d
        const val ELIGIBLE_NODE_NUM = 0xa1b2c3d4.toInt()
        const val SETTLE_MS = 1_000L

        fun recentLastHeard() = Clock.System.now().epochSeconds.toInt()

        @Suppress("LongParameterList")
        fun meshNode(
            num: Int = ELIGIBLE_NODE_NUM,
            latitudeI: Int = 377_749_000,
            longitudeI: Int = -1_224_194_000,
            lastHeard: Int = recentLastHeard(),
            hwModel: org.meshtastic.proto.HardwareModel = org.meshtastic.proto.HardwareModel.TBEAM,
        ) = Node(
            num = num,
            user =
            org.meshtastic.proto.User(
                id = "!" + num.toUInt().toString(16).padStart(8, '0'),
                short_name = "WOLF",
                long_name = "Wolf Ridge Relay",
                hw_model = hwModel,
            ),
            position = org.meshtastic.proto.Position(latitude_i = latitudeI, longitude_i = longitudeI),
            lastHeard = lastHeard,
        )

        fun myNodeInfo(num: Int) = MyNodeInfo(
            myNodeNum = num,
            hasGPS = false,
            model = null,
            firmwareVersion = "2.8.0",
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 1L,
            messageTimeoutMsec = 5000,
            minAppVersion = 1,
            maxChannels = 8,
            hasWifi = false,
            channelUtilization = 0f,
            airUtilTx = 0f,
            deviceId = null,
        )
    }
}
