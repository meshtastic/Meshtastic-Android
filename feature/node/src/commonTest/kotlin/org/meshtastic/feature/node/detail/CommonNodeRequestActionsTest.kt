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
package org.meshtastic.feature.node.detail

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioController
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommonNodeRequestActionsTest {
    private val radioController: RadioController = mock()
    private val snackbarManager = RecordingSnackbarManager()
    private val actions =
        CommonNodeRequestActions(
            radioController = radioController,
            snackbarManager = snackbarManager,
            analytics = mock<PlatformAnalytics>(),
            resolveUiText = resolveNodeRequestFailureUiText,
        )

    @BeforeTest
    fun setUp() {
        snackbarManager.messages.clear()
    }

    @Test
    fun `missing local identity uses the same localized request feedback`() = runTest {
        everySuspend { radioController.requestUserInfo(1234) } throws LocalNodeUnavailableException("local node")

        actions.requestUserInfo(destNum = 1234, longName = "Test Node")

        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun `traceroute queue rejection is surfaced without starting the request timer`() = runTest {
        val rejection = PacketQueueRejectedException("Traceroute request")
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(42, 1234) } throws rejection

        actions.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNull(actions.lastTracerouteTime.value)
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun `neighbor queue rejection is surfaced without starting the request timer`() = runTest {
        val rejection = PacketQueueRejectedException("Neighbor info request")
        every { radioController.generatePacketId() } returns 43
        everySuspend { radioController.requestNeighborInfo(43, 1234) } throws rejection

        actions.requestNeighborInfo(destNum = 1234, longName = "Test Node")

        assertEquals(emptyMap(), actions.lastRequestNeighborTimes.value)
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    private fun sendingActions() = CommonNodeRequestActions(
        radioController = radioController,
        snackbarManager = snackbarManager,
        analytics = mock<PlatformAnalytics>(MockMode.autofill),
        resolveUiText = { "feedback" },
    )

    @Test
    fun `traceroute request stamps the shared cooldown`() = runTest {
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(any(), any()) } returns Unit
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNotNull(sending.lastTracerouteTime.value)
        verifySuspend(exactly(1)) { radioController.requestTraceroute(42, 1234) }
    }

    @Test
    fun `second traceroute request inside the cooldown is not sent`() = runTest {
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(any(), any()) } returns Unit
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")
        val stamped = sending.lastTracerouteTime.value
        sending.requestTraceroute(destNum = 5678, longName = "Other Node")

        assertEquals(stamped, sending.lastTracerouteTime.value)
        verifySuspend(exactly(1)) { radioController.requestTraceroute(any(), any()) }
    }

    @Test
    fun `concurrent traceroute requests send exactly once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(any(), any()) } calls { gate.await() }
        val sending = sendingActions()

        val first = launch { sending.requestTraceroute(destNum = 1234, longName = "Test Node") }
        val second = launch { sending.requestTraceroute(destNum = 5678, longName = "Other Node") }
        runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()

        verifySuspend(exactly(1)) { radioController.requestTraceroute(any(), any()) }
        assertNotNull(sending.lastTracerouteTime.value)
    }

    @Test
    fun `failed traceroute send leaves the cooldown unclaimed`() = runTest {
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(any(), any()) } throws PacketQueueRejectedException("x")
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")
        assertNull(sending.lastTracerouteTime.value)

        everySuspend { radioController.requestTraceroute(any(), any()) } returns Unit
        sending.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNotNull(sending.lastTracerouteTime.value)
        verifySuspend(exactly(2)) { radioController.requestTraceroute(any(), any()) }
    }
}
