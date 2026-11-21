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
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.request_failed_from
import org.meshtastic.core.resources.request_metadata
import org.meshtastic.core.resources.user_info
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommonNodeRequestActionsTest {
    private val radioController: RadioController = mock()
    private val snackbarManager = RecordingSnackbarManager()

    private fun TestScope.actions(resolveUiText: suspend (UiText) -> String = resolveNodeRequestFailureUiText) =
        CommonNodeRequestActions(
            radioController = radioController,
            snackbarManager = snackbarManager,
            analytics = mock<PlatformAnalytics>(MockMode.autofill),
            serviceScope = backgroundScope.asServiceScope(),
            resolveUiText = resolveUiText,
        )

    private fun TestScope.sendingActions() = actions { "feedback" }

    private fun answered(requestId: Int = 42) = NodeRequest(requestId) { null }

    @Test
    fun `missing local identity uses the same localized request feedback`() = runTest {
        everySuspend { radioController.requestUserInfo(1234) } throws LocalNodeUnavailableException("local node")

        actions().requestUserInfo(destNum = 1234, longName = "Test Node")

        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun `a request the mesh refuses shows which request failed and why`() = runTest {
        everySuspend { radioController.requestUserInfo(1234) } returns
            NodeRequest(41) { Routing.Error.NOT_AUTHORIZED.value }
        val resolved = mutableListOf<UiText>()

        actions {
            resolved += it
            ""
        }
            .requestUserInfo(destNum = 1234, longName = "Test Node")
        runCurrent()

        val nak = resolved.filterIsInstance<UiText.Resource>().single { it.res == Res.string.request_failed_from }
        assertEquals(
            listOf(Res.string.user_info, "Test Node", getStringResFrom(Routing.Error.NOT_AUTHORIZED.value)),
            nak.args.toList(),
        )
    }

    @Test
    fun `an answered request shows no failure`() = runTest {
        everySuspend { radioController.requestUserInfo(1234) } returns answered()
        val resolved = mutableListOf<UiText>()

        actions {
            resolved += it
            ""
        }
            .requestUserInfo(destNum = 1234, longName = "Test Node")
        runCurrent()

        assertTrue(resolved.filterIsInstance<UiText.Resource>().none { it.res == Res.string.request_failed_from })
    }

    @Test
    fun `a metadata NAK still shows after the screen that asked is gone`() = runTest {
        val nak = CompletableDeferred<Int?>()
        everySuspend { radioController.refreshMetadata(1234) } returns NodeRequest(null) { nak.await() }
        val resolved = mutableListOf<UiText>()
        val actions = actions {
            resolved += it
            ""
        }

        val screen = launch { actions.refreshMetadata(destNum = 1234, longName = "Test Node") }
        runCurrent()
        screen.cancel()
        nak.complete(Routing.Error.MAX_RETRANSMIT.value)
        runCurrent()

        val shown = resolved.filterIsInstance<UiText.Resource>().single { it.res == Res.string.request_failed_from }
        assertEquals(
            listOf(Res.string.request_metadata, "Test Node", getStringResFrom(Routing.Error.MAX_RETRANSMIT.value)),
            shown.args.toList(),
        )
    }

    @Test
    fun `traceroute queue rejection is surfaced without starting the request timer`() = runTest {
        val actions = actions()
        everySuspend { radioController.requestTraceroute(1234) } throws PacketQueueRejectedException("Traceroute")

        actions.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNull(actions.lastTracerouteTime.value)
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun `neighbor queue rejection is surfaced without starting the request timer`() = runTest {
        val actions = actions()
        everySuspend { radioController.requestNeighborInfo(1234) } throws PacketQueueRejectedException("Neighbor")

        actions.requestNeighborInfo(destNum = 1234, longName = "Test Node")

        assertEquals(emptyMap(), actions.lastRequestNeighborTimes.value)
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun `traceroute request stamps the shared cooldown`() = runTest {
        everySuspend { radioController.requestTraceroute(any()) } returns answered()
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNotNull(sending.lastTracerouteTime.value)
        verifySuspend(exactly(1)) { radioController.requestTraceroute(1234) }
    }

    @Test
    fun `a rate limit refusal is recorded against the traceroute request`() = runTest {
        everySuspend { radioController.requestTraceroute(any()) } returns
            NodeRequest(42) { Routing.Error.RATE_LIMIT_EXCEEDED.value }
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")
        runCurrent()

        assertEquals(mapOf(42 to Routing.Error.RATE_LIMIT_EXCEEDED.value), sending.tracerouteNaks.value)
    }

    @Test
    fun `second traceroute request inside the cooldown is not sent`() = runTest {
        everySuspend { radioController.requestTraceroute(any()) } returns answered()
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")
        val stamped = sending.lastTracerouteTime.value
        sending.requestTraceroute(destNum = 5678, longName = "Other Node")

        assertEquals(stamped, sending.lastTracerouteTime.value)
        verifySuspend(exactly(1)) { radioController.requestTraceroute(any()) }
    }

    @Test
    fun `concurrent traceroute requests send exactly once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        everySuspend { radioController.requestTraceroute(any()) } calls
            {
                gate.await()
                answered()
            }
        val sending = sendingActions()

        val first = launch { sending.requestTraceroute(destNum = 1234, longName = "Test Node") }
        val second = launch { sending.requestTraceroute(destNum = 5678, longName = "Other Node") }
        runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()

        verifySuspend(exactly(1)) { radioController.requestTraceroute(any()) }
        assertNotNull(sending.lastTracerouteTime.value)
    }

    @Test
    fun `failed traceroute send leaves the cooldown unclaimed`() = runTest {
        everySuspend { radioController.requestTraceroute(any()) } throws PacketQueueRejectedException("x")
        val sending = sendingActions()

        sending.requestTraceroute(destNum = 1234, longName = "Test Node")
        assertNull(sending.lastTracerouteTime.value)

        everySuspend { radioController.requestTraceroute(any()) } returns answered()
        sending.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNotNull(sending.lastTracerouteTime.value)
        verifySuspend(exactly(2)) { radioController.requestTraceroute(any()) }
    }
}
