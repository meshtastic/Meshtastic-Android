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
package org.meshtastic.feature.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.data.repository.DiscoveryRepositoryImpl
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DiscoveryPacketCollector
import org.meshtastic.core.repository.DiscoveryPacketCollectorRegistry
import org.meshtastic.core.repository.DiscoveryPrefs
import org.meshtastic.core.repository.MeshBeaconPrefs
import org.meshtastic.core.repository.MeshBeaconRepository
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.feature.discovery.scan.Check24GhzCapability
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshBeacon
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers design#140 behavior 10's presentation-time filtering and [DiscoveryViewModel.jpAribGrid] firmware gating.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val radioLora =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
            }
            .build()
    private val partyNetOffer =
        MeshBeaconOffer(
            fromNodeNum = 456,
            beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.message = "Join us"
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build(),
        )

    @Test
    fun `offer for an unconfigured channel passes through both filters`() {
        val channels = listOf(ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build())

        val offers = filterAlreadyJoinedOffers(listOf(partyNetOffer), radioLora, channels)
        val beaconChannels = filterAlreadyJoinedBeaconChannels(listOf(partyNetOffer), radioLora, channels)

        assertEquals(listOf(partyNetOffer), offers)
        assertEquals(listOf("PartyNet"), beaconChannels.map { it.name })
    }

    @Test
    fun `offer matching a configured channel is dropped from both filters`() {
        val channels = listOf(ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build())

        val offers = filterAlreadyJoinedOffers(listOf(partyNetOffer), radioLora, channels)
        val beaconChannels = filterAlreadyJoinedBeaconChannels(listOf(partyNetOffer), radioLora, channels)

        assertTrue(offers.isEmpty(), "an already-joined offer must not appear in the invitations list")
        assertTrue(beaconChannels.isEmpty(), "an already-joined channel must not appear as a scan target")
    }

    @Test
    fun `filtering never mutates the offers list it is given`() {
        // The repository itself must never be touched by presentation-time filtering (spec: reactive, not destructive).
        val original = listOf(partyNetOffer)
        filterAlreadyJoinedOffers(
            original,
            LoRaConfig.Builder().also { wb -> wb.use_preset = true }.build(),
            listOf(ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()),
        )
        assertEquals(1, original.size)
    }

    @Test
    fun `beacon channels filter still deduplicates by id once already-joined entries are removed`() {
        val duplicate = partyNetOffer.copy(fromNodeNum = 789) // Same channel from a second node: one row, not two.
        val beaconChannels =
            filterAlreadyJoinedBeaconChannels(
                listOf(partyNetOffer, duplicate),
                radioLora,
                listOf(ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build()),
            )
        assertEquals(1, beaconChannels.size)
    }

    @Test
    fun `reactively combining offers with a later-changing channel set brings a filtered invitation back`() = runTest {
        // Mirrors DiscoveryViewModel's own combine(offers, currentLora, currentChannels) wiring: proves the filter is
        // reactive at presentation time, not a one-shot decision baked in when the offer arrived.
        val offersFlow = MutableStateFlow(listOf(partyNetOffer))
        val loraFlow = MutableStateFlow<LoRaConfig?>(radioLora)
        val channelsFlow =
            MutableStateFlow(listOf(ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()))

        val results = mutableListOf<List<MeshBeaconOffer>>()
        val job = launch {
            combine(offersFlow, loraFlow, channelsFlow, ::filterAlreadyJoinedOffers).collect { results += it }
        }
        runCurrent()

        assertTrue(results.last().isEmpty(), "already configured, so the invitation starts out hidden")

        // The user deletes the "PartyNet" channel; the still-stored offer must reappear without dismiss()/repository
        // mutation, since MeshBeaconRepository is never touched by this filter.
        channelsFlow.value = emptyList()
        runCurrent()
        assertEquals(listOf(partyNetOffer), results.last())

        // The user re-adds an unrelated channel; still not a match, offer stays visible.
        channelsFlow.value = listOf(ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build())
        runCurrent()
        assertEquals(listOf(partyNetOffer), results.last())

        job.cancel()
    }

    @Test
    fun `jpAribGrid follows connected node firmware version reactively`() = runTest(testDispatcher) {
        val nodeRepository = FakeNodeRepository()
        val viewModel = createViewModel(this, nodeRepository)
        val collector = backgroundScope.launch { viewModel.jpAribGrid.collect {} }

        runCurrent()
        assertFalse(viewModel.jpAribGrid.value, "unknown/absent node must default to legacy grid")

        nodeRepository.setOurNode(nodeWithFirmware("2.7.26"))
        runCurrent()
        assertFalse(viewModel.jpAribGrid.value)

        nodeRepository.setOurNode(nodeWithFirmware("2.8.2"))
        runCurrent()
        assertFalse(viewModel.jpAribGrid.value)

        // Connected node metadata arriving after subscription flips jpAribGrid on for ARIB firmware.
        nodeRepository.setOurNode(nodeWithFirmware("2.8.3"))
        runCurrent()
        assertTrue(viewModel.jpAribGrid.value)

        // Switching back to a pre-ARIB or absent radio reverts to the legacy grid.
        nodeRepository.setOurNode(nodeWithFirmware("2.7.26"))
        runCurrent()
        assertFalse(viewModel.jpAribGrid.value)

        nodeRepository.setOurNode(null)
        runCurrent()
        assertFalse(viewModel.jpAribGrid.value)

        collector.cancel()
    }

    private fun nodeWithFirmware(version: String) = Node(
        num = 1000,
        metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = version }.build(),
    )

    private fun createViewModel(testScope: TestScope, nodeRepository: FakeNodeRepository): DiscoveryViewModel {
        val serviceRepository = FakeServiceRepository()
        val radioConfigRepository = FakeRadioConfigRepository()
        val dao = SharedInMemoryDiscoveryDao()
        val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)
        val appScope =
            object : ApplicationCoroutineScope {
                override val coroutineContext = testDispatcher + SupervisorJob()
            }
        val scanEngine =
            DiscoveryScanEngine(
                radioController = DiscoveryTestRadioController(),
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                collectorRegistry =
                object : DiscoveryPacketCollectorRegistry {
                    override var collector: DiscoveryPacketCollector? = null
                },
                discoveryDao = dao,
                aiProvider = NoOpAiProvider(),
                applicationScope = appScope,
                dispatchers = dispatchers,
                meshPrefs = FakeMeshPrefs(),
                radioOperationLock = RadioOperationLock(),
            )
        return DiscoveryViewModel(
            scanEngine = scanEngine,
            serviceRepository = serviceRepository,
            discoveryPrefs = InMemoryDiscoveryPrefs(),
            check24GhzCapability = Check24GhzCapability(nodeRepository, FakeDeviceHardwareRepository()),
            meshBeaconRepository = MeshBeaconRepository(InMemoryBeaconPrefs(), testScope.backgroundScope),
            nodeRepository = nodeRepository,
            radioConfigRepository = radioConfigRepository,
            discoveryRepository = DiscoveryRepositoryImpl(dao),
        )
    }

    private class NoOpAiProvider : DiscoverySummaryAiProvider {
        override val isAvailable: Boolean = false

        override suspend fun generateSessionSummary(
            session: DiscoverySessionEntity,
            presetResults: List<DiscoveryPresetResultEntity>,
        ): String? = null

        override suspend fun generatePresetSummary(result: DiscoveryPresetResultEntity): String? = null
    }

    private class InMemoryDiscoveryPrefs : DiscoveryPrefs {
        override val dwellMinutes = MutableStateFlow(DiscoveryPrefs.DEFAULT_DWELL_MINUTES)

        override fun setDwellMinutes(minutes: Int) {
            dwellMinutes.value = minutes
        }

        override val selectedPresets = MutableStateFlow<Set<String>>(emptySet())

        override fun setSelectedPresets(presets: Set<String>) {
            selectedPresets.value = presets
        }

        override val aiEnabled = MutableStateFlow(false)

        override fun setAiEnabled(enabled: Boolean) {
            aiEnabled.value = enabled
        }

        override val topologyOverlayEnabled = MutableStateFlow(false)

        override fun setTopologyOverlayEnabled(enabled: Boolean) {
            topologyOverlayEnabled.value = enabled
        }
    }

    private class InMemoryBeaconPrefs : MeshBeaconPrefs {
        override val storedBeacons: StateFlow<List<String>> = MutableStateFlow(emptyList())

        override fun setStoredBeacons(records: List<String>) = Unit
    }
}
