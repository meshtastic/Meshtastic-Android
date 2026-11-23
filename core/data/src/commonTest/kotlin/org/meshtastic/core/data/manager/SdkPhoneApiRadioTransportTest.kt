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

import com.juul.kable.GattStatusException
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import org.meshtastic.sdk.ConnectionState.DeviceSleeping
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.WireFraming
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
        address: String = "tcp:sdk",
        installsSnapshot: Boolean = false,
    ) {
        private val fake =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress(address)
                connect()
            }

        /** Each `recoverable` the transport reported when its session ended. */
        val ends = mutableListOf<Boolean>()

        /** Each `handshakeTimedOut` the transport reported when its session ended. */
        val timeouts = mutableListOf<Boolean>()

        /** Each `receiveOverflowed` the transport reported when its session ended. */
        val overflows = mutableListOf<Boolean>()

        /** Each `reason` the transport reported when its session ended. */
        val reasons = mutableListOf<TransportDisconnectReason?>()

        /** Each SDK sleep verdict the transport reported when its session ended. */
        val sleeps = mutableListOf<DeviceSleeping?>()
        val radio: RadioInterfaceService =
            object : RadioInterfaceService by fake {
                override fun onPhoneApiSessionEnded(
                    recoverable: Boolean,
                    errorMessage: String?,
                    handshakeTimedOut: Boolean,
                    reason: TransportDisconnectReason?,
                    receiveOverflowed: Boolean,
                    deviceSleeping: DeviceSleeping?,
                ) {
                    sleeps += deviceSleeping
                    overflows += receiveOverflowed
                    ends += recoverable
                    timeouts += handshakeTimedOut
                    reasons += reason
                }
            }
        private val nodeRepository = FakeNodeRepository()
        private val nodeManager =
            NodeManagerImpl(nodeRepository, mock(MockMode.autofill), radio, scope.asServiceScope())
        private val configFlowManager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                nodeRepository = nodeRepository,
                radioConfigRepository = FakeRadioConfigRepository(),
                serviceStateWriter = FakeServiceRepository(),
                analytics = mock(MockMode.autofill),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
                scope = scope.asServiceScope(),
            )
        val transport =
            SdkPhoneApiRadioTransport(
                openTransport = open,
                service = radio,
                projection =
                lazy {
                    if (installsSnapshot) {
                        radioSessionProjection(
                            nodeManager,
                            radio,
                            scope,
                            configFlowManager = lazy { configFlowManager },
                        )
                    } else {
                        radioSessionProjection(nodeManager, radio, scope)
                    }
                },
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
            assertEquals(listOf(true), harness.overflows)
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
            // Old firmware is no restart failure: it must not spend the handshake recovery cap.
            assertEquals(listOf(false), harness.timeouts)
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
    fun `a BLE connect the radio refuses for want of pairing reports pairing required`() = runTest {
        val link = FakeRadioTransport(TransportIdentity("fake:ble"))
        // BleTransport rethrows the warmup read's auth failure wrapped in its connect failure.
        val refused =
            MeshtasticException.Transport(
                "BLE connect failed",
                GattStatusException(message = "read", status = GATT_INSUFFICIENT_AUTHENTICATION),
            )
        val harness = Harness(backgroundScope, link, open = { throw refused }, address = "xAA:BB:CC:DD:EE:FF")
        try {
            harness.transport.start()
            runCurrent()

            assertEquals(TransportFailureKind.PairingRequired, harness.radio.transportStatus.value?.lastFailure?.kind)
            // A transport error is no lost receive coverage: it must not spend the overflow recovery cap.
            assertEquals(listOf(false), harness.overflows)
        } finally {
            harness.transport.close()
        }
    }

    @Test
    fun `a power-saving tracker that drops its BLE link ends the session with the SDK's sleep`() = runTest {
        val link = FakeRadioTransport(TransportIdentity("ble:AA:BB:CC:DD:EE:FF"), autoHandshake = true, nodeNum = NODE)
        val harness = Harness(backgroundScope, link, open = { TrackerConfigTransport(link) }, installsSnapshot = true)
        try {
            harness.transport.start()
            // The handshake settles over virtual time before the snapshot installs.
            repeat(READY_POLLS) {
                if (harness.radio.transportStatus.value?.phase != TransportPhase.Connected) advanceTimeBy(READY_POLL_MS)
            }
            assertEquals(TransportPhase.Connected, harness.radio.transportStatus.value?.phase)

            link.simulateError(MeshtasticException.Transport("BLE link lost"), recoverable = true)
            runCurrent()

            // A tracker with is_power_saving deep-sleeps position_broadcast_secs, firmware's 3600 when that is 0.
            assertEquals(listOf(3600), harness.sleeps.map { it?.expectedWakeSeconds })
            assertEquals(listOf(true), harness.ends)
        } finally {
            harness.transport.close()
        }
    }

    /** Sends a power-saving tracker's config in the download, as firmware does ahead of config_complete. */
    private class TrackerConfigTransport(private val link: FakeRadioTransport) : SdkRadioTransport by link {
        private var configSent = false

        override suspend fun send(frame: Frame) {
            val bytes = frame.bytes.toByteArray()
            val wantsConfig =
                ToRadio.ADAPTER.decode(bytes.copyOfRange(WireFraming.HEADER_SIZE, bytes.size)).want_config_id != null
            if (wantsConfig && !configSent) {
                configSent = true
                val device = Config.DeviceConfig.Builder().also { it.role = Config.DeviceConfig.Role.TRACKER }.build()
                val power = Config.PowerConfig.Builder().also { it.is_power_saving = true }.build()
                listOf(
                    Config.Builder().also { it.device = device }.build(),
                    Config.Builder().also { it.power = power }.build(),
                )
                    .forEach { config ->
                        link.injectFrame(FromRadio.Builder().also { it.config = config }.build().toFrame())
                    }
            }
            link.send(frame)
        }
    }

    private fun MeshPacket.admin(): AdminMessage? =
        decoded?.takeIf { it.portnum == PortNum.ADMIN_APP }?.let { AdminMessage.ADAPTER.decode(it.payload) }

    private companion object {
        const val NODE = 0x1A2B3C4D
        const val PEER = 0x0BADF00D
        const val HANDSHAKE_START_MS = 1_000L
        const val READY_POLLS = 300
        const val READY_POLL_MS = 100L
        const val EARLY_PACKETS = 200
        const val SILENT_RADIO_MS = 10 * 60 * 1_000L
        const val CLOCK_TOLERANCE_S = 60L

        // Android BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION.
        const val GATT_INSUFFICIENT_AUTHENTICATION = 5
    }
}
