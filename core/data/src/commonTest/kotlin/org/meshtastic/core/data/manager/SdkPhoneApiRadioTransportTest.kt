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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SdkPhoneApiRadioTransportTest {

    private class Harness(scope: CoroutineScope, val link: FakeRadioTransport) {
        private val fake =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress("tcp:sdk")
                connect()
            }
        val radio: RadioInterfaceService = fake
        private val nodeManager =
            NodeManagerImpl(FakeNodeRepository(), mock(MockMode.autofill), radio, scope.asServiceScope())
        val transport =
            SdkPhoneApiRadioTransport(
                openTransport = { link },
                service = radio,
                projection = lazy { radioSessionProjection(nodeManager, radio, scope) },
                connectionManager = lazy { mock(MockMode.autofill) },
                lockdownCoordinator = lazy { FakeLockdownCoordinator() },
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

    private fun MeshPacket.admin(): AdminMessage? =
        decoded?.takeIf { it.portnum == PortNum.ADMIN_APP }?.let { AdminMessage.ADAPTER.decode(it.payload) }

    private companion object {
        const val NODE = 0x1A2B3C4D
        const val HANDSHAKE_START_MS = 1_000L
        const val CLOCK_TOLERANCE_S = 60L
    }
}
