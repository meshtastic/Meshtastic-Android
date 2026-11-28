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
package org.meshtastic.core.data.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.data.manager.MeshConfigFlowManagerImpl
import org.meshtastic.core.data.manager.NodeManagerImpl
import org.meshtastic.core.data.manager.radioSessionProjection
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.repository.RadioSessionAuthority
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.sdk.DisplayImage
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.SessionScoped
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which app session each SDK session event and tagged side-stream item reaches, keyed by SDK session id. */
class SdkRadioLinkSessionRoutingTest {

    private class RecordingHost(private val radio: FakeRadioInterfaceService, private val log: MutableList<String>) :
        RadioSessionHost,
        RadioSessionAuthority by radio {
        var open: RadioSessionContext? = null
        var openFailure: Throwable? = null
        var closeFailure: Throwable? = null

        override fun openSession(): RadioSessionContext {
            openFailure?.let {
                openFailure = null
                throw it
            }
            check(open == null) { "an app session is already open" }
            radio.connect()
            return checkNotNull(radio.activeSession.value).also {
                open = it
                log += "open ${it.generation}"
            }
        }

        override suspend fun closeSession(session: RadioSessionContext) {
            log += "close ${session.generation}"
            open = null
            radio.disconnect()
            closeFailure?.let { throw it }
        }

        override fun onSnapshotInstalled(session: RadioSessionContext) {
            log += "snapshot-installed ${session.generation}"
        }

        override fun onSnapshotInstallFailed(session: RadioSessionContext) {
            log += "install-failed ${session.generation}"
        }
    }

    private class Rig(val scope: TestScope) {
        val log = mutableListOf<String>()
        val radio = FakeRadioInterfaceService(scope.backgroundScope).apply { setDeviceAddress("tfake") }
        val logs = FakeMeshLogRepository()
        val palettes = mutableListOf<DisplayPalette>()
        val images = mutableListOf<DisplayImage>()
        val lockdown =
            mock<LockdownCoordinator>(MockMode.autofill).also {
                every { it.onConnect() } calls { log += "lockdown-connect" }
                every { it.onDisconnect() } calls { log += "lockdown-disconnect" }
            }
        val mirror =
            object : DisplayMirrorManager {
                override val frame = MutableStateFlow<MirrorFrame?>(null)
                override val palette = MutableStateFlow<MirrorPalette?>(null)

                override fun handleImage(image: DisplayImage) {
                    images += image
                }

                override fun handlePalette(palette: DisplayPalette) {
                    palettes += palette
                }

                override fun reset() = Unit
            }
        val connections =
            mock<MeshConnectionManager>(MockMode.autofill).also {
                everySuspend { it.onNodeDbReady() } calls { log += "node-db-ready" }
            }
        val nodeRepository = FakeNodeRepository()
        val nodeManager =
            NodeManagerImpl(nodeRepository, mock(MockMode.autofill), radio, scope.backgroundScope.asServiceScope())
                .apply {
                    setMyNodeNum(1)
                    setNodeDbReady(true)
                    setAllowNodeDbWrites(true)
                }
        val configFlowManager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                nodeRepository = nodeRepository,
                radioConfigRepository = FakeRadioConfigRepository(),
                analytics = mock(MockMode.autofill),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
            )
        val host = RecordingHost(radio, log)
        val pipeline =
            RadioSessionPipeline(
                lazy {
                    radioSessionProjection(
                        nodeManager = nodeManager,
                        radio = radio,
                        scope = scope.backgroundScope,
                        meshLogRepository = logs,
                        nodeRepository = nodeRepository,
                        configFlowManager = lazy { configFlowManager },
                        displayMirrorManager = mirror,
                    )
                },
                lazy { connections },
                lazy { lockdown },
            )

        fun link(configure: RadioClient.Builder.() -> RadioClient.Builder = { this }) = SdkRadioLink(
            client =
            RadioClient.Builder()
                .transport(FakeRadioTransport(TransportIdentity("fake:a7"), autoHandshake = true, nodeNum = 1))
                .storage(InMemoryStorageProvider())
                .coroutineContext(scope.backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .configure()
                .build(),
            pipeline = pipeline,
            gattCache = null,
            reasonOf = { null },
            onClose = {},
        )
    }

    private fun lockdown(id: Long) = RadioSessionEvent.Lockdown(id, 1, LockdownStatus.Builder().build())

    private fun TestScope.pump(rig: Rig, link: SdkRadioLink, events: MutableSharedFlow<RadioSessionEvent>) {
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { link.consume(events, rig.host) }
    }

    private suspend fun TestScope.send(events: MutableSharedFlow<RadioSessionEvent>, vararg sent: RadioSessionEvent) {
        sent.forEach {
            events.emit(it)
            runCurrent()
        }
    }

    // Regression: a Lockdown-only session was closed by inference when the next one opened, not at its own Ended.
    @Test
    fun `a session that stops at Lockdown is closed by its own Ended`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)

        send(events, lockdown(1), RadioSessionEvent.Ended(1))
        assertEquals(
            listOf("open 1", "lockdown-connect", "close 1", "lockdown-disconnect"),
            rig.log,
            "Ended(1) must close the first session before any later event",
        )

        send(events, lockdown(2))
        assertEquals(
            listOf("open 1", "lockdown-connect", "close 1", "lockdown-disconnect", "open 2", "lockdown-connect"),
            rig.log,
        )
    }

    // Regression: late tagged items were written to whichever app session was open instead of their own.
    @Test
    fun `a side-stream item for a closed session is dropped and never written to the next one`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        val link = rig.link()
        pump(rig, link, events)

        send(events, lockdown(1), RadioSessionEvent.Ended(1), lockdown(2))
        link.routeDeviceLog(SessionScoped(1L, LogRecord.Builder().build()))
        runCurrent()
        assertTrue(rig.logs.currentLogs.isEmpty(), "an item tagged with closed session 1 must not reach session 2")

        link.routeDeviceLog(SessionScoped(2L, LogRecord.Builder().build()))
        runCurrent()
        assertEquals(1, rig.logs.currentLogs.size, "an item tagged with the open session is written")
    }

    // Regression: a snapshot that was not installed left the session open and the app short of Connected for good.
    @Test
    fun `a snapshot that is not installed is reported to the host`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)

        send(
            events,
            RadioSessionEvent.Ready(1, myInfo = null, configBundle = null, channels = null, nodes = emptyMap()),
        )

        assertEquals(listOf("open 1", "lockdown-connect", "install-failed 1"), rig.log)
    }

    // Regression: an Ended that closed whatever was open, whatever its id.
    @Test
    fun `an Ended for an unknown id leaves the open session open`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)

        send(events, lockdown(1), RadioSessionEvent.Ended(7))

        assertNotNull(rig.host.open)
        assertEquals(listOf("open 1", "lockdown-connect"), rig.log)
    }

    // Regression: opening a new session without closing the previous one trips the host's overlap check.
    @Test
    fun `a new id closes the previous session before opening its own`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)

        send(events, lockdown(1), lockdown(2))

        assertEquals(
            listOf("open 1", "lockdown-connect", "close 1", "lockdown-disconnect", "open 2", "lockdown-connect"),
            rig.log,
        )
    }

    // Regression: the untagged palette stream was routed to a closed or absent session.
    @Test
    fun `displayPalette reaches the open session and is dropped after its Ended`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        val link = rig.link()
        pump(rig, link, events)
        val palette = DisplayPalette.Builder().build()

        link.routeDisplayPalette(palette)
        assertTrue(rig.palettes.isEmpty(), "no session is open yet")

        send(events, lockdown(1))
        link.routeDisplayPalette(palette)
        assertEquals(1, rig.palettes.size)

        send(events, RadioSessionEvent.Ended(1))
        link.routeDisplayPalette(palette)
        assertEquals(1, rig.palettes.size, "a palette after Ended(1) has no session to reach")
    }

    // Regression: the untagged display image was routed to a closed or absent session.
    @Test
    fun `displayImage reaches the open session and is dropped after its Ended`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        val link = rig.link()
        pump(rig, link, events)
        val image = DisplayImage(8, 4, DisplayFrame.Format.RGB565, 1, 0, ByteArray(64).toByteString())

        link.routeDisplayImage(image)
        assertTrue(rig.images.isEmpty(), "no session is open yet")

        send(events, lockdown(1))
        link.routeDisplayImage(image)
        assertEquals(1, rig.images.size)

        send(events, RadioSessionEvent.Ended(1))
        link.routeDisplayImage(image)
        assertEquals(1, rig.images.size, "an image after Ended(1) has no session to reach")
    }

    // Regression: cancelling the pump left its app session open.
    @Test
    fun `cancelling the pump closes the open session`() = runTest {
        val rig = Rig(this)
        val link = rig.link { sessionReceive() }
        val pump = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { link.run(rig.host) }

        link.client.connect()
        runCurrent()
        assertNotNull(rig.host.open, "the SDK session must have opened an app session")

        pump.cancel()
        runCurrent()

        assertNull(rig.host.open)
        assertTrue("close 1" in rig.log)
    }

    // Regression: a throw from the host opening a session escaped consume and ended the pump.
    @Test
    fun `a failed session open is skipped and the next session id opens normally`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)
        rig.host.openFailure = IllegalStateException("Cannot admit a session while the previous one is still draining")

        send(events, lockdown(1), lockdown(2))

        assertNotNull(rig.host.open, "the event for the new session id must open an app session")
        assertEquals(listOf("open 1", "lockdown-connect"), rig.log)
    }

    // Regression: a throw from host.closeSession skipped lockdown.onDisconnect.
    @Test
    fun `lockdown is told of the disconnect when closing the session throws`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        val link = rig.link()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { runCatching { link.consume(events, rig.host) } }
        rig.host.closeFailure = IllegalStateException("close failed")

        send(events, lockdown(1), RadioSessionEvent.Ended(1))

        assertEquals(listOf("open 1", "lockdown-connect", "close 1", "lockdown-disconnect"), rig.log)
    }

    // Regression: a Lockdown event for a session whose admission closed reached the lockdown coordinator.
    @Test
    fun `a Lockdown event for a session whose admission closed is not handled`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)

        send(events, lockdown(1))
        rig.radio.disconnect()
        send(events, lockdown(1))

        verify(VerifyMode.exactly(1)) { rig.lockdown.handleLockdownStatus(any()) }
    }

    // Regression: one try block around both readiness callbacks skipped onNodeDbReady when onRadioConfigLoaded threw.
    @Test
    fun `a throwing onRadioConfigLoaded still calls onNodeDbReady`() = runTest {
        val rig = Rig(this)
        every { rig.connections.onRadioConfigLoaded() } calls { error("config callback failed") }
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)
        val myInfo = MyNodeInfo.Builder().also { it.my_node_num = 1 }.build()

        send(
            events,
            RadioSessionEvent.Ready(1, myInfo, configBundle = null, channels = emptyList(), nodes = emptyMap()),
        )

        assertTrue("node-db-ready" in rig.log, "onNodeDbReady must run after onRadioConfigLoaded threw: ${rig.log}")
    }

    // Regression: a dropped or late onSnapshotInstalled would run post-handshake requests before Connected shows.
    @Test
    fun `a handshake snapshot that installs is reported to the host before onNodeDbReady`() = runTest {
        val rig = Rig(this)
        val events = MutableSharedFlow<RadioSessionEvent>()
        pump(rig, rig.link(), events)
        val myInfo = MyNodeInfo.Builder().also { it.my_node_num = 1 }.build()

        send(
            events,
            RadioSessionEvent.Ready(1, myInfo, configBundle = null, channels = emptyList(), nodes = emptyMap()),
        )

        val installed = rig.log.indexOf("snapshot-installed 1")
        val ready = rig.log.indexOf("node-db-ready")
        assertTrue(installed >= 0, "the host must be told the snapshot installed: ${rig.log}")
        assertTrue(installed < ready, "snapshot-installed must precede node-db-ready: ${rig.log}")
    }
}
