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
package org.meshtastic.feature.map.hud

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MapFilterPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.testing.FakeLocaleUnitsProvider
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeTracerouteSnapshotRepository
import org.meshtastic.proto.Position
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LAT = 40.0
private const val LON = -105.0

/** About 1.1 km north of the phone. */
private const val NEAR_LAT = 40.01

/** About 111 km north of the phone. */
private const val FAR_LAT = 41.0
private const val FAR_ENOUGH_M = 100_000.0

private fun node(num: Int, lat: Double = NEAR_LAT, favorite: Boolean = false) = Node(
    num = num,
    isFavorite = favorite,
    position =
    Position.Builder()
        .also { b ->
            b.latitude_i = (lat * 1e7).toInt()
            b.longitude_i = (LON * 1e7).toInt()
        }
        .build(),
)

private class FakePoseProvider : CameraPoseProvider {
    override fun poseUpdates(): Flow<CameraPoseState> = MutableStateFlow(CameraPoseState())
}

private class NoBarometer : PhonePressureProvider {
    override fun pressureUpdates(): Flow<Float> = emptyFlow()
}

private class FakePhoneLocationProvider : PhoneLocationProvider {
    val state =
        MutableStateFlow(
            PhoneLocationState(
                permissionGranted = true,
                providerEnabled = true,
                location = PhoneLocation(LAT, LON, 100.0, 0L, accuracyM = 5f, speedMps = 0f),
            ),
        )

    override fun locationUpdates(): Flow<PhoneLocationState> = state
}

private class ZeroDeclination : MagneticFieldProvider {
    override fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long) = 0f
}

private class FakeTracerouteResponses : TracerouteResponseProvider {
    override val tracerouteResponse = MutableStateFlow<TracerouteResponse?>(null)

    override fun clearTracerouteResponse() {
        tracerouteResponse.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MeshHudViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nodeRepository = FakeNodeRepository()
    private val mapPrefs = FakeMapPrefs()
    private val traceroutes = FakeTracerouteResponses()
    private val snapshots = FakeTracerouteSnapshotRepository()
    private val packetRepository: PacketRepository = mock()
    private lateinit var viewModel: MeshHudViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { packetRepository.getWaypoints() } returns MutableStateFlow(emptyList<DataPacket>())
        viewModel =
            MeshHudViewModel(
                poseProvider = FakePoseProvider(),
                phoneLocationProvider = FakePhoneLocationProvider(),
                pressureProvider = NoBarometer(),
                magneticFieldProvider = ZeroDeclination(),
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                meshLogRepository = FakeMeshLogRepository(),
                tracerouteResponseProvider = traceroutes,
                tracerouteSnapshotRepository = snapshots,
                mapPrefs = mapPrefs,
                notificationPrefs = FakeNotificationPrefs(),
                localeUnitsProvider = FakeLocaleUnitsProvider(),
                dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectTargets(): StateFlow<MeshHudTargets> {
        backgroundScope.launch { viewModel.targets.collect {} }
        runCurrent()
        return viewModel.targets
    }

    private val StateFlow<MeshHudTargets>.keys
        get() = value.targets.map { it.subject.key }.toSet()

    @Test
    fun `targets are not ready before the first computation and ready after`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1)))
        assertFalse(viewModel.targets.value.ready)
        val targets = collectTargets()
        assertTrue(targets.value.ready)
    }

    @Test
    fun `the whole mesh view shows every positioned node passing the map filter`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1, favorite = true), node(2), Node(num = 3)))
        mapPrefs.mapFilters.value = MapFilterPrefs(onlyFavorites = true)
        viewModel.openFor(null)
        val targets = collectTargets()
        assertEquals(setOf("node-1"), targets.keys)
        assertFalse(targets.value.scoped)

        mapPrefs.mapFilters.value = MapFilterPrefs()
        runCurrent()
        assertEquals(setOf("node-1", "node-2"), targets.keys)
    }

    @Test
    fun `a view opened for a node shows only that node`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1), node(2), node(3)))
        viewModel.openFor(2)
        val targets = collectTargets()
        assertEquals(setOf("node-2"), targets.keys)
        assertTrue(targets.value.scoped)
        assertTrue(targets.value.targets.single().focused)
    }

    @Test
    fun `opening again for the same node keeps the focus the user chose`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1), node(2)))
        viewModel.openFor(null)
        val targets = collectTargets()
        viewModel.toggleFocus(HudSubject.NodeSubject(node(2)))
        viewModel.openFor(null)
        runCurrent()
        assertEquals("node-2", targets.value.targets.single { it.focused }.subject.key)

        viewModel.openFor(1)
        runCurrent()
        assertEquals("node-1", targets.value.targets.single { it.focused }.subject.key)
    }

    @Test
    fun `a scoped view ignores the map filter for its node`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1, favorite = true), node(2)))
        mapPrefs.mapFilters.value = MapFilterPrefs(onlyFavorites = true)
        viewModel.openFor(2)
        assertEquals(setOf("node-2"), collectTargets().keys)
    }

    @Test
    fun `a scoped view also shows the node its open track names`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1), node(2), node(3)))
        viewModel.openFor(1)
        viewModel.showTrack(2)
        assertEquals(setOf("node-1", "node-2"), collectTargets().keys)
    }

    @Test
    fun `a scoped view also shows the hops its open traceroute names`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1), node(2), node(3), node(4), node(5)))
        traceroutes.tracerouteResponse.value =
            TracerouteResponse("", 2, requestId = 7, forwardRoute = listOf(3), returnRoute = listOf(4))
        viewModel.openFor(2)
        viewModel.showTraceroute(destNum = 2, requestId = 7, logUuid = null)
        assertEquals(setOf("node-2", "node-3", "node-4"), collectTargets().keys)
    }

    @Test
    fun `a hop is placed where its traceroute recorded it rather than where it is now`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(2), node(3)))
        val recorded = mapOf(3 to node(3, lat = FAR_LAT).position)
        snapshots.upsertSnapshotPositions("log-1", requestId = 7, positions = recorded)
        traceroutes.tracerouteResponse.value =
            TracerouteResponse("", 2, requestId = 7, forwardRoute = listOf(3), returnRoute = emptyList())
        viewModel.openFor(2)
        viewModel.showTraceroute(destNum = 2, requestId = 7, logUuid = "log-1")
        val hop = collectTargets().value.targets.first { it.subject.key == "node-3" }
        assertTrue(hop.distanceM > FAR_ENOUGH_M)
    }

    @Test
    fun `the whole mesh view leaves distance to the map filters`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1), node(3, lat = FAR_LAT)))
        viewModel.openFor(null)
        assertEquals(setOf("node-1", "node-3"), collectTargets().keys)
    }

    @Test
    fun `a scoped view keeps its node when the focus is cleared`() = runTest(testDispatcher) {
        val far = node(3, lat = FAR_LAT)
        nodeRepository.setNodes(listOf(node(1), far))
        viewModel.openFor(3)
        val targets = collectTargets()
        assertEquals(setOf("node-3"), targets.keys)

        viewModel.toggleFocus(HudSubject.NodeSubject(far))
        runCurrent()
        assertEquals(setOf("node-3"), targets.keys)
        assertFalse(targets.value.targets.single().focused)
    }

    @Test
    fun `a tracked node stays in the whole mesh view whatever the filters say`() = runTest(testDispatcher) {
        nodeRepository.setNodes(listOf(node(1, favorite = true), node(3, lat = FAR_LAT)))
        mapPrefs.mapFilters.value = MapFilterPrefs(onlyFavorites = true)
        viewModel.openFor(null)
        viewModel.showTrack(3)
        assertEquals(setOf("node-1", "node-3"), collectTargets().keys)
    }
}
