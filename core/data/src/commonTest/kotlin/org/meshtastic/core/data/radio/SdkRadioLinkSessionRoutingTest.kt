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
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.manager.radioSessionProjection
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.repository.RadioSessionAuthority
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.LogRecord
import org.meshtastic.sdk.DisplayMirrorFrame
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

    private class Rig(val scope: TestScope) {
        val log = mutableListOf<String>()
        val radio = FakeRadioInterfaceService(scope.backgroundScope).apply { setDeviceAddress("tfake") }
        val logs = FakeMeshLogRepository()
        val palettes = mutableListOf<DisplayPalette>()
        val lockdown =
            mock<LockdownCoordinator>(MockMode.autofill).also {
                every { it.onConnect() } calls { log += "lockdown-connect" }
                every { it.onDisconnect() } calls { log += "lockdown-disconnect" }
            }
        val mirror =
            object : DisplayMirrorManager {
                override val frame = MutableStateFlow<MirrorFrame?>(null)
                override val palette = MutableStateFlow<MirrorPalette?>(null)

                override fun handleFrame(frame: DisplayMirrorFrame) = Unit

                override fun handlePalette(palette: DisplayPalette) {
                    palettes += palette
                }

                override fun reset() = Unit
            }
        val host =
            object : RadioSessionHost, RadioSessionAuthority by radio {
                var open: RadioSessionContext? = null

                override fun openSession(): RadioSessionContext {
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
                }
            }
        val pipeline =
            RadioSessionPipeline(
                lazy {
                    radioSessionProjection(
                        nodeManager = mock(MockMode.autofill),
                        radio = radio,
                        scope = scope.backgroundScope,
                        meshLogRepository = logs,
                        displayMirrorManager = mirror,
                    )
                },
                lazy { error("connection manager is not under test") },
                lazy { lockdown },
            )

        fun link(configure: RadioClient.Builder.() -> RadioClient.Builder = { this }) =
            SdkRadioLink(
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
    fun `a side-stream item for a closed session is dropped, not written to the next one`() = runTest {
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
}
