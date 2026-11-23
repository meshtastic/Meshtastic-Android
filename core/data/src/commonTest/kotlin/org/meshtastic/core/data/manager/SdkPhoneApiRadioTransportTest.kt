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

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import org.meshtastic.sdk.RadioTransport as SdkRadioTransport

class SdkPhoneApiRadioTransportTest {

    private class Harness(
        scope: CoroutineScope,
        val link: FakeRadioTransport,
        open: suspend () -> SdkRadioTransport = { link },
        history: WebSocketSessionHistory? = null,
    ) {
        private val fake =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress("tcp:sdk")
                connect()
            }

        /** Each `recoverable` the transport reported when its session ended. */
        val ends = mutableListOf<Boolean>()

        /** Each `handshakeTimedOut` the transport reported when its session ended. */
        val timeouts = mutableListOf<Boolean>()

        /** Each `reason` the transport reported when its session ended. */
        val reasons = mutableListOf<TransportDisconnectReason?>()
        val radio: RadioInterfaceService =
            object : RadioInterfaceService by fake {
                override fun onPhoneApiSessionEnded(
                    recoverable: Boolean,
                    errorMessage: String?,
                    handshakeTimedOut: Boolean,
                    reason: TransportDisconnectReason?,
                ) {
                    ends += recoverable
                    timeouts += handshakeTimedOut
                    reasons += reason
                }
            }
        private val nodeManager =
            NodeManagerImpl(FakeNodeRepository(), mock(MockMode.autofill), radio, scope.asServiceScope())
        val transport =
            SdkPhoneApiRadioTransport(
                openTransport = open,
                service = radio,
                projection = lazy { radioSessionProjection(nodeManager, radio, scope) },
                connectionManager = lazy { mock(MockMode.autofill) },
                lockdownCoordinator = lazy { FakeLockdownCoordinator() },
                webSocketHistory = history,
            )

        // Firmware answers want_config with my_info first (PhoneAPI STATE_SEND_MY_INFO).
        fun sendMyInfo() {
            val myInfo = MyNodeInfo.Builder().also { it.my_node_num = NODE }.build()
            link.injectFrame(FromRadio.Builder().also { it.my_info = myInfo }.build().toFrame())
        }
    }

    @Test
    fun `a session sets the radio clock as soon as my_info arrives`() = runTest {
        val harness = Harness(backgroundScope, FakeRadioTransport(TransportIdentity("fake:clock")))
        try {
            harness.transport.start()
            advanceTimeBy(HANDSHAKE_START_MS)
            harness.sendMyInfo()
            runCurrent()

            // Firmware's AdminModule handles set_time_only addressed to its own node number.
            val sync = harness.link.outboundPackets().singleOrNull { it.admin()?.set_time_only != null }
            assertNotNull(sync, "no set_time_only after my_info")
            assertEquals(NODE, sync.to)
            val sentSeconds = assertNotNull(sync.admin()?.set_time_only)
            assertTrue(abs(Clock.System.now().epochSeconds - sentSeconds) < CLOCK_TOLERANCE_S, "sent $sentSeconds")
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `a receive overflow during the handshake ends the session for a retry`() = runTest {
        val harness = Harness(backgroundScope, FakeRadioTransport(TransportIdentity("fake:overflow")))
        try {
            harness.transport.start()
            advanceTimeBy(HANDSHAKE_START_MS)
            harness.sendMyInfo()
            // Mesh traffic interleaved with the config dump outgrows what the SDK holds until Ready.
            repeat(EARLY_PACKETS) { id ->
                harness.link.injectPacket(
                    MeshPacket.Builder()
                        .also {
                            it.id = id + 1
                            it.from = PEER
                            it.to = NodeAddress.NODENUM_BROADCAST
                            it.decoded = Data.Builder().also { d -> d.portnum = PortNum.TEXT_MESSAGE_APP }.build()
                        }
                        .build(),
                )
            }
            runCurrent()

            assertEquals(listOf(true), harness.ends)
            assertEquals(listOf(false), harness.timeouts)
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `a radio that never answers the handshake reports a handshake timeout`() = runTest {
        val harness = Harness(backgroundScope, FakeRadioTransport(TransportIdentity("fake:silent")))
        try {
            harness.transport.start()
            advanceTimeBy(SILENT_RADIO_MS)

            assertEquals(listOf(true), harness.ends)
            assertEquals(listOf(true), harness.timeouts)
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `firmware the SDK refuses as too old ends the session for good with its own reason`() = runTest {
        val link = FakeRadioTransport(TransportIdentity("fake:old"))
        val harness =
            Harness(
                backgroundScope,
                link,
                open = { throw MeshtasticException.FirmwareTooOld(required = 30, present = 22) },
            )
        try {
            harness.transport.start()
            runCurrent()

            assertEquals(listOf(false), harness.ends)
            assertEquals(listOf<TransportDisconnectReason?>(TransportDisconnectReason.FirmwareTooOld), harness.reasons)
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `an unexpected failure opening the link leaves the session to the retry`() = runTest {
        val link = FakeRadioTransport(TransportIdentity("fake:vanished"))
        // A serial port that disappeared between the retry's check and the open.
        val harness = Harness(backgroundScope, link, open = { throw IllegalStateException("port is gone") })
        try {
            harness.transport.start()
            runCurrent()

            assertEquals(listOf(true), harness.ends)
            assertEquals(listOf<TransportDisconnectReason?>(null), harness.reasons)
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `a WebSocket the radio gave to another client ends for good with that reason`() = runTest {
        val link = FakeRadioTransport(TransportIdentity("fake:ws"))
        val harness = Harness(backgroundScope, link, history = WebSocketSessionHistory { currentTime })
        try {
            harness.transport.start()
            advanceTimeBy(HANDSHAKE_START_MS)
            // The SDK WebSocketTransport's report of firmware's WS_CLOSE_REPLACED.
            link.simulateError(
                MeshtasticException.Transport(
                    "WebSocket closed: another client took the node's only WebSocket slot (4001)",
                ),
                recoverable = false,
            )
            runCurrent()

            assertEquals(listOf(false), harness.ends)
            assertEquals(
                listOf<TransportDisconnectReason?>(TransportDisconnectReason.ReplacedByAnotherClient),
                harness.reasons,
            )
        } finally {
            harness.transport.close()
        }
    }

    private fun MeshPacket.admin(): AdminMessage? =
        decoded?.takeIf { it.portnum == PortNum.ADMIN_APP }?.let { AdminMessage.ADAPTER.decode(it.payload) }

    private companion object {
        const val NODE = 0x1A2B3C4D
        const val PEER = 0x0BADF00D
        const val HANDSHAKE_START_MS = 1_000L
        const val EARLY_PACKETS = 200
        const val SILENT_RADIO_MS = 10 * 60 * 1_000L
        const val CLOCK_TOLERANCE_S = 60L
    }
}
