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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeCommandSender
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeTakPrefs
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the self-test blind spot found while investigating #6583: [TakMeshTestRunner] must exercise
 * the SAME real dispatch pipeline ([TAKMeshIntegration.sendCoTToMeshV1] / `sendCoTToMeshV2`) that live TAK traffic
 * uses, not a hardcoded v2-only shortcut — and it must do so for BOTH protocol versions regardless of whichever
 * firmware a test radio happens to report, since [TAKMeshIntegration.useTakV2] gates real traffic on the connected
 * radio's firmware and older firmware silently falls back to the much more limited v1 schema.
 */
class TakMeshTestRunnerTest {

    private class FakeMeshConfigHandler : MeshConfigHandler {
        override val localConfig = MutableStateFlow(LocalConfig())
        override val moduleConfig = MutableStateFlow(LocalModuleConfig())

        override fun handleDeviceConfig(config: Config, session: RadioSessionContext) = true

        override fun handleModuleConfig(config: ModuleConfig, session: RadioSessionContext) = true

        override fun handleChannel(channel: Channel, session: RadioSessionContext) = true

        override fun handleDeviceUIConfig(config: DeviceUIConfig, session: RadioSessionContext) = true

        override fun handleRegionPresets(map: LoRaRegionPresetMap, session: RadioSessionContext) = true
    }

    private class Harness(firmwareVersion: String?) {
        val serverManager = FakeTAKServerManager()
        val commandSender = FakeCommandSender()
        val serviceRepository = FakeServiceRepository()
        val meshConfigHandler = FakeMeshConfigHandler()
        val nodeRepository =
            FakeNodeRepository().apply {
                setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = firmwareVersion))
            }
        val takPrefs = FakeTakPrefs()
        private val dispatcher = UnconfinedTestDispatcher()
        val broadcaster =
            MeshToCotBroadcaster(
                serverManager,
                nodeRepository,
                takPrefs,
                CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
            )

        val integration =
            TAKMeshIntegration(
                takServerManager = serverManager,
                commandSender = commandSender,
                serviceRepository = serviceRepository,
                meshConfigHandler = meshConfigHandler,
                nodeRepository = nodeRepository,
                meshToCotBroadcaster = broadcaster,
            )

        val runner = TakMeshTestRunner(integration)
    }

    // ── runAll() covers both protocols ──────────────────────────────────────

    @Test
    fun `runAll exercises every fixture on both v1 and v2`() = runTest(UnconfinedTestDispatcher()) {
        // Firmware is irrelevant here — the runner forces the protocol per pass rather than
        // reading Capabilities.supportsTakV2, which is exactly the bug this test guards against.
        val h = Harness(firmwareVersion = null)

        h.runner.runAll()

        val results = h.runner.results.value
        assertEquals(TakMeshTestRunner.FIXTURE_NAMES.size * 2, results.size)
        assertEquals(TakMeshTestRunner.FIXTURE_NAMES.size, results.count { it.protocol == TakProtocol.V2 })
        assertEquals(TakMeshTestRunner.FIXTURE_NAMES.size, results.count { it.protocol == TakProtocol.V1 })
    }

    @Test
    fun `v2 pass sends on ATAK_PLUGIN_V2 regardless of connected firmware`() = runTest(UnconfinedTestDispatcher()) {
        // Connected "radio" reports legacy firmware — if the runner still read useTakV2() instead of
        // forcing the protocol, this would incorrectly downgrade the v2 pass to v1.
        val h = Harness(firmwareVersion = "2.7.0.0")

        h.runner.runAll()

        val v2Results = h.runner.results.value.filter { it.protocol == TakProtocol.V2 }
        val v2Sent = h.commandSender.sentPackets.count { it.dataType == PortNum.ATAK_PLUGIN_V2.value }
        assertEquals(v2Results.count { it.passed }, v2Sent, "every passing v2 result must correspond to a v2 send")
        assertTrue(v2Sent > 0, "at least one fixture must round-trip through the real v2 pipeline")
    }

    @Test
    fun `v1 pass sends on legacy ATAK_PLUGIN regardless of connected firmware`() = runTest(UnconfinedTestDispatcher()) {
        // Connected "radio" reports v2-capable firmware — if the runner still read useTakV2() instead
        // of forcing the protocol, this would incorrectly upgrade the v1 pass to v2, hiding the exact
        // blind spot reported in #6583.
        val h = Harness(firmwareVersion = "2.8.0.0")

        h.runner.runAll()

        val v1Results = h.runner.results.value.filter { it.protocol == TakProtocol.V1 }
        val v1Sent = h.commandSender.sentPackets.count { it.dataType == PortNum.ATAK_PLUGIN.value }
        assertEquals(v1Results.count { it.passed }, v1Sent, "every passing v1 result must correspond to a v1 send")
        assertTrue(v1Sent > 0, "PLI/GeoChat fixtures must still round-trip through the real v1 pipeline")
    }

    @Test
    fun `v1 pass reports non-PLI non-chat drops as expected not as failures`() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(firmwareVersion = null)

        h.runner.runAll()

        val v1Results = h.runner.results.value.filter { it.protocol == TakProtocol.V1 }
        // marker_spot.xml is type "b-m-p-s-m" — not representable in the legacy v1 TAKPacket
        // schema (only PLI/"a-f-*" and chat/"b-t-f" are). This must show up as an *expected*
        // drop, not an unlabeled self-test failure, per the v1 schema's documented limitations.
        val markerResult = v1Results.single { it.fixtureName == "marker_spot.xml" }
        assertTrue(markerResult.expectedDrop, "v1 dropping an unsupported CoT type is expected, not a bug")
        assertTrue(!markerResult.passed)

        // Every v1 drop must be accounted for: either the known schema-coverage limitation
        // (expectedDrop) or an oversize drop, which is a real MTU problem and correctly NOT
        // labeled expected even though it happened on v1 — see TakSendOutcome.Dropped.schemaLimited.
        // A drop that is neither would be a genuine, unaccounted-for failure.
        assertTrue(
            v1Results.filter { !it.passed }.all { it.expectedDrop || it.error?.startsWith("Oversized") == true },
            "every v1 drop in a clean run should be the known schema limitation or an oversize drop, " +
                "not an unaccounted-for failure",
        )
    }

    @Test
    fun `v2 pass has no expected drops - v2 must support every fixture type`() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(firmwareVersion = null)

        h.runner.runAll()

        val v2Results = h.runner.results.value.filter { it.protocol == TakProtocol.V2 }
        assertTrue(
            v2Results.none { it.expectedDrop },
            "v2 has no known schema gaps, so nothing should be labeled expected",
        )
    }
}
