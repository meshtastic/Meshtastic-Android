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
package org.meshtastic.feature.firmware.ota

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.proto.ClientNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [Esp32OtaUpdateHandler]'s OTA preflight gate — the bounded wait for firmware confirmation between
 * `triggerRebootOta` and tearing down the mesh transport.
 *
 * Each test drives `startUpdate` end-to-end just far enough to observe the preflight outcome:
 * - Confirmed → mesh disconnect (`setDeviceAddress("n")`) is invoked, then the handler proceeds into the BLE transport
 *   phase, which fails fast on the mocked BLE deps. We bound the call with `withTimeoutOrNull` so the post-preflight
 *   BLE-connect retry loop is cancelled before it costs real wall-clock time.
 * - Rejected → mesh transport is preserved (no `setDeviceAddress`) and an `Error` state is emitted; the handler returns
 *   cleanly with no exception to chase.
 * - Silent firmware → legacy fallback disconnects the mesh transport and proceeds toward OTA connection attempts.
 *
 * The firmware response is simulated by mutating [FakeRadioController.clientNotification] from a sibling coroutine
 * scheduled to fire after `triggerRebootOta`. Because baseline is captured BEFORE the trigger (see `performUpdate`), a
 * value written afterward is recognized as the response, not the baseline.
 */
class Esp32OtaUpdateHandlerTest {

    private val release = FirmwareRelease(id = "v1.0", title = "test")
    private val hardware =
        DeviceHardware(hwModelSlug = "HELTEC_V3", platformioTarget = "heltec-v3", architecture = "esp32-s3")
    private val firmwareUri = CommonUri.parse("file:///tmp/firmware.bin")

    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    private fun makeHandler(
        radioController: FakeRadioController,
        nodeRepository: FakeNodeRepository,
        fileHandler: FirmwareFileHandler,
    ): Esp32OtaUpdateHandler = Esp32OtaUpdateHandler(
        firmwareRetriever = FirmwareRetriever(fileHandler),
        firmwareFileHandler = fileHandler,
        radioController = radioController,
        nodeRepository = nodeRepository,
        bleScanner = mock(MockMode.autofill),
        bleConnectionFactory = mock(MockMode.autofill),
        dispatchers = dispatchers,
    )

    private fun newFileHandler(): FirmwareFileHandler = mock(MockMode.autofill) {
        everySuspend { readBytes(any()) } returns byteArrayOf(1, 2, 3, 4)
        everySuspend { importFromUri(any()) } returns
            FirmwareArtifact(uri = firmwareUri, fileName = "firmware.bin", isTemporary = false)
    }

    private fun newNodeRepository(): FakeNodeRepository = FakeNodeRepository().apply {
        setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1, firmwareVersion = "1.0"))
    }

    /** Schedule [block] on a sibling Default dispatcher; cancelled at the end of the test. */
    private fun runSideEffect(block: suspend CoroutineScope.() -> Unit): Job =
        CoroutineScope(Dispatchers.Default).launch { block() }

    // ── Confirmed ──────────────────────────────────────────────────────────

    @Test
    fun `Confirmed preflight releases the mesh transport`() = runBlocking {
        val radioController = FakeRadioController()
        val nodeRepository = newNodeRepository()
        val handler = makeHandler(radioController, nodeRepository, newFileHandler())
        val states = mutableListOf<FirmwareUpdateState>()

        // Simulate the firmware emitting its "Rebooting to BLE OTA" confirmation shortly after the trigger fires.
        val emitter = runSideEffect {
            delay(50)
            radioController.setClientNotification(ClientNotification(message = "Rebooting to BLE OTA"))
        }

        try {
            // Bound the call: we only need to observe the preflight side effect. The post-preflight BLE transport
            // phase would otherwise burn ~30 s of real wall-clock time on retry/delay loops with mocked BLE deps.
            withTimeoutOrNull(3000L) { handler.startUpdate(release, hardware, BLE_TARGET, states::add, firmwareUri) }
        } finally {
            emitter.cancel()
        }

        assertEquals("n", radioController.lastSetDeviceAddress, "Confirmed preflight must disconnect mesh service")
        assertNull(radioController.clientNotification.value, "Confirmed preflight must clear its consumed status")
    }

    // ── Rejected ───────────────────────────────────────────────────────────

    @Test
    fun `Rejected preflight preserves mesh transport and surfaces Error`() = runBlocking {
        val radioController = FakeRadioController()
        val nodeRepository = newNodeRepository()
        val handler = makeHandler(radioController, nodeRepository, newFileHandler())
        val states = mutableListOf<FirmwareUpdateState>()

        val emitter = runSideEffect {
            delay(50)
            // OTA-keyed message that is NOT the canonical "Rebooting to <mode> OTA" success prefix → Rejected.
            radioController.setClientNotification(
                ClientNotification(message = "Cannot start OTA: OTA Loader partition not found."),
            )
        }

        try {
            handler.startUpdate(release, hardware, BLE_TARGET, states::add, firmwareUri)
        } finally {
            emitter.cancel()
        }

        assertNull(radioController.lastSetDeviceAddress, "Rejected preflight must NOT disconnect mesh service")
        assertNull(radioController.clientNotification.value, "Rejected preflight must clear its consumed status")
        assertTrue(states.any { it is FirmwareUpdateState.Error }, "Rejected preflight must emit Error state")
    }

    // ── Legacy fallback ────────────────────────────────────────────────────

    @Test
    fun `Silent preflight falls back to legacy OTA reconnect path`() = runBlocking {
        val radioController = FakeRadioController()
        val nodeRepository = newNodeRepository()
        val handler =
            makeHandler(radioController, nodeRepository, newFileHandler()).apply { otaPreflightTimeoutMs = 10L }
        val states = mutableListOf<FirmwareUpdateState>()

        // No emitter: older firmware may be silent, so the overridden preflight timeout should resolve quickly into
        // the same disconnect/reconnect path the app used before preflight confirmation existed.
        withTimeoutOrNull(3000L) { handler.startUpdate(release, hardware, BLE_TARGET, states::add, firmwareUri) }

        assertEquals("n", radioController.lastSetDeviceAddress, "Silent preflight must still disconnect mesh service")
    }

    private companion object {
        // Valid BLE MAC target so startUpdate routes through the BLE OTA preflight path.
        const val BLE_TARGET = "AA:BB:CC:DD:EE:FF"
    }
}
