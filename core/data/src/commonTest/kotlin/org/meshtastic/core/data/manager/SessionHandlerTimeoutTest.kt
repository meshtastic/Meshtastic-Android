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

import co.touchlab.kermit.Severity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.sdk.ConfigBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionHandlerTimeoutTest {

    @Test
    fun `a handler that never returns is cancelled at two minutes and logged with its label`() = runTest {
        val never = CompletableDeferred<Unit>()
        var ranToEnd = false
        var afterTimeout = false

        val logs = CapturingLogWriter.install()
        try {
            val job = launch {
                withHandlerTimeout("FromRadio packet TEXT_MESSAGE_APP") {
                    never.await()
                    ranToEnd = true
                }
                afterTimeout = true
            }
            runCurrent()
            advanceTimeBy(119_000L)
            assertTrue(job.isActive, "the handler must still be running before two minutes")

            advanceTimeBy(2_000L)
            runCurrent()

            assertFalse(ranToEnd, "the wedged handler must be cancelled, not completed")
            assertTrue(afterTimeout, "the timeout must be swallowed so the pipeline continues")
            assertTrue(job.isCompleted)
            val lines = logs.messages(Severity.Error).filter { "Session handler exceeded" in it }
            assertEquals(1, lines.size, "one timeout line: $lines")
            assertTrue("(handler=FromRadio packet TEXT_MESSAGE_APP)" in lines.single())
        } finally {
            CapturingLogWriter.uninstall()
            never.complete(Unit)
        }
    }

    @Test
    fun `disconnect completes after a wedged handler is timed out`() = runTest {
        val radio = FakeRadioInterfaceService(backgroundScope)
        radio.setDeviceAddress("ble:a")
        radio.connect()
        val session = assertNotNull(radio.activeSession.value)
        val never = CompletableDeferred<Unit>()
        radio.launchSessionWork(backgroundScope, session) { withHandlerTimeout("wedged") { never.await() } }
        runCurrent()

        val disconnect = launch { radio.disconnect() }
        runCurrent()
        assertFalse(disconnect.isCompleted, "disconnect must wait while the session work is running")

        advanceTimeBy(121_000L)
        runCurrent()

        assertTrue(disconnect.isCompleted)
        assertNull(radio.activeSession.value, "teardown must complete once the timed-out work is released")
        never.complete(Unit)
    }

    @Test
    fun `a wedged config install is cancelled and the next config install runs`() = runTest {
        val recorded = FakeRadioConfigRepository()
        var hang = true
        val repo =
            object : RadioConfigRepository by recorded {
                override suspend fun setLocalConfig(config: Config) {
                    if (hang) {
                        hang = false
                        awaitCancellation()
                    }
                    recorded.setLocalConfig(config)
                }
            }
        val notificationPrefs = mock<NotificationPrefs>(MockMode.autofill)
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)
        every { notificationPrefs.nodeEventsEnabled } returns MutableStateFlow(true)
        val radio = FakeRadioInterfaceService(backgroundScope)
        radio.setDeviceAddress("ble:a")
        radio.connect()
        val session = assertNotNull(radio.activeSession.value)
        val manager =
            MeshConfigFlowManagerImpl(
                nodeManager = mock<NodeManager>(MockMode.autofill),
                nodeRepository = mock<NodeRepository>(MockMode.autofill),
                radioConfigRepository = repo,
                analytics = mock<PlatformAnalytics>(MockMode.autofill),
                notificationPrefs = notificationPrefs,
                radioInterfaceService = radio,
            )
        val config = Config.Builder().also { it.device = Config.DeviceConfig.Builder().build() }.build()
        val bundle =
            ConfigBundle(
                myInfo = MyNodeInfo.Builder().also { it.my_node_num = 1 }.build(),
                metadata = DeviceMetadata.Builder().build(),
                configs = listOf(config),
                moduleConfigs = emptyList(),
            )

        val logs = CapturingLogWriter.install()
        try {
            val first = launch { manager.installConfigBundle(session, bundle) }
            runCurrent()
            advanceTimeBy(121_000L)
            runCurrent()

            assertTrue(first.isCompleted)
            val lines = logs.messages(Severity.Error).filter { "Session handler exceeded" in it }
            assertEquals(1, lines.size, "one timeout line: $lines")
            assertTrue("(handler=SDK config change)" in lines.single())

            manager.installConfigBundle(session, bundle)
            assertEquals(config, recorded.lastSetLocalConfig)
        } finally {
            CapturingLogWriter.uninstall()
        }
    }
}
